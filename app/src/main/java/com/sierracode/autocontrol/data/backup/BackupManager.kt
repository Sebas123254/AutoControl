@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.sierracode.autocontrol.data.backup

import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val db: AutoControlDatabase
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportToJson(outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val backup = BackupData(
                exportedAt = System.currentTimeMillis(),
                vehicles = db.vehicleDao().getAllOnce().map { it.toDto() },
                expenses = db.expenseDao().getAllOnce().map { it.toDto() },
                fuelDetails = db.fuelDao().getAllOnce().map { it.toDto() },
                maintenanceDetails = db.maintenanceDao().getAllOnce().map { it.toDto() },
                documents = db.documentDao().getAllOnce().map { it.toDto() },
                attachments = db.attachmentDao().getAllOnce().map { it.toDto() },
                maintenanceSchedules = db.scheduleDao().getAllOnce().map { it.toDto() },
                reminders = db.reminderDao().getAllOnce().map { it.toDto() },
                goals = db.goalDao().getAllOnce().map { it.toDto() }
            )
            val jsonString = json.encodeToString(BackupData.serializer(), backup)
            outputStream.write(jsonString.toByteArray())
            outputStream.flush()
        }
    }

    suspend fun importFromJson(inputStream: InputStream): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val backup = json.decodeFromString(BackupData.serializer(), jsonString)

                if (backup.schemaVersion > BackupData.CURRENT_SCHEMA_VERSION) {
                    return@withContext Result.failure(Exception("Versión de respaldo no soportada."))
                }

                db.withTransaction {
                    db.goalDao().deleteAll()
                    db.reminderDao().deleteAll()
                    db.attachmentDao().deleteAll()
                    db.scheduleDao().deleteAll()
                    db.documentDao().deleteAll()
                    db.maintenanceDao().deleteAll()
                    db.fuelDao().deleteAll()
                    db.expenseDao().deleteAll()
                    db.vehicleDao().deleteAll()

                    backup.vehicles.forEach { db.vehicleDao().upsert(it.toEntity()) }
                    backup.expenses.forEach { db.expenseDao().upsert(it.toEntity()) }
                    backup.fuelDetails.forEach { db.fuelDao().upsert(it.toEntity()) }
                    backup.maintenanceSchedules.forEach { db.scheduleDao().upsert(it.toEntity()) }
                    backup.maintenanceDetails.forEach { db.maintenanceDao().upsert(it.toEntity()) }
                    backup.documents.forEach { db.documentDao().upsert(it.toEntity()) }
                    backup.attachments.forEach { db.attachmentDao().upsert(it.toEntity()) }
                    backup.reminders.forEach { db.reminderDao().upsert(it.toEntity()) }
                    backup.goals.forEach { db.goalDao().upsert(it.toEntity()) }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

// --- Conversiones Entity -> DTO ---
private fun VehicleEntity.toDto() = VehicleDto(
    id, brand, model, year, trim, color, plates, vin, fuelType, tankLiters, odometer,
    photoUri, purchaseDateEpochDay, purchasePriceCents, notes, currencyCode, unitSystem, createdAt, updatedAt
)
private fun ExpenseEntity.toDto() = ExpenseDto(id, vehicleId, type, categoryId, amountCents, dateEpochDay, odometer, description, customCategoryName, establishment, paymentMethod, notes, createdAt, updatedAt)
private fun FuelDetailEntity.toDto() = FuelDetailDto(expenseId, litersMilli, pricePerLiterCents, fuelType, station, fullTank)
private fun MaintenanceDetailEntity.toDto() = MaintenanceDetailDto(expenseId, scheduleId, serviceName, workshop, nextOdometer, nextDateEpochDay)
private fun DocumentEntity.toDto() = DocumentDto(id, vehicleId, type, name, issueDateEpochDay, expiryDateEpochDay, reminderLeadDays, fileUri, notes, createdAt)
private fun AttachmentEntity.toDto() = AttachmentDto(id, ownerType, ownerId, uri, mimeType, bytes, createdAt)
private fun MaintenanceScheduleEntity.toDto() = MaintenanceScheduleDto(id, vehicleId, name, intervalKm, intervalMonths, lastOdometer, lastDateEpochDay, remindBeforeKm, remindBeforeDays, active)
private fun ReminderEntity.toDto() = ReminderDto(id, vehicleId, sourceType, sourceId, title, body, triggerAtEpochMillis, enabled, lastNotifiedAt)
private fun GoalEntity.toDto() = GoalDto(id, vehicleId, title, notes, estimatedCents, estimatedMaxCents, priority, targetDateEpochDay, status, categoryId, createdAt, completedAt, linkedExpenseId)

// --- Conversiones DTO -> Entity ---
private fun VehicleDto.toEntity() = VehicleEntity(
    id, brand, model, year, trim, color, plates, vin, fuelType, tankLiters, odometer,
    photoUri, purchaseDateEpochDay, purchasePriceCents, notes, currencyCode, unitSystem, createdAt, updatedAt
)
private fun ExpenseDto.toEntity() = ExpenseEntity(id, vehicleId, type, categoryId, amountCents, dateEpochDay, odometer, description, customCategoryName, establishment, paymentMethod, notes, createdAt, updatedAt)
private fun FuelDetailDto.toEntity() = FuelDetailEntity(expenseId, litersMilli, pricePerLiterCents, fuelType, station, fullTank)
private fun MaintenanceDetailDto.toEntity() = MaintenanceDetailEntity(expenseId, scheduleId, serviceName, workshop, nextOdometer, nextDateEpochDay)
private fun DocumentDto.toEntity() = DocumentEntity(id, vehicleId, type, name, issueDateEpochDay, expiryDateEpochDay, reminderLeadDays, fileUri, notes, createdAt)
private fun AttachmentDto.toEntity() = AttachmentEntity(id, ownerType, ownerId, uri, mimeType, bytes, createdAt)
private fun MaintenanceScheduleDto.toEntity() = MaintenanceScheduleEntity(id, vehicleId, name, intervalKm, intervalMonths, lastOdometer, lastDateEpochDay, remindBeforeKm, remindBeforeDays, active)
private fun ReminderDto.toEntity() = ReminderEntity(id, vehicleId, sourceType, sourceId, title, body, triggerAtEpochMillis, enabled, lastNotifiedAt)
private fun GoalDto.toEntity() = GoalEntity(id, vehicleId, title, notes, estimatedCents, estimatedMaxCents, priority, targetDateEpochDay, status, categoryId, createdAt, completedAt, linkedExpenseId)