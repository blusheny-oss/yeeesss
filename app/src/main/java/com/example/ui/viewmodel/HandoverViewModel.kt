package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChecklistItem
import com.example.data.model.HandoverAct
import com.example.data.model.UserPlanSettings
import com.example.data.repository.HandoverRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class HandoverViewModel(
    private val repository: HandoverRepository,
    private val currentUserId: String,
) : ViewModel() {

    val actsState: StateFlow<UiState<List<HandoverAct>>> = repository.observeActs(currentUserId)
        .map<List<HandoverAct>, UiState<List<HandoverAct>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing acts", root)
            emit(UiState.Error(root.localizedMessage ?: "Не удалось загрузить акты"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val planSettingsState: StateFlow<UserPlanSettings> = repository.observePlanSettings(currentUserId)
        .catch { error ->
            if (error is CancellationException && unwrapFirestoreError(error) == null) throw error
            Log.w(TAG, "Error observing plan settings", error)
            emit(UserPlanSettings(id = UserPlanSettings.DOC_ID, userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UserPlanSettings(id = UserPlanSettings.DOC_ID, userId = currentUserId),
        )

    private val _selectedActId = MutableStateFlow<String?>(null)
    val selectedActId: StateFlow<String?> = _selectedActId.asStateFlow()

    val selectedActState: StateFlow<UiState<HandoverAct?>> = _selectedActId
        .flatMapLatest { actId ->
            if (actId.isNullOrBlank()) {
                flowOf<UiState<HandoverAct?>>(UiState.Success(null))
            } else {
                repository.observeAct(currentUserId, actId)
                    .map<HandoverAct?, UiState<HandoverAct?>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = unwrapFirestoreError(error)
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing act $actId", root)
                        emit(UiState.Error(root.localizedMessage ?: "Не удалось загрузить акт"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val checklistItemsState: StateFlow<UiState<List<ChecklistItem>>> = _selectedActId
        .flatMapLatest { actId ->
            if (actId.isNullOrBlank()) {
                flowOf<UiState<List<ChecklistItem>>>(UiState.Success(emptyList()))
            } else {
                repository.observeChecklistItems(currentUserId, actId)
                    .map<List<ChecklistItem>, UiState<List<ChecklistItem>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = unwrapFirestoreError(error)
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing checklist items", root)
                        emit(UiState.Error(root.localizedMessage ?: "Не удалось загрузить чек-лист"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun selectAct(actId: String?) {
        _selectedActId.value = actId
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun createAct(
        title: String,
        address: String,
        apartmentNumber: String,
        depositAmountRub: Long,
        monthlyRentRub: Long,
        creatorRole: String,
        landlordName: String,
        landlordPhone: String,
        tenantName: String,
        tenantPhone: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val selectedPlan = if (planSettingsState.value.b2bActive) {
                HandoverAct.PLAN_B2B_PRO
            } else {
                HandoverAct.PLAN_SINGLE_ACT
            }
            val res = repository.createActWithDefaultChecklist(
                title = title,
                address = address,
                apartmentNumber = apartmentNumber,
                depositAmountRub = depositAmountRub,
                monthlyRentRub = monthlyRentRub,
                creatorRole = creatorRole,
                landlordName = landlordName,
                landlordPhone = landlordPhone,
                tenantName = tenantName,
                tenantPhone = tenantPhone,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
                selectedPlan = selectedPlan,
            )
            _isBusy.value = false
            res.onSuccess { actId ->
                _selectedActId.value = actId
                onCreated(actId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Ошибка создания акта"
            }
        }
    }

    fun updateAct(act: HandoverAct, successToast: String? = null) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateActDetails(act)
            _isBusy.value = false
            res.onSuccess {
                if (successToast != null) _statusMessage.value = successToast
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Ошибка сохранения акта"
            }
        }
    }

    fun updateChecklistItem(
        actId: String,
        updatedItem: ChecklistItem,
        currentItems: List<ChecklistItem>,
    ) {
        viewModelScope.launch {
            val newList = currentItems.map { if (it.id == updatedItem.id) updatedItem else it }
            val res = repository.updateChecklistItemAndCounters(actId, updatedItem, newList)
            res.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Ошибка обновления пункта"
            }
        }
    }

    fun addCustomItem(actId: String, zone: String, title: String, nextOrder: Long) {
        viewModelScope.launch {
            val res = repository.addCustomChecklistItem(actId, zone, title, nextOrder)
            res.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Не удалось добавить пункт"
            }
        }
    }

    fun signAsCreator(actId: String, phone: String, smsCode: String, shareToken: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.signAsCreator(actId, phone, smsCode, shareToken)
            _isBusy.value = false
            res.onSuccess {
                _statusMessage.value = "Акт подписан ПЭП (СМС-код). Отправьте ссылку второй стороне."
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Не удалось подписать акт"
            }
        }
    }

    fun confirmAsCounterparty(
        actId: String,
        counterpartyName: String,
        counterpartyPhone: String,
        smsCode: String,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.confirmAsCounterparty(
                actId = actId,
                counterpartyName = counterpartyName,
                counterpartyPhone = counterpartyPhone,
                smsCode = smsCode,
            )
            _isBusy.value = false
            res.onSuccess {
                _statusMessage.value = "Акт подтвержден обеими сторонами и защищен от изменений!"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Не удалось подтвердить акт"
            }
        }
    }

    fun markPdfGenerated(actId: String) {
        viewModelScope.launch {
            repository.markPdfGenerated(actId)
            val current = planSettingsState.value
            repository.savePlanSettings(
                planType = current.planType,
                companyName = current.companyName,
                b2bActive = current.b2bActive,
                incrementGeneratedCount = true,
            )
        }
    }

    fun deleteDraftAct(actId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteDraftAct(actId)
            _isBusy.value = false
            res.onSuccess {
                if (_selectedActId.value == actId) _selectedActId.value = null
                _statusMessage.value = "Черновик акта удален"
                onDeleted()
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Не удалось удалить акт"
            }
        }
    }

    fun updatePlanSettings(planType: String, companyName: String, b2bActive: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.savePlanSettings(
                planType = planType,
                companyName = companyName,
                b2bActive = b2bActive,
                incrementGeneratedCount = false,
            )
            _isBusy.value = false
            res.onSuccess {
                _statusMessage.value = if (b2bActive) {
                    "Тариф B2B Pro (1 500 ₽/мес) активирован"
                } else {
                    "Выбран тариф «Разовый акт (299–490 ₽)»"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Не удалось обновить тариф"
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "HandoverVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
