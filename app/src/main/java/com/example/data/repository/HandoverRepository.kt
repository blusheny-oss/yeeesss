package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChecklistItem
import com.example.data.model.HandoverAct
import com.example.data.model.InspectionChecklistDefaults
import com.example.data.model.UserPlanSettings
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class HandoverRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String {
        // Работаем и без входа через Google: данные остаются на устройстве.
        return auth.currentUser?.uid ?: "local-user"
    }

    private fun actsCollection(uid: String) =
        db.collection("users").document(uid).collection("acts")

    private fun itemsCollection(uid: String, actId: String) =
        actsCollection(uid).document(actId).collection("items")

    private fun settingsDoc(uid: String) =
        db.collection("users").document(uid).collection("settings").document(UserPlanSettings.DOC_ID)

    fun observeActs(userId: String = requireUserId()): Flow<List<HandoverAct>> = flow {
        val path = "users/$userId/acts"
        emitAll(
            actsCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                HandoverAct::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAct(userId: String = requireUserId(), actId: String): Flow<HandoverAct?> = flow {
        val docRef = actsCollection(userId).document(actId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            HandoverAct::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeChecklistItems(userId: String = requireUserId(), actId: String): Flow<List<ChecklistItem>> = flow {
        val path = "users/$userId/acts/$actId/items"
        emitAll(
            itemsCollection(userId, actId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(
                                ChecklistItem::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }
                        .sortedBy { it.sortOrder }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlanSettings(userId: String = requireUserId()): Flow<UserPlanSettings> = flow {
        val docRef = settingsDoc(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserPlanSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserPlanSettings(id = UserPlanSettings.DOC_ID, userId = userId)
                    } else {
                        UserPlanSettings(id = UserPlanSettings.DOC_ID, userId = userId)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createActWithDefaultChecklist(
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
        selectedPlan: String = HandoverAct.PLAN_SINGLE_ACT,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val actId = "act_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val shareToken = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        val actDocRef = actsCollection(uid).document(actId)

        val actPayload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120),
            "address" to address.trim().take(240),
            "apartmentNumber" to apartmentNumber.trim().take(40),
            "depositAmountRub" to depositAmountRub.coerceIn(0L, 100_000_000L),
            "monthlyRentRub" to monthlyRentRub.coerceIn(0L, 100_000_000L),
            "creatorRole" to creatorRole,
            "landlordName" to landlordName.trim().take(120),
            "landlordPhone" to landlordPhone.trim().take(32),
            "tenantName" to tenantName.trim().take(120),
            "tenantPhone" to tenantPhone.trim().take(32),
            "status" to HandoverAct.STATUS_DRAFT,
            "latitude" to latitude,
            "longitude" to longitude,
            "locationLabel" to locationLabel.trim().take(200),
            "coldWaterMeter" to "",
            "hotWaterMeter" to "",
            "electricityMeter" to "",
            "gasMeter" to "",
            "photosCount" to 0L,
            "defectsCount" to 0L,
            "creatorSignedAtMillis" to 0L,
            "creatorSmsCodeHash" to "",
            "counterpartyConfirmed" to false,
            "counterpartyName" to "",
            "counterpartyPhone" to "",
            "counterpartySignedAtMillis" to 0L,
            "counterpartySmsCodeHash" to "",
            "shareToken" to shareToken,
            "selectedPlan" to selectedPlan,
            "pdfGenerated" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        return try {
            actDocRef.set(actPayload).await()
            val batch = db.batch()
            InspectionChecklistDefaults.defaultItems.forEachIndexed { index, template ->
                val itemId = "item_${index}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                val itemRef = itemsCollection(uid, actId).document(itemId)
                val itemPayload = mapOf(
                    "userId" to uid,
                    "actId" to actId,
                    "zone" to template.zone,
                    "title" to template.title,
                    "condition" to ChecklistItem.CONDITION_PENDING,
                    "comment" to "",
                    "photoLocalPath" to "",
                    "photoCapturedAtIso" to "",
                    "photoLat" to 0.0,
                    "photoLng" to 0.0,
                    "photoSha256" to "",
                    "sortOrder" to index.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                batch.set(itemRef, itemPayload)
            }
            batch.commit().await()
            Result.success(actId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, actDocRef.path)
            Result.failure(e)
        }
    }

    suspend fun getAct(actId: String): Result<HandoverAct?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = actsCollection(uid).document(actId)
        return try {
            val act = docRef.get().await().toObject(
                HandoverAct::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(act)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateActDetails(act: HandoverAct): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        require(act.id.isNotBlank()) { "Act ID must not be blank" }
        val docRef = actsCollection(uid).document(act.id)
        val updates = mapOf(
            "title" to act.title.trim().take(120),
            "address" to act.address.trim().take(240),
            "apartmentNumber" to act.apartmentNumber.trim().take(40),
            "depositAmountRub" to act.depositAmountRub.coerceIn(0L, 100_000_000L),
            "monthlyRentRub" to act.monthlyRentRub.coerceIn(0L, 100_000_000L),
            "landlordName" to act.landlordName.trim().take(120),
            "landlordPhone" to act.landlordPhone.trim().take(32),
            "tenantName" to act.tenantName.trim().take(120),
            "tenantPhone" to act.tenantPhone.trim().take(32),
            "latitude" to act.latitude,
            "longitude" to act.longitude,
            "locationLabel" to act.locationLabel.trim().take(200),
            "coldWaterMeter" to act.coldWaterMeter.trim().take(40),
            "hotWaterMeter" to act.hotWaterMeter.trim().take(40),
            "electricityMeter" to act.electricityMeter.trim().take(40),
            "gasMeter" to act.gasMeter.trim().take(40),
            "photosCount" to act.photosCount,
            "defectsCount" to act.defectsCount,
            "selectedPlan" to act.selectedPlan,
            "pdfGenerated" to act.pdfGenerated,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markPdfGenerated(actId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = actsCollection(uid).document(actId)
        return try {
            docRef.update(
                mapOf(
                    "pdfGenerated" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChecklistItemAndCounters(
        actId: String,
        item: ChecklistItem,
        allItemsAfterEdit: List<ChecklistItem>,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        require(item.id.isNotBlank()) { "Checklist item ID must not be blank" }
        val itemRef = itemsCollection(uid, actId).document(item.id)
        val actRef = actsCollection(uid).document(actId)

        val photosCount = allItemsAfterEdit.count { it.photoLocalPath.isNotBlank() }.toLong()
        val defectsCount = allItemsAfterEdit.count { it.condition == ChecklistItem.CONDITION_DEFECT }.toLong()

        return try {
            val batch = db.batch()
            batch.update(
                itemRef,
                mapOf(
                    "zone" to item.zone.trim().take(80),
                    "title" to item.title.trim().take(160),
                    "condition" to item.condition,
                    "comment" to item.comment.trim().take(500),
                    "photoLocalPath" to item.photoLocalPath.take(400),
                    "photoCapturedAtIso" to item.photoCapturedAtIso.take(64),
                    "photoLat" to item.photoLat,
                    "photoLng" to item.photoLng,
                    "photoSha256" to item.photoSha256.take(128),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                actRef,
                mapOf(
                    "photosCount" to photosCount,
                    "defectsCount" to defectsCount,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, itemRef.path)
            Result.failure(e)
        }
    }

    suspend fun addCustomChecklistItem(
        actId: String,
        zone: String,
        title: String,
        sortOrder: Long,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val itemId = "item_custom_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val itemRef = itemsCollection(uid, actId).document(itemId)
        val payload = mapOf(
            "userId" to uid,
            "actId" to actId,
            "zone" to zone.trim().take(80),
            "title" to title.trim().take(160),
            "condition" to ChecklistItem.CONDITION_PENDING,
            "comment" to "",
            "photoLocalPath" to "",
            "photoCapturedAtIso" to "",
            "photoLat" to 0.0,
            "photoLng" to 0.0,
            "photoSha256" to "",
            "sortOrder" to sortOrder,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            itemRef.set(payload).await()
            Result.success(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, itemRef.path)
            Result.failure(e)
        }
    }

    suspend fun signAsCreator(
        actId: String,
        phone: String,
        smsCode: String,
        shareToken: String,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = actsCollection(uid).document(actId)
        val signedAt = System.currentTimeMillis()
        val digest = sha256Hex("CREATOR|$actId|$phone|$smsCode|$signedAt")
        return try {
            docRef.update(
                mapOf(
                    "status" to HandoverAct.STATUS_PENDING_COUNTERPARTY,
                    "creatorSignedAtMillis" to signedAt,
                    "creatorSmsCodeHash" to digest,
                    "shareToken" to shareToken,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun confirmAsCounterparty(
        actId: String,
        counterpartyName: String,
        counterpartyPhone: String,
        smsCode: String,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = actsCollection(uid).document(actId)
        val signedAt = System.currentTimeMillis()
        val digest = sha256Hex("COUNTERPARTY|$actId|$counterpartyPhone|$smsCode|$signedAt")
        return try {
            docRef.update(
                mapOf(
                    "status" to HandoverAct.STATUS_SIGNED,
                    "counterpartyConfirmed" to true,
                    "counterpartyName" to counterpartyName.trim().take(120),
                    "counterpartyPhone" to counterpartyPhone.trim().take(32),
                    "counterpartySignedAtMillis" to signedAt,
                    "counterpartySmsCodeHash" to digest,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDraftAct(actId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val actRef = actsCollection(uid).document(actId)
        return try {
            val itemsSnapshot = itemsCollection(uid, actId).get().await()
            val batch = db.batch()
            itemsSnapshot.documents.forEach { batch.delete(it.reference) }
            batch.delete(actRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, actRef.path)
            Result.failure(e)
        }
    }

    suspend fun savePlanSettings(
        planType: String,
        companyName: String,
        b2bActive: Boolean,
        incrementGeneratedCount: Boolean = false,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = settingsDoc(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "planType" to planType,
                    "companyName" to companyName.trim().take(120),
                    "actsGeneratedCount" to if (incrementGeneratedCount) 1L else 0L,
                    "b2bActive" to b2bActive,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val currentCount = existingSnap.getLong("actsGeneratedCount") ?: 0L
                val updates = mapOf(
                    "planType" to planType,
                    "companyName" to companyName.trim().take(120),
                    "actsGeneratedCount" to if (incrementGeneratedCount) currentCount + 1L else currentCount,
                    "b2bActive" to b2bActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        fun sha256Hex(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(32)
        }
    }
}
