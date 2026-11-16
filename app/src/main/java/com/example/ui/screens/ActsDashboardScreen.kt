package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.HandoverAct
import com.example.data.model.UserPlanSettings
import com.example.ui.viewmodel.UiState
import com.example.util.LocationHelper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private enum class ActFilter(val label: String) {
    ALL("Все"),
    DRAFT("Черновики"),
    PENDING("Ждут подписи"),
    SIGNED("Подписаны"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActsDashboardScreen(
    actsState: UiState<List<HandoverAct>>,
    planSettings: UserPlanSettings,
    isBusy: Boolean,
    onOpenAct: (String) -> Unit,
    onCreateAct: (
        title: String,
        address: String,
        apartment: String,
        depositRub: Long,
        rentRub: Long,
        role: String,
        landlordName: String,
        landlordPhone: String,
        tenantName: String,
        tenantPhone: String,
        lat: Double,
        lng: Double,
        locationLabel: String,
    ) -> Unit,
    onDeleteDraftAct: (String) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(ActFilter.ALL) }
    var showCreateSheet by remember { mutableStateOf(false) }

    val rubFormat = remember { NumberFormat.getIntegerInstance(Locale("ru", "RU")) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (actsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Text(
                            text = actsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            is UiState.Success -> {
                val allActs = actsState.data
                val filteredActs = remember(allActs, selectedFilter) {
                    when (selectedFilter) {
                        ActFilter.ALL -> allActs
                        ActFilter.DRAFT -> allActs.filter { it.status == HandoverAct.STATUS_DRAFT }
                        ActFilter.PENDING -> allActs.filter { it.status == HandoverAct.STATUS_PENDING_COUNTERPARTY }
                        ActFilter.SIGNED -> allActs.filter { it.status == HandoverAct.STATUS_SIGNED }
                    }
                }
                val totalProtectedDeposit = remember(allActs) {
                    allActs.sumOf { it.depositAmountRub }
                }

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        ProtectionSummaryBanner(
                            totalDepositRub = totalProtectedDeposit,
                            actsCount = allActs.size,
                            signedCount = allActs.count { it.status == HandoverAct.STATUS_SIGNED },
                            planSettings = planSettings,
                            rubFormat = rubFormat,
                        )
                    }

                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(ActFilter.entries) { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
                                )
                            }
                        }
                    }

                    if (filteredActs.isEmpty()) {
                        item {
                            EmptyActsCard(
                                isFiltered = selectedFilter != ActFilter.ALL,
                                onCreateClick = { showCreateSheet = true },
                            )
                        }
                    } else {
                        items(filteredActs, key = { it.id }) { act ->
                            HandoverActCard(
                                act = act,
                                rubFormat = rubFormat,
                                onClick = { onOpenAct(act.id) },
                                onDelete = if (act.status != HandoverAct.STATUS_SIGNED) {
                                    { onDeleteDraftAct(act.id) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_act_fab"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Создать акт")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Новый акт", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showCreateSheet) {
        CreateActBottomSheet(
            isBusy = isBusy,
            onDismiss = { showCreateSheet = false },
            onSubmit = { title, address, apt, deposit, rent, role, lName, lPhone, tName, tPhone, lat, lng, locLabel ->
                showCreateSheet = false
                onCreateAct(title, address, apt, deposit, rent, role, lName, lPhone, tName, tPhone, lat, lng, locLabel)
            },
        )
    }
}

@Composable
private fun ProtectionSummaryBanner(
    totalDepositRub: Long,
    actsCount: Int,
    signedCount: Int,
    planSettings: UserPlanSettings,
    rubFormat: NumberFormat,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF132A4A),
                            Color(0xFF1A3A63),
                        )
                    )
                )
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ЗАЛОГИ ПОД ЗАЩИТОЙ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7F3D0),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Surface(
                        color = if (planSettings.b2bActive) Color(0xFF0D7A5F) else Color(0x33FFFFFF),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = if (planSettings.b2bActive) "B2B Pro" else "Разовый акт",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Text(
                    text = "${rubFormat.format(totalDepositRub)} ₽",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Всего актов: $actsCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                    Text(
                        text = "Подписано обеими сторонами: $signedCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFA7F3D0),
                    )
                }
            }
        }
    }
}

@Composable
private fun HandoverActCard(
    act: HandoverAct,
    rubFormat: NumberFormat,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val (statusText, statusBg, statusFg, statusIcon) = when (act.status) {
        HandoverAct.STATUS_SIGNED -> Quad(
            "Подписан обеими сторонами",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Filled.CheckCircle,
        )
        HandoverAct.STATUS_PENDING_COUNTERPARTY -> Quad(
            "Ждет подтверждения второй стороны",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.Schedule,
        )
        else -> Quad(
            "Черновик осмотра",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Description,
        )
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("act_card_${act.id}"),
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
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusFg,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusFg,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Удалить черновик",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = act.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val aptPart = if (act.apartmentNumber.isNotBlank()) ", кв. ${act.apartmentNumber}" else ""
                Text(
                    text = "${act.address}$aptPart",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill(
                    icon = Icons.Filled.Shield,
                    text = "Залог ${rubFormat.format(act.depositAmountRub)} ₽",
                    tint = MaterialTheme.colorScheme.secondary,
                )
                MetricPill(
                    icon = Icons.Filled.CameraAlt,
                    text = "${act.photosCount} фото",
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (act.defectsCount > 0L) {
                    MetricPill(
                        icon = Icons.Filled.WarningAmber,
                        text = "${act.defectsCount} дефект(а)",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (act.locationLabel.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = act.locationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyActsCard(
    isFiltered: Boolean,
    onCreateClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_handover_hero),
                contentDescription = "Иллюстрация квартиры",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Text(
                text = if (isFiltered) "В этом разделе пока пусто" else "Создайте первый акт приема-передачи",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Сфотографируйте углы, мебель, технику и счетчики при заезде, соберите PDF-документ с геолокацией и подпишите его СМС-кодом.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_first_act_button"),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Оформить акт заселения")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateActBottomSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        title: String,
        address: String,
        apartment: String,
        depositRub: Long,
        rentRub: Long,
        role: String,
        landlordName: String,
        landlordPhone: String,
        tenantName: String,
        tenantPhone: String,
        lat: Double,
        lng: Double,
        locationLabel: String,
    ) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var role by remember { mutableStateOf(HandoverAct.ROLE_TENANT) }
    var title by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var apartment by remember { mutableStateOf("") }
    var depositStr by remember { mutableStateOf("30000") }
    var rentStr by remember { mutableStateOf("50000") }
    var landlordName by remember { mutableStateOf("") }
    var landlordPhone by remember { mutableStateOf("") }
    var tenantName by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var locationLabel by remember { mutableStateOf("") }
    var fetchingGps by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchingGps = true
            scope.launch {
                val stamp = LocationHelper.fetchCurrentGeoStamp(context)
                fetchingGps = false
                if (stamp != null) {
                    latitude = stamp.latitude
                    longitude = stamp.longitude
                    locationLabel = stamp.label
                    if (address.isBlank() && stamp.resolvedAddress.isNotBlank()) {
                        address = stamp.resolvedAddress
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Новый акт приема-передачи",
                style = MaterialTheme.typography.headlineSmall,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = role == HandoverAct.ROLE_TENANT,
                    onClick = { role = HandoverAct.ROLE_TENANT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Я Наниматель")
                }
                SegmentedButton(
                    selected = role == HandoverAct.ROLE_LANDLORD,
                    onClick = { role = HandoverAct.ROLE_LANDLORD },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Я Собственник")
                }
            }

            OutlinedButton(
                onClick = {
                    val hasLoc = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    if (hasLoc) {
                        fetchingGps = true
                        scope.launch {
                            val stamp = LocationHelper.fetchCurrentGeoStamp(context)
                            fetchingGps = false
                            if (stamp != null) {
                                latitude = stamp.latitude
                                longitude = stamp.longitude
                                locationLabel = stamp.label
                                if (address.isBlank() && stamp.resolvedAddress.isNotBlank()) {
                                    address = stamp.resolvedAddress
                                }
                            }
                        }
                    } else {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("detect_gps_button"),
            ) {
                if (fetchingGps) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Определяем координаты...")
                } else {
                    Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (locationLabel.isBlank()) "Определить GPS-метку квартиры"
                        else "GPS: $locationLabel"
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название объекта (напр., 1-к на Тверской)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_act_title"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес квартиры") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(2.2f)
                        .testTag("input_act_address"),
                )
                OutlinedTextField(
                    value = apartment,
                    onValueChange = { apartment = it },
                    label = { Text("Кв.") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = depositStr,
                    onValueChange = { depositStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Залог (₽)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_deposit_amount"),
                )
                OutlinedTextField(
                    value = rentStr,
                    onValueChange = { rentStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Аренда / мес (₽)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = landlordName,
                    onValueChange = { landlordName = it },
                    label = { Text("ФИО собственника") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = landlordPhone,
                    onValueChange = { landlordPhone = it },
                    label = { Text("Тел. собственника") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = tenantName,
                    onValueChange = { tenantName = it },
                    label = { Text("ФИО нанимателя") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = tenantPhone,
                    onValueChange = { tenantPhone = it },
                    label = { Text("Тел. нанимателя") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = {
                    val safeTitle = title.trim().ifBlank { "Квартира в аренду" }
                    val safeAddress = address.trim().ifBlank { "Адрес не указан" }
                    onSubmit(
                        safeTitle,
                        safeAddress,
                        apartment.trim(),
                        depositStr.toLongOrNull() ?: 30000L,
                        rentStr.toLongOrNull() ?: 0L,
                        role,
                        landlordName.trim(),
                        landlordPhone.trim(),
                        tenantName.trim(),
                        tenantPhone.trim(),
                        latitude,
                        longitude,
                        locationLabel.ifBlank { "GPS не зафиксирован" },
                    )
                },
                enabled = !isBusy && title.isNotBlank() && address.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_create_act_button"),
            ) {
                Text("Создать акт и открыть чек-лист")
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
