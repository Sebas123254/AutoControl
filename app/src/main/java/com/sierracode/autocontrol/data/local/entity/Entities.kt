package com.sierracode.autocontrol.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import androidx.room.ColumnInfo

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val brand: String, val model: String, val year: Int,
    val trim: String? = null, val color: String? = null,
    val plates: String? = null, val vin: String? = null,
    val fuelType: String = "GASOLINA",
    val tankLiters: Double? = null,
    val odometer: Long = 0,
    val photoUri: String? = null,
    val purchaseDateEpochDay: Long? = null,
    val purchasePriceCents: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["group"], unique = false)]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val group: String,        // FUEL, MAINT, REPAIR, OTHER, MOD
    val name: String,
    val iconKey: String,
    val isSystem: Boolean = true,
    val active: Boolean = true
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("vehicleId"), Index("dateEpochDay"), Index("categoryId")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val type: String,             // FUEL, MAINTENANCE, REPAIR, OTHER, MOD, DOCUMENT
    val categoryId: String,
    val amountCents: Long,
    val dateEpochDay: Long,
    val odometer: Long? = null,
    val description: String? = null,
    val establishment: String? = null,
    val paymentMethod: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "fuel_details",
    foreignKeys = [ForeignKey(ExpenseEntity::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["expenseId"], unique = true)]
)
data class FuelDetailEntity(
    @PrimaryKey val expenseId: String,
    val litersMilli: Long,        // litros × 1000
    val pricePerLiterCents: Long,
    val fuelType: String,
    val station: String? = null,
    val fullTank: Boolean = false
)

@Entity(
    tableName = "maintenance_details",
    foreignKeys = [
        ForeignKey(ExpenseEntity::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MaintenanceScheduleEntity::class, ["id"], ["scheduleId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["expenseId"], unique = true), Index("scheduleId")]
)
data class MaintenanceDetailEntity(
    @PrimaryKey val expenseId: String,
    val scheduleId: String? = null,
    val serviceName: String,
    val workshop: String? = null,
    val nextOdometer: Long? = null,
    val nextDateEpochDay: Long? = null
)

@Entity(
    tableName = "maintenance_schedules",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId")]
)
data class MaintenanceScheduleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val name: String,
    val intervalKm: Long? = null,
    val intervalMonths: Int? = null,
    val lastOdometer: Long? = null,
    val lastDateEpochDay: Long? = null,
    val remindBeforeKm: Long = 500,
    val remindBeforeDays: Int = 15,
    val active: Boolean = true
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("expiryDateEpochDay")]
)
data class DocumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val type: String,
    val name: String,
    val issueDateEpochDay: Long? = null,
    val expiryDateEpochDay: Long? = null, // <-- CAMBIO 1: Lo volvemos opcional (Long?)
    @ColumnInfo(defaultValue = "30") val reminderLeadDays: Int = 30, // <-- CAMBIO 2: Agregamos el valor por defecto
    val fileUri: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// CAJÓN 9: Fotos/archivos adjuntos (se queda igual)
@Entity(tableName = "attachments", indices = [Index(value = ["ownerType", "ownerId"])])
data class AttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ownerType: String,
    val ownerId: String,
    val uri: String,
    val mimeType: String,
    val bytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)

// CAJÓN 10: Recordatorios (LA NUEVA, BORRA LA VIEJA SI ESTÁ AHÍ)
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(VehicleEntity::class, ["id"], ["vehicleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("vehicleId"), Index("triggerAtEpochMillis")]
)
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val sourceType: String, // "DOCUMENT" o "MAINTENANCE"
    val sourceId: String,   // El ID del documento o de la plantilla
    val title: String,
    val body: String,
    val triggerAtEpochMillis: Long,
    val enabled: Boolean = true,
    val lastNotifiedAt: Long? = null
)