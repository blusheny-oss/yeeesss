package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GasMeter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.ChecklistItem
import com.example.data.model.HandoverAct
import com.example.data.model.UserPlanSettings
import com.example.ui.viewmodel.UiState
import com.example.util.PdfActGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActDetailScreen(
    actState: UiState<HandoverAct?>,
    itemsState: UiState<List<ChecklistItem>>,
    planSettings: UserPlanSettings,
    isBusy: Boolean,
    onBack: () -> Unit,
    onLaunchCamera: (ChecklistItem, HandoverAct) -> Unit,
    onUpdateChecklistItem: (ChecklistItem, List<ChecklistItem>) -> Unit,
    onAddCustomItem: (zone: String, title: String, nextOrder: Long) -> Unit,
    onSaveActDetails: (HandoverAct) -> Unit,
    onMarkPdfGenerated: (String) -> Unit,
    onCreatorSign: (actId: String, phone: String, smsCode: String, shareToken: String) -> Unit,
    onCounterpartyConfirm: (actId: String, name: String, phone: String, smsCode: String) -> Unit,
) {
    BackHandler { onBack() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Чек-лист", "Счетчики", "PDF и Подпись")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (actState as? UiState.Success)?.data?.title ?: "Акт осмотра"
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("act_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад к списку",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (actState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(actState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val act = actState.data
                if (act == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Акт не найден")
                    }
                    return@Scaffold
                }

                val items = (itemsState as? UiState.Success)?.data ?: emptyList()
                val isLocked = act.status == HandoverAct.STATUS_SIGNED

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        tabTitles.forEachIndexed { idx, label ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("act_tab_$idx"),
                            )
                        }
                    }

                    if (isLocked) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Акт подписан обеими сторонами и защищен от изменений.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize(),
                    ) {
                        when (selectedTab) {
                            0 -> ChecklistTab(
                                act = act,
                                items = items,
                                isLocked = isLocked,
                                onLaunchCamera = { item -> onLaunchCamera(item, act) },
                                onUpdateItem = { updated -> onUpdateChecklistItem(updated, items) },
                                onAddCustomItem = { zone, title ->
                                    onAddCustomItem(zone, title, (items.maxOfOrNull { it.sortOrder } ?: 0L) + 1L)
                                },
                                onGoToPdfTab = { selectedTab = 2 },
                            )
                            1 -> MetersAndPartiesTab(
                                act = act,
                                isLocked = isLocked,
                                isBusy = isBusy,
                                onSave = onSaveActDetails,
                            )
                            2 -> PdfAndSignTab(
                                act = act,
                                items = items,
                                planSettings = planSettings,
                                isBusy = isBusy,
                                onMarkPdfGenerated = { onMarkPdfGenerated(act.id) },
                                onCreatorSign = { phone, code, token ->
                                    onCreatorSign(act.id, phone, code, token)
                                },
                                onCounterpartyConfirm = { name, phone, code ->
                                    onCounterpartyConfirm(act.id, name, phone, code)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistTab(
    act: HandoverAct,
    items: List<ChecklistItem>,
    isLocked: Boolean,
    onLaunchCamera: (ChecklistItem) -> Unit,
    onUpdateItem: (ChecklistItem) -> Unit,
    onAddCustomItem: (zone: String, title: String) -> Unit,
    onGoToPdfTab: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCommentItem by remember { mutableStateOf<ChecklistItem?>(null) }

    val checkedCount = items.count { it.condition != ChecklistItem.CONDITION_PENDING }
    val photosCount = items.count { it.photoLocalPath.isNotBlank() }
    val defectsCount = items.count { it.condition == ChecklistItem.CONDITION_DEFECT }
    val progress = if (items.isNotEmpty()) checkedCount.toFloat() / items.size else 0f

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Осмотр: $checkedCount из ${items.size}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Фото: $photosCount • Дефекты: $defectsCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                    Text(
                        text = "Галерея отключена: фото делаются только живой камерой с привязкой к GPS и времени.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        items(items, key = { it.id }) { item ->
            ChecklistItemCard(
                item = item,
                isLocked = isLocked,
                onConditionChange = { newCond ->
                    onUpdateItem(item.copy(condition = newCond))
                },
                onTakePhoto = { onLaunchCamera(item) },
                onEditComment = { editingCommentItem = item },
            )
        }

        if (!isLocked) {
            item {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_custom_item_button"),
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить зону или дефект")
                }
            }
        }

        item {
            Button(
                onClick = onGoToPdfTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("go_to_pdf_sign_button"),
            ) {
                Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Перейти к PDF и подписи")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        var zone by remember { mutableStateOf("Мебель и дефекты") }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новый пункт осмотра") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = zone,
                        onValueChange = { zone = it },
                        label = { Text("Зона (напр., Балкон, Кухня)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Что проверяем / дефект") },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (zone.isNotBlank() && title.isNotBlank()) {
                            onAddCustomItem(zone.trim(), title.trim())
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Отмена") }
            },
        )
    }

    editingCommentItem?.let { targetItem ->
        var commentText by remember(targetItem.id) { mutableStateOf(targetItem.comment) }
        AlertDialog(
            onDismissRequest = { editingCommentItem = null },
            title = { Text("Комментарий к пункту") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(targetItem.title, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Опишите скол, царапину или состояние") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateItem(targetItem.copy(comment = commentText.trim()))
                        editingCommentItem = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCommentItem = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ChecklistItemCard(
    item: ChecklistItem,
    isLocked: Boolean,
    onConditionChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onEditComment: () -> Unit,
) {
    val borderColor = when (item.condition) {
        ChecklistItem.CONDITION_DEFECT -> MaterialTheme.colorScheme.error
        ChecklistItem.CONDITION_OK -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = item.zone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (item.photoSha256.isNotBlank()) {
                    Text(
                        text = "SHA256: ${item.photoSha256.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = item.condition == ChecklistItem.CONDITION_OK,
                    onClick = { if (!isLocked) onConditionChange(ChecklistItem.CONDITION_OK) },
                    label = { Text("Исправно") },
                    enabled = !isLocked,
                )
                FilterChip(
                    selected = item.condition == ChecklistItem.CONDITION_DEFECT,
                    onClick = { if (!isLocked) onConditionChange(ChecklistItem.CONDITION_DEFECT) },
                    label = { Text("Дефект") },
                    enabled = !isLocked,
                )
                FilterChip(
                    selected = item.condition == ChecklistItem.CONDITION_NOTE,
                    onClick = { if (!isLocked) onConditionChange(ChecklistItem.CONDITION_NOTE) },
                    label = { Text("Замечание") },
                    enabled = !isLocked,
                )
            }

            if (item.comment.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.comment,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            if (item.photoLocalPath.isNotBlank() && File(item.photoLocalPath).exists()) {
                AsyncImage(
                    model = File(item.photoLocalPath),
                    contentDescription = "Фотофиксация ${item.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }

            if (!isLocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onTakePhoto,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("capture_photo_button_${item.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (item.photoLocalPath.isBlank()) "Снять фото" else "Переснять")
                    }
                    OutlinedButton(
                        onClick = onEditComment,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(if (item.comment.isBlank()) "Комментарий" else "Изм. заметку")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetersAndPartiesTab(
    act: HandoverAct,
    isLocked: Boolean,
    isBusy: Boolean,
    onSave: (HandoverAct) -> Unit,
) {
    var coldWater by remember(act.id, act.coldWaterMeter) { mutableStateOf(act.coldWaterMeter) }
    var hotWater by remember(act.id, act.hotWaterMeter) { mutableStateOf(act.hotWaterMeter) }
    var electricity by remember(act.id, act.electricityMeter) { mutableStateOf(act.electricityMeter) }
    var gas by remember(act.id, act.gasMeter) { mutableStateOf(act.gasMeter) }
    var landlordName by remember(act.id, act.landlordName) { mutableStateOf(act.landlordName) }
    var landlordPhone by remember(act.id, act.landlordPhone) { mutableStateOf(act.landlordPhone) }
    var tenantName by remember(act.id, act.tenantName) { mutableStateOf(act.tenantName) }
    var tenantPhone by remember(act.id, act.tenantPhone) { mutableStateOf(act.tenantPhone) }
    var depositStr by remember(act.id, act.depositAmountRub) { mutableStateOf(act.depositAmountRub.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Показания счетчиков ЖКУ", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = coldWater,
            onValueChange = { coldWater = it },
            enabled = !isLocked,
            label = { Text("ХВС — Холодная вода (м³)") },
            leadingIcon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("meter_cold_water"),
        )
        OutlinedTextField(
            value = hotWater,
            onValueChange = { hotWater = it },
            enabled = !isLocked,
            label = { Text("ГВС — Горячая вода (м³)") },
            leadingIcon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("meter_hot_water"),
        )
        OutlinedTextField(
            value = electricity,
            onValueChange = { electricity = it },
            enabled = !isLocked,
            label = { Text("Электроэнергия (кВт·ч)") },
            leadingIcon = { Icon(Icons.Filled.ElectricBolt, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("meter_electricity"),
        )
        OutlinedTextField(
            value = gas,
            onValueChange = { gas = it },
            enabled = !isLocked,
            label = { Text("Газ (м³, если есть)") },
            leadingIcon = { Icon(Icons.Filled.GasMeter, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text("Стороны договора и залог", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = depositStr,
            onValueChange = { depositStr = it.filter { c -> c.isDigit() } },
            enabled = !isLocked,
            label = { Text("Сумма залога / депозита (₽)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = landlordName,
            onValueChange = { landlordName = it },
            enabled = !isLocked,
            label = { Text("ФИО собственника") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = landlordPhone,
            onValueChange = { landlordPhone = it },
            enabled = !isLocked,
            label = { Text("Телефон собственника") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tenantName,
            onValueChange = { tenantName = it },
            enabled = !isLocked,
            label = { Text("ФИО нанимателя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tenantPhone,
            onValueChange = { tenantPhone = it },
            enabled = !isLocked,
            label = { Text("Телефон нанимателя") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!isLocked) {
            Button(
                onClick = {
                    onSave(
                        act.copy(
                            coldWaterMeter = coldWater.trim(),
                            hotWaterMeter = hotWater.trim(),
                            electricityMeter = electricity.trim(),
                            gasMeter = gas.trim(),
                            landlordName = landlordName.trim(),
                            landlordPhone = landlordPhone.trim(),
                            tenantName = tenantName.trim(),
                            tenantPhone = tenantPhone.trim(),
                            depositAmountRub = depositStr.toLongOrNull() ?: act.depositAmountRub,
                        )
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_meters_button"),
            ) {
                Text("Сохранить показания и данные")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PdfAndSignTab(
    act: HandoverAct,
    items: List<ChecklistItem>,
    planSettings: UserPlanSettings,
    isBusy: Boolean,
    onMarkPdfGenerated: () -> Unit,
    onCreatorSign: (phone: String, smsCode: String, shareToken: String) -> Unit,
    onCounterpartyConfirm: (name: String, phone: String, smsCode: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rubFormat = remember { NumberFormat.getIntegerInstance(Locale("ru", "RU")) }

    var generatedPdfFile by remember(act.id) {
        val candidate = File(context.filesDir, "acts_pdf/Akt_${act.id}.pdf")
        mutableStateOf(if (candidate.exists()) candidate else null)
    }
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var generatingPdf by remember { mutableStateOf(false) }

    // SMS Signing state
    val defaultCreatorPhone = if (act.creatorRole == HandoverAct.ROLE_TENANT) act.tenantPhone else act.landlordPhone
    var creatorPhone by remember(act.id, defaultCreatorPhone) { mutableStateOf(defaultCreatorPhone) }
    var sentCreatorCode by remember { mutableStateOf<String?>(null) }
    var enteredCreatorCode by remember { mutableStateOf("") }

    val defaultCpName = if (act.creatorRole == HandoverAct.ROLE_TENANT) act.landlordName else act.tenantName
    val defaultCpPhone = if (act.creatorRole == HandoverAct.ROLE_TENANT) act.landlordPhone else act.tenantPhone
    var cpName by remember(act.id, defaultCpName) { mutableStateOf(defaultCpName) }
    var cpPhone by remember(act.id, defaultCpPhone) { mutableStateOf(defaultCpPhone) }
    var sentCpCode by remember { mutableStateOf<String?>(null) }
    var enteredCpCode by remember { mutableStateOf("") }

    LaunchedEffect(generatedPdfFile) {
        val file = generatedPdfFile
        if (file != null && file.exists()) {
            renderedPages = renderPdfPages(file)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. PDF Generation Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Юридический PDF-акт", style = MaterialTheme.typography.titleLarge)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (planSettings.b2bActive) "B2B Pro (0 ₽)" else "Разовый 390 ₽",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    text = "Автоматическая склейка чек-листа, показаний счетчиков, фото с GPS-штампами и формулировок ГК РФ для защиты залога ${rubFormat.format(act.depositAmountRub)} ₽.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = {
                        generatingPdf = true
                        scope.launch {
                            val file = PdfActGenerator.generateActPdf(context, act, items)
                            generatedPdfFile = file
                            renderedPages = renderPdfPages(file)
                            generatingPdf = false
                            onMarkPdfGenerated()
                        }
                    },
                    enabled = !generatingPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("generate_pdf_button"),
                ) {
                    if (generatingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Собираем PDF-акт...")
                    } else {
                        Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (generatedPdfFile == null) "Сформировать PDF-акт" else "Обновить PDF-акт")
                    }
                }

                if (generatedPdfFile != null) {
                    OutlinedButton(
                        onClick = {
                            sharePdfFile(context, generatedPdfFile!!, act)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("share_pdf_button"),
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отправить PDF второй стороне")
                    }
                }
            }
        }

        // In-app PDF Page Preview
        if (renderedPages.isNotEmpty()) {
            Text(
                text = "Предпросмотр PDF (${renderedPages.size} стр.)",
                style = MaterialTheme.typography.titleMedium,
            )
            renderedPages.forEachIndexed { idx, bmp ->
                Card(
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Страница PDF ${idx + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Страница ${idx + 1} из ${renderedPages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }

        // 2. Creator SMS Signature Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (act.creatorSignedAtMillis > 0L) Icons.Filled.CheckCircle else Icons.Filled.Sms,
                        contentDescription = null,
                        tint = if (act.creatorSignedAtMillis > 0L) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Шаг 1: Ваша подпись (СМС-код ПЭП)", style = MaterialTheme.typography.titleMedium)
                }

                if (act.creatorSignedAtMillis > 0L) {
                    val dt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")).format(Date(act.creatorSignedAtMillis))
                    Text(
                        text = "Подписано $dt • Хэш: ${act.creatorSmsCodeHash.take(14)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    OutlinedTextField(
                        value = creatorPhone,
                        onValueChange = { creatorPhone = it },
                        label = { Text("Ваш номер телефона") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("creator_phone_input"),
                    )
                    OutlinedButton(
                        onClick = {
                            val code = Random.nextInt(100000, 999999).toString()
                            sentCreatorCode = code
                        },
                        enabled = creatorPhone.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("request_creator_sms_button"),
                    ) {
                        Text("Сформировать СМС-код подтверждения")
                    }

                    if (sentCreatorCode != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "СМС-код для подписания: $sentCreatorCode (введите код ниже для фиксации ПЭП)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        OutlinedTextField(
                            value = enteredCreatorCode,
                            onValueChange = { enteredCreatorCode = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Введите 6-значный код") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("creator_sms_code_input"),
                        )
                        Button(
                            onClick = {
                                val token = act.shareToken.ifBlank { "AKT${Random.nextInt(100000, 999999)}" }
                                onCreatorSign(creatorPhone.trim(), enteredCreatorCode, token)
                            },
                            enabled = !isBusy && enteredCreatorCode == sentCreatorCode,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("creator_sign_button"),
                        ) {
                            Icon(imageVector = Icons.Filled.VerifiedUser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Подписать акт ПЭП")
                        }
                    }
                }
            }
        }

        // 3. Counterparty Confirmation Link & Second Signature Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (act.counterpartyConfirmed) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (act.counterpartyConfirmed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Шаг 2: Подтверждение второй стороны", style = MaterialTheme.typography.titleMedium)
                }

                if (act.counterpartyConfirmed) {
                    val dt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
                        .format(Date(act.counterpartySignedAtMillis))
                    Text(
                        text = "Подтвердил(а): ${act.counterpartyName} (${act.counterpartyPhone}) • $dt\nХэш ПЭП: ${act.counterpartySmsCodeHash.take(14)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else if (act.creatorSignedAtMillis == 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сначала подпишите акт на Шаге 1, чтобы открыть отправку ссылки и подтверждение второй стороны.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val confirmLink = "https://ais-pre-eoijfory7nkiud7q3sexqm-360385484188.us-west1.run.app/confirm?act=${act.id}&code=${act.shareToken}"
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "Код акта: ${act.shareToken}\nСсылка: $confirmLink",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val msg = "Прошу подтвердить цифровой акт приема-передачи квартиры «${act.title}» (${act.address}). Залог: ${rubFormat.format(act.depositAmountRub)} ₽. Код подтверждения: ${act.shareToken}. Ссылка: $confirmLink"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Акт приема-передачи квартиры")
                                    putExtra(Intent.EXTRA_TEXT, msg)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Отправить ссылку для подтверждения"))
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("share_confirm_link_button"),
                        ) {
                            Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ссылка")
                        }
                        OutlinedButton(
                            onClick = {
                                val smsBody = "Подтвердите акт приема-передачи (${act.title}). Код: ${act.shareToken}. $confirmLink"
                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(cpPhone)}")).apply {
                                    putExtra("sms_body", smsBody)
                                }
                                context.startActivity(smsIntent)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(imageVector = Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("В СМС")
                        }
                    }

                    OutlinedTextField(
                        value = cpName,
                        onValueChange = { cpName = it },
                        label = { Text("ФИО второй стороны") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("counterparty_name_input"),
                    )
                    OutlinedTextField(
                        value = cpPhone,
                        onValueChange = { cpPhone = it },
                        label = { Text("Телефон второй стороны") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("counterparty_phone_input"),
                    )
                    OutlinedButton(
                        onClick = {
                            sentCpCode = Random.nextInt(100000, 999999).toString()
                        },
                        enabled = cpName.isNotBlank() && cpPhone.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("request_counterparty_sms_button"),
                    ) {
                        Text("Получить код подтверждения второй стороны")
                    }

                    if (sentCpCode != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "Код подтверждения второй стороны: $sentCpCode",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        OutlinedTextField(
                            value = enteredCpCode,
                            onValueChange = { enteredCpCode = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Введите 6-значный код") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("counterparty_sms_code_input"),
                        )
                        Button(
                            onClick = {
                                onCounterpartyConfirm(cpName.trim(), cpPhone.trim(), enteredCpCode)
                            },
                            enabled = !isBusy && enteredCpCode == sentCpCode && cpName.isNotBlank() && cpPhone.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("counterparty_sign_button"),
                        ) {
                            Icon(imageVector = Icons.Filled.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Подтвердить и зафиксировать акт")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private suspend fun renderPdfPages(file: File): List<Bitmap> = withContext(Dispatchers.IO) {
    val result = mutableListOf<Bitmap>()
    try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val width = 900
                        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        result.add(bmp)
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    result
}

private fun sharePdfFile(context: Context, pdfFile: File, act: HandoverAct) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        pdfFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Акт приема-передачи: ${act.title}")
        putExtra(
            Intent.EXTRA_TEXT,
            "Цифровой акт приема-передачи квартиры (${act.address}). Код подтверждения: ${act.shareToken}"
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться PDF-актом"))
}
