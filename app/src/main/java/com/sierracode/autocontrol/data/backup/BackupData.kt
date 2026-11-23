package com.sierracode.autocontrol.data.backup

import kotlinx.serialization.Serializable

sealed class BackupError(message: String) : Exception(message) {
    class CannotRead(message: String = "No se pudo leer el archivo") : BackupError(message)
    class CannotWrite(message: String = "No se pudo escribir en el destino") : BackupError(message)
    class MissingJson(message: String = "No se encontró backup.json") : BackupError(message)
    class NewerVersion(val found: Int, val supported: Int) :
        BackupError("El respaldo tiene versión $found, pero esta app solo soporta hasta $supported")
}

@Serializable
data class BackupData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = "", // Se llena dinámicamente al exportar
    val filesCount: Int = 0,     // Para validar la integridad del ZIP
    val exportedAt: Long,
    val vehicles: List<VehicleDto>,
    val expenses: List<ExpenseDto>,
    val fuelDetails: List<FuelDetailDto>,
    val maintenanceDetails: List<MaintenanceDetailDto>,
    val documents: List<DocumentDto>,
    val attachments: List<AttachmentDto> = emptyList(),
    val maintenanceSchedules: List<MaintenanceScheduleDto> = emptyList(),
    val reminders: List<ReminderDto> = emptyList(),
    val goals: List<GoalDto> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 9
    }
}

@Serializable
data class VehicleDto(
    val id: String,
    val brand: String,
    val model: String,
    val year: Int,
    val trim: String?,
    val color: String?,
    val plates: String?,
    val vin: String?,
    val fuelType: String,
    val tankLiters: Double?,
    val odometer: Long,
    val photoUri: String?,
    val purchaseDateEpochDay: Long?,
    val purchasePriceCents: Long?,
    val notes: String?,
    val currencyCode: String = "MXN",
    val unitSystem: String = "METRIC",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ExpenseDto(
    val id: String,
    val vehicleId: String,
    val type: String,
    val categoryId: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val odometer: Long?,
    val description: String?,
    val customCategoryName: String? = null,
    val establishment: String?,
    val paymentMethod: String?,
    val notes: String?,
    val currencyCode: String = "MXN",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class FuelDetailDto(
    val expenseId: String,
    val litersMilli: Long,
    val pricePerLiterCents: Long,
    val fuelType: String,
    val station: String?,
    val fullTank: Boolean
)

@Serializable
data class MaintenanceDetailDto(
    val expenseId: String,
    val scheduleId: String?,
    val serviceName: String,
    val workshop: String?,
    val nextOdometer: Long?,
    val nextDateEpochDay: Long?
)

@Serializable
data class DocumentDto(
    val id: String,
    val vehicleId: String,
    val type: String,
    val name: String,
    val issueDateEpochDay: Long?,
    val expiryDateEpochDay: Long?,
    val reminderLeadDays: Int,
    val fileUri: String?,
    val notes: String?,
    val createdAt: Long
)

@Serializable
data class AttachmentDto(
    val id: String,
    val ownerType: String,
    val ownerId: String,
    val uri: String,
    val mimeType: String,
    val bytes: Long,
    val createdAt: Long
)

@Serializable
data class MaintenanceScheduleDto(
    val id: String,
    val vehicleId: String,
    val name: String,
    val intervalKm: Long?,
    val intervalMonths: Int?,
    val lastOdometer: Long?,
    val lastDateEpochDay: Long?,
    val remindBeforeKm: Long,
    val remindBeforeDays: Int,
    val active: Boolean
)

@Serializable
data class ReminderDto(
    val id: String,
    val vehicleId: String,
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val body: String,
    val triggerAtEpochMillis: Long,
    val enabled: Boolean,
    val lastNotifiedAt: Long?,
    val templateKey: String? = null,
    val templateArgs: String? = null,
    val lastNotifiedStage: String? = null
)

@Serializable
data class GoalDto(
    val id: String,
    val vehicleId: String,
    val title: String,
    val notes: String?,
    val estimatedCents: Long,
    val estimatedMaxCents: Long?,
    val priority: String,
    val targetDateEpochDay: Long?,
    val status: String,
    val categoryId: String?,
    val savedCents: Long = 0L,
    val createdAt: Long,
    val completedAt: Long?,
    val linkedExpenseId: String?
)