@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.sierracode.autocontrol.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val db: AutoControlDatabase,
    @ApplicationContext private val context: Context
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

            // Crear el archivo ZIP
            ZipOutputStream(outputStream).use { zipOut ->
                // 1. Escribir el JSON
                val jsonEntry = ZipEntry("backup.json")
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(jsonString.toByteArray())
                zipOut.closeEntry()

                // 2. Escribir los archivos (imágenes y PDFs)
                val files = context.filesDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("img_") || file.name.startsWith("doc_"))) {
                        val fileEntry = ZipEntry(file.name)
                        zipOut.putNextEntry(fileEntry)
                        file.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }
            outputStream.flush()
        }
    }

    suspend fun importFromJson(inputStream: InputStream): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                var jsonString = ""
                val currentFilesDir = context.filesDir.absolutePath

                // 1. Leer el ZIP y extraer archivos
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "backup.json") {
                            jsonString = zipIn.readBytes().decodeToString()
                        } else if (entry.name.startsWith("img_") || entry.name.startsWith("doc_")) {
                            val file = File(context.filesDir, entry.name)
                            FileOutputStream(file).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                if (jsonString.isEmpty()) {
                    return@withContext Result.failure(Exception("No se encontró el archivo backup.json dentro del ZIP."))
                }

                val backup = json.decodeFromString(BackupData.serializer(), jsonString)

                if (backup.schemaVersion > BackupData.CURRENT_SCHEMA_VERSION) {
                    return@withContext Result.failure(Exception("Versión de respaldo no soportada."))
                }

                // Función auxiliar para reparar las rutas de archivos en el nuevo dispositivo
                fun fixUri(uriString: String?): String? {
                    if (uriString.isNullOrBlank()) return null
                    val fileName = uriString.substringAfterLast("/")
                    return "file://$currentFilesDir/$fileName"
                }

                db.withTransaction {
                    // Borrar datos anteriores (y archivos físicos huérfanos)
                    context.filesDir.listFiles()?.forEach {
                        if (it.name.startsWith("img_") || it.name.startsWith("doc_")) it.delete()
                    }

                    db.goalDao().deleteAll()
                    db.reminderDao().deleteAll()
                    db.attachmentDao().deleteAll()
                    db.scheduleDao().deleteAll()
                    db.documentDao().deleteAll()
                    db.maintenanceDao().deleteAll()
                    db.fuelDao().deleteAll()
                    db.expenseDao().deleteAll()
                    db.vehicleDao().deleteAll()

                    // Restaurar datos con URIs corregidas
                    backup.vehicles.forEach { db.vehicleDao().upsert(it.toEntity()) }
                    backup.expenses.forEach { db.expenseDao().upsert(it.toEntity()) }
                    backup.fuelDetails.forEach { db.fuelDao().upsert(it.toEntity()) }
                    backup.maintenanceSchedules.forEach { db.scheduleDao().upsert(it.toEntity()) }
                    backup.maintenanceDetails.forEach { db.maintenanceDao().upsert(it.toEntity()) }

                    backup.documents.forEach { dto ->
                        db.documentDao().upsert(dto.toEntity().copy(fileUri = fixUri(dto.fileUri)))
                    }
                    backup.attachments.forEach { dto ->
                        db.attachmentDao().upsert(dto.toEntity().copy(uri = fixUri(dto.uri) ?: dto.uri))
                    }

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