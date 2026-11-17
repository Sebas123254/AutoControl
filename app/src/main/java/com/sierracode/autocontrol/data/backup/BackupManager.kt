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

/**
 * Resumen del resultado de una importación.
 * Se usa para mostrar un mensaje útil al usuario en lugar de un simple "OK / ERROR".
 */
data class ImportSummary(
    val vehicles: Int,
    val expenses: Int,
    val documents: Int,
    val attachments: Int,
    val imagesExtracted: Int,
    val imagesMissing: Int,
    val imagesFailed: Int,
    val hadImagesInZip: Boolean
) {
    val totalRecords: Int get() = vehicles + expenses + documents
}

@Singleton
class BackupManager @Inject constructor(
    private val db: AutoControlDatabase,
    @ApplicationContext private val context: Context
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // =========================================================================
    // EXPORT
    // =========================================================================

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

            ZipOutputStream(outputStream).use { zipOut ->
                // 1. backup.json
                val jsonEntry = ZipEntry("backup.json")
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(jsonString.toByteArray())
                zipOut.closeEntry()

                // 2. Imágenes / PDFs (con streams, sin cargar a memoria)
                context.filesDir.listFiles()?.forEach { file ->
                    if (file.isFile && isAppAttachmentFile(file.name)) {
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

    // =========================================================================
    // IMPORT
    // =========================================================================

    /**
     * Restaura un backup.
     * - Extrae las imágenes ANTES de borrar los archivos existentes (fix del bug
     *   donde se borraban justo después de extraerlas).
     * - Valida zip-slip: cualquier entrada con `..`, `/` inicial o `\` se rechaza.
     * - Transacción atómica: si falla cualquier inserción, no se modifican datos.
     * - Devuelve un resumen con contadores para reportar al usuario.
     */
    suspend fun importFromJson(inputStream: InputStream): Result<ImportSummary> {
        return withContext(Dispatchers.IO) {
            try {
                var jsonString = ""
                val currentFilesDir = context.filesDir
                val canonicalFilesDir = currentFilesDir.canonicalPath
                val extractedFiles = mutableMapOf<String, File>()
                var failedCount = 0

                // ============================================================
                // PASO 1: leer el ZIP. Extraer backup.json + imágenes válidas.
                // NO se borra nada todavía.
                // ============================================================
                ZipInputStream(inputStream).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name

                        when {
                            name == "backup.json" -> {
                                jsonString = zipIn.readBytes().decodeToString()
                            }

                            isSafeAttachmentEntry(name) -> {
                                val targetFile = File(currentFilesDir, name)
                                val canonicalTarget = targetFile.canonicalPath

                                // Validar zip-slip
                                if (!canonicalTarget.startsWith("$canonicalFilesDir${File.separator}")) {
                                    failedCount++
                                    zipIn.closeEntry()
                                    entry = zipIn.nextEntry
                                    continue
                                }

                                try {
                                    FileOutputStream(targetFile).use { output ->
                                        zipIn.copyTo(output)
                                    }
                                    extractedFiles[name] = targetFile
                                } catch (e: Exception) {
                                    failedCount++
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                if (jsonString.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("No se encontró backup.json dentro del ZIP.")
                    )
                }

                val backup = json.decodeFromString(BackupData.serializer(), jsonString)
                if (backup.schemaVersion > BackupData.CURRENT_SCHEMA_VERSION) {
                    return@withContext Result.failure(
                        Exception("El respaldo fue creado con una versión más reciente de AutoControl.")
                    )
                }

                // Reescribe una URI absoluta antigua (file:///data/user/0/.../img_x.jpg)
                // a una URI absoluta nueva que apunte al filesDir del dispositivo actual.
                fun fixUri(uriString: String?): String? {
                    if (uriString.isNullOrBlank()) return null
                    val fileName = uriString.substringAfterLast("/")
                    if (fileName.isBlank()) return null
                    val targetFile = File(currentFilesDir, fileName)
                    return "file://${targetFile.absolutePath}"
                }

                // ============================================================
                // PASO 2: transacción atómica (BD + limpieza de huérfanos).
                // Solo borramos archivos que NO acabamos de extraer.
                // ============================================================
                db.withTransaction {
                    currentFilesDir.listFiles()?.forEach { file ->
                        if (isAppAttachmentFile(file.name) && file.name !in extractedFiles.keys) {
                            file.delete()
                        }
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

                // ============================================================
                // PASO 3: contar imágenes referenciadas que no estaban en el ZIP.
                // ============================================================
                val referencedFiles = (
                        backup.attachments.mapNotNull { it.uri } +
                                backup.documents.mapNotNull { it.fileUri }
                        ).mapNotNull { uri ->
                        uri.substringAfterLast("/").takeIf { it.isNotBlank() }
                    }.toSet()

                val missingCount = referencedFiles.count { it !in extractedFiles.keys }

                val summary = ImportSummary(
                    vehicles = backup.vehicles.size,
                    expenses = backup.expenses.size,
                    documents = backup.documents.size,
                    attachments = backup.attachments.size,
                    imagesExtracted = extractedFiles.size,
                    imagesMissing = missingCount,
                    imagesFailed = failedCount,
                    hadImagesInZip = extractedFiles.isNotEmpty()
                )
                Result.success(summary)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun isAppAttachmentFile(name: String): Boolean =
        name.startsWith("img_") || name.startsWith("doc_")

    /**
     * Valida una entrada del ZIP antes de extraerla.
     * Solo se aceptan archivos con prefijo `img_` o `doc_` en la raíz del zip,
     * sin `..`, sin `/` inicial y sin `\`.
     */
    private fun isSafeAttachmentEntry(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.contains("..")) return false
        if (name.startsWith("/")) return false
        if (name.contains("\\")) return false
        if (name.contains("/")) return false  // solo raíz del zip
        return isAppAttachmentFile(name)
    }
}

// --- Entity → DTO ---
private fun VehicleEntity.toDto() = VehicleDto(
    id, brand, model, year, trim, color, plates, vin, fuelType, tankLiters, odometer,
    photoUri, purchaseDateEpochDay, purchasePriceCents, notes, currencyCode, unitSystem, createdAt, updatedAt
)
private fun ExpenseEntity.toDto() = ExpenseDto(
    id, vehicleId, type, categoryId, amountCents, dateEpochDay, odometer,
    description, customCategoryName, establishment, paymentMethod, notes, createdAt, updatedAt
)
private fun FuelDetailEntity.toDto() = FuelDetailDto(expenseId, litersMilli, pricePerLiterCents, fuelType, station, fullTank)
private fun MaintenanceDetailEntity.toDto() = MaintenanceDetailDto(expenseId, scheduleId, serviceName, workshop, nextOdometer, nextDateEpochDay)
private fun DocumentEntity.toDto() = DocumentDto(id, vehicleId, type, name, issueDateEpochDay, expiryDateEpochDay, reminderLeadDays, fileUri, notes, createdAt)
private fun AttachmentEntity.toDto() = AttachmentDto(id, ownerType, ownerId, uri, mimeType, bytes, createdAt)
private fun MaintenanceScheduleEntity.toDto() = MaintenanceScheduleDto(id, vehicleId, name, intervalKm, intervalMonths, lastOdometer, lastDateEpochDay, remindBeforeKm, remindBeforeDays, active)
private fun ReminderEntity.toDto() = ReminderDto(
    id, vehicleId, sourceType, sourceId, title, body,
    triggerAtEpochMillis, enabled, lastNotifiedAt,
    templateKey, templateArgs, lastNotifiedStage
)
private fun GoalEntity.toDto() = GoalDto(id, vehicleId, title, notes, estimatedCents, estimatedMaxCents, priority, targetDateEpochDay, status, categoryId, createdAt, completedAt, linkedExpenseId)

// --- DTO → Entity ---
private fun VehicleDto.toEntity() = VehicleEntity(
    id, brand, model, year, trim, color, plates, vin, fuelType, tankLiters, odometer,
    photoUri, purchaseDateEpochDay, purchasePriceCents, notes, currencyCode, unitSystem, createdAt, updatedAt
)
private fun ExpenseDto.toEntity() = ExpenseEntity(
    id, vehicleId, type, categoryId, amountCents, dateEpochDay, odometer,
    description, customCategoryName, establishment, paymentMethod, notes, createdAt, updatedAt
)
private fun FuelDetailDto.toEntity() = FuelDetailEntity(expenseId, litersMilli, pricePerLiterCents, fuelType, station, fullTank)
private fun MaintenanceDetailDto.toEntity() = MaintenanceDetailEntity(expenseId, scheduleId, serviceName, workshop, nextOdometer, nextDateEpochDay)
private fun DocumentDto.toEntity() = DocumentEntity(id, vehicleId, type, name, issueDateEpochDay, expiryDateEpochDay, reminderLeadDays, fileUri, notes, createdAt)
private fun AttachmentDto.toEntity() = AttachmentEntity(id, ownerType, ownerId, uri, mimeType, bytes, createdAt)
private fun MaintenanceScheduleDto.toEntity() = MaintenanceScheduleEntity(id, vehicleId, name, intervalKm, intervalMonths, lastOdometer, lastDateEpochDay, remindBeforeKm, remindBeforeDays, active)
private fun ReminderDto.toEntity() = ReminderEntity(
    id, vehicleId, sourceType, sourceId, title, body,
    triggerAtEpochMillis, enabled, lastNotifiedAt,
    templateKey, templateArgs, lastNotifiedStage
)
private fun GoalDto.toEntity() = GoalEntity(id, vehicleId, title, notes, estimatedCents, estimatedMaxCents, priority, targetDateEpochDay, status, categoryId, createdAt, completedAt, linkedExpenseId)