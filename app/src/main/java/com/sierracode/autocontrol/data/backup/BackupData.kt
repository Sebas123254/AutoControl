package com.sierracode.autocontrol.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val schemaVersion: Int = 1,
    val appVersion: String = "1.0.0",
    val exportedAt: Long,
    val vehicles: List<VehicleDto>,
    val expenses: List<ExpenseDto>,
    val fuelDetails: List<FuelDetailDto>,
    val maintenanceDetails: List<MaintenanceDetailDto>,
    val documents: List<DocumentDto>
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