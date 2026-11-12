@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.sierracode.autocontrol.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val schemaVersion: Int = 3,
    val appVersion: String = "1.0.0",
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
)

@Serializable
data class VehicleDto(
    val id: String, val brand: String, val model: String, val year: Int,
    val trim: String?, val color: String?, val plates: String?, val vin: String?,
    val fuelType: String, val tankLiters: Double?, val odometer: Long,
    val photoUri: String?, val purchaseDateEpochDay: Long?, val purchasePriceCents: Long?,
    val notes: String?, val createdAt: Long, val updatedAt: Long
)

@Serializable
data class ExpenseDto(
    val id: String, val vehicleId: String, val type: String, val categoryId: String,
    val amountCents: Long, val dateEpochDay: Long, val odometer: Long?,
    val description: String?, val establishment: String?, val paymentMethod: String?,
    val notes: String?, val createdAt: Long, val updatedAt: Long
)

@Serializable
data class FuelDetailDto(
    val expenseId: String, val litersMilli: Long, val pricePerLiterCents: Long,
    val fuelType: String, val station: String?, val fullTank: Boolean
)

@Serializable
data class MaintenanceDetailDto(
    val expenseId: String, val scheduleId: String?, val serviceName: String,
    val workshop: String?, val nextOdometer: Long?, val nextDateEpochDay: Long?
)

@Serializable
data class DocumentDto(
    val id: String, val vehicleId: String, val type: String, val name: String,
    val issueDateEpochDay: Long?, val expiryDateEpochDay: Long?, val reminderLeadDays: Int,
    val fileUri: String?, val notes: String?, val createdAt: Long
)

@Serializable
data class AttachmentDto(
    val id: String, val ownerType: String, val ownerId: String, val uri: String,
    val mimeType: String, val bytes: Long, val createdAt: Long
)

@Serializable
data class MaintenanceScheduleDto(
    val id: String, val vehicleId: String, val name: String,
    val intervalKm: Long?, val intervalMonths: Int?, val lastOdometer: Long?,
    val lastDateEpochDay: Long?, val remindBeforeKm: Long, val remindBeforeDays: Int,
    val active: Boolean
)

@Serializable
data class ReminderDto(
    val id: String, val vehicleId: String, val sourceType: String, val sourceId: String,
    val title: String, val body: String, val triggerAtEpochMillis: Long,
    val enabled: Boolean, val lastNotifiedAt: Long?
)

@Serializable
data class GoalDto(
    val id: String, val vehicleId: String, val title: String, val notes: String?,
    val estimatedCents: Long, val estimatedMaxCents: Long?, val priority: String,
    val targetDateEpochDay: Long?, val status: String, val categoryId: String?,
    val createdAt: Long, val completedAt: Long?, val linkedExpenseId: String?
)