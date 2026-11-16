package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class HandoverAct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val address: String = "",
    val apartmentNumber: String = "",
    val depositAmountRub: Long = 30000L,
    val monthlyRentRub: Long = 0L,
    val creatorRole: String = ROLE_TENANT,
    val landlordName: String = "",
    val landlordPhone: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val status: String = STATUS_DRAFT,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationLabel: String = "",
    val coldWaterMeter: String = "",
    val hotWaterMeter: String = "",
    val electricityMeter: String = "",
    val gasMeter: String = "",
    val photosCount: Long = 0L,
    val defectsCount: Long = 0L,
    val creatorSignedAtMillis: Long = 0L,
    val creatorSmsCodeHash: String = "",
    val counterpartyConfirmed: Boolean = false,
    val counterpartyName: String = "",
    val counterpartyPhone: String = "",
    val counterpartySignedAtMillis: Long = 0L,
    val counterpartySmsCodeHash: String = "",
    val shareToken: String = "",
    val selectedPlan: String = PLAN_SINGLE_ACT,
    val pdfGenerated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val ROLE_TENANT = "tenant"
        const val ROLE_LANDLORD = "landlord"

        const val STATUS_DRAFT = "draft"
        const val STATUS_PENDING_COUNTERPARTY = "pending_counterparty"
        const val STATUS_SIGNED = "signed"

        const val PLAN_SINGLE_ACT = "single_act"
        const val PLAN_B2B_PRO = "b2b_pro"
    }
}

@IgnoreExtraProperties
data class ChecklistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actId: String = "",
    val zone: String = "",
    val title: String = "",
    val condition: String = CONDITION_PENDING,
    val comment: String = "",
    val photoLocalPath: String = "",
    val photoCapturedAtIso: String = "",
    val photoLat: Double = 0.0,
    val photoLng: Double = 0.0,
    val photoSha256: String = "",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val CONDITION_PENDING = "pending"
        const val CONDITION_OK = "ok"
        const val CONDITION_DEFECT = "defect"
        const val CONDITION_NOTE = "note"
    }
}

@IgnoreExtraProperties
data class UserPlanSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planType: String = PLAN_PAY_PER_ACT,
    val companyName: String = "",
    val actsGeneratedCount: Long = 0L,
    val b2bActive: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val DOC_ID = "subscription"
        const val PLAN_PAY_PER_ACT = "pay_per_act"
        const val PLAN_B2B_PRO = "b2b_pro"
    }
}

data class DefaultChecklistTemplate(
    val zone: String,
    val title: String,
)

object InspectionChecklistDefaults {
    val defaultItems: List<DefaultChecklistTemplate> = listOf(
        DefaultChecklistTemplate("Пол и плинтусы", "Покрытие пола (ламинат, паркет, плитка) и стыки"),
        DefaultChecklistTemplate("Пол и плинтусы", "Плинтусы и углы по периметру комнат"),
        DefaultChecklistTemplate("Стены и потолок", "Обои / покраска стен, углы и отсутствие пятен"),
        DefaultChecklistTemplate("Стены и потолок", "Потолок, светильники и карнизы"),
        DefaultChecklistTemplate("Техника", "Холодильник, варочная панель и духовой шкаф"),
        DefaultChecklistTemplate("Техника", "Стиральная машина, кондиционер и вытяжка"),
        DefaultChecklistTemplate("Счетчики", "Счетчики ХВС, ГВС, электричества и пломбы"),
        DefaultChecklistTemplate("Мебель и дефекты", "Диван, шкафы, столы и фурнитура (царапины, сколы)"),
        DefaultChecklistTemplate("Окна и двери", "Стеклопакеты, откосы, входная дверь и замки"),
        DefaultChecklistTemplate("Сантехника", "Смесители, ванна/душ, раковина и герметик"),
    )
}
