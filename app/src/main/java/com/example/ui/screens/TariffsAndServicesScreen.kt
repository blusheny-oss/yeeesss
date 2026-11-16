package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UserPlanSettings
import java.text.NumberFormat
import java.util.Locale

@Composable
fun TariffsAndServicesScreen(
    planSettings: UserPlanSettings,
    isBusy: Boolean,
    onSelectPlan: (planType: String, companyName: String, b2bActive: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val rubFormat = remember { NumberFormat.getIntegerInstance(Locale("ru", "RU")) }
    var agencyName by remember(planSettings.companyName) { mutableStateOf(planSettings.companyName) }
    var cleaningRooms by remember { mutableIntStateOf(1) }
    var insuredDeposit by remember { mutableIntStateOf(30000) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Тарифы защиты залога",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Стоимость одного акта ниже 2% от среднего залога 30 000 ₽ — полная юридическая фиксация при заезде и выезде.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Single Act Tier
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = if (!planSettings.b2bActive) 2.dp else 1.dp,
                    color = if (!planSettings.b2bActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Разовый PDF-акт", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            text = "299 – 490 ₽",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "• Защищенный PDF с формулировками ГК РФ\n• Живые фото с GPS, временем и SHA-256\n• Двусторонняя цифровая подпись по СМС-коду",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            onSelectPlan(UserPlanSettings.PLAN_PAY_PER_ACT, agencyName.trim(), false)
                        },
                        enabled = !isBusy && planSettings.b2bActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("select_single_plan_button"),
                    ) {
                        Text(if (!planSettings.b2bActive) "Текущий тариф" else "Перейти на разовую оплату")
                    }
                }
            }

            // B2B Pro Tier
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = if (planSettings.b2bActive) 2.dp else 1.dp,
                    color = if (planSettings.b2bActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Business, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("B2B Pro для риелторов", style = MaterialTheme.typography.titleLarge)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                text = "1 500 ₽/мес",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = "• Безлимитное количество актов для риелторов и УК\n• Облачное хранение всех объектов и архив\n• Сформировано актов: ${planSettings.actsGeneratedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = agencyName,
                        onValueChange = { agencyName = it },
                        label = { Text("Название агентства или УК (необязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("b2b_company_input"),
                    )
                    Button(
                        onClick = {
                            onSelectPlan(UserPlanSettings.PLAN_B2B_PRO, agencyName.trim(), true)
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("select_b2b_plan_button"),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (planSettings.b2bActive) "Сохранить профиль B2B Pro" else "Активировать B2B Pro")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Партнерские сервисы при заезде и выезде",
                style = MaterialTheme.typography.headlineSmall,
            )

            // Partner 1: Apartment Insurance
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
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Страхование квартиры и отделки", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Покрывает залив соседей, поломку техники и ущерб сверх депозита. Партнерская скидка для пользователей АктЖилья.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30000, 50000, 100000).forEach { dep ->
                            FilterChip(
                                selected = insuredDeposit == dep,
                                onClick = { insuredDeposit = dep },
                                label = { Text("${rubFormat.format(dep)} ₽") },
                            )
                        }
                    }
                    val policyPrice = (insuredDeposit * 0.012).toInt().coerceAtLeast(350)
                    Text(
                        text = "Ориентировочный полис: от ${rubFormat.format(policyPrice)} ₽/мес (покрытие до ${rubFormat.format(insuredDeposit * 10)} ₽)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Запрос на партнерское страхование арендуемой квартиры (залог ${rubFormat.format(insuredDeposit)} ₽, расчет ~${policyPrice} ₽/мес) через приложение АктЖилья."
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить заявку на полис"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("partner_insurance_button"),
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Рассчитать и оформить полис")
                    }
                }
            }

            // Partner 2: Move-out Cleaning
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
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Клининг перед сдачей квартиры", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Профессиональная уборка при выезде помогает сдать квартиру в идеальном виде и вернуть 100% депозита.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "1-комн.", 2 to "2-комн.", 3 to "3-комн.").forEach { (rooms, label) ->
                            FilterChip(
                                selected = cleaningRooms == rooms,
                                onClick = { cleaningRooms = rooms },
                                label = { Text(label) },
                            )
                        }
                    }
                    val cleaningPrice = 2400 + (cleaningRooms - 1) * 900
                    Text(
                        text = "Фиксированная смета: ${rubFormat.format(cleaningPrice)} ₽ (включая кухню и санузел)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Заявка на клининг перед выездом ($cleaningRooms-комн. квартира, смета ${rubFormat.format(cleaningPrice)} ₽) через партнерскую программу АктЖилья."
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Заказать клининг при выезде"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("partner_cleaning_button"),
                    ) {
                        Text("Заказать клининг при выезде")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
