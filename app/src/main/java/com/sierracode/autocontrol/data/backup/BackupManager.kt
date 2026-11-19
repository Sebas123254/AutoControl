package com.sierracode.autocontrol.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.sierracode.autocontrol.BuildConfig
import com.sierracode.autocontrol.core.util.FileUtils
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.entity.*
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.domain.reminder.ReminderPlanner
import com.sierracode.autocontrol.worker.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupStats(val records: Int, val images: Int)

private const val TAG = "BackupManager"
private const val MAX_UNCOMPRESSED_BYTES = 1L * 1024 * 1024 * 1024 // 1 GB limit

@Singleton
class BackupManager @Inject constructor(
    private val db: AutoControlDatabase,
    private val settingsStore: SettingsStore,
    private val reminderPlanner: ReminderPlanner,
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // =========================================================================
    // EXPORTACIÓN
    // =========================================================================
    suspend fun exportToZip(uri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}.zip")
        try {
            // 1. Snapshot consistente de todas las tablas dentro de una sola transacción
            val (backup, actualFiles) = db.withTransaction {
                val vehicles = db.vehicleDao().getAllOnce()
                val expenses = db.expenseDao().getAllOnce()
                val fuelDetails = db.fuelDao().getAllOnce()
                val maintenanceDetails = db.maintenanceDao().getAllOnce()
                val documents = db.documentDao().getAllOnce()
                val attachments = db.attachmentDao().getAllOnce()
                val maintenanceSchedules = db.scheduleDao().getAllOnce()
                val reminders = db.reminderDao().getAllOnce()
                val goals = db.goalDao().getAllOnce()

                // Recolectar archivos referenciados
                val referencedNames = mutableSetOf<String>()
                attachments.forEach { if (it.uri.isNotBlank()) referencedNames.add(it.uri.substringAfterLast("/")) }
                documents.forEach { doc -> doc.fileUri?.let { if (it.isNotBlank()) referencedNames.add(it.substringAfterLast("/")) } }
                vehicles.forEach { v -> v.photoUri?.let { if (it.isNotBlank()) referencedNames.add(it.substringAfterLast("/")) } }

                val filesOnDisk = referencedNames.mapNotNull { FileUtils.getFile(context, it) }
                    .filter { it.exists() && it.isFile }
                    .distinctBy { it.name }

                val backupData = BackupData(
                    schemaVersion = BackupData.CURRENT_SCHEMA_VERSION,
                    appVersion = BuildConfig.VERSION_NAME,
                    filesCount = filesOnDisk.size,
                    exportedAt = System.currentTimeMillis(),
                    vehicles = vehicles.map { it.toDto() },
                    expenses = expenses.map { it.toDto() },
                    fuelDetails = fuelDetails.map { it.toDto() },
                    maintenanceDetails = maintenanceDetails.map { it.toDto() },
                    documents = documents.map { it.toDto() },
                    attachments = attachments.map { it.toDto() },
                    maintenanceSchedules = maintenanceSchedules.map { it.toDto() },
                    reminders = reminders.map { it.toDto() },
                    goals = goals.map { it.toDto() }
                )
                Pair(backupData, filesOnDisk)
            }

            val jsonString = json.encodeToString(BackupData.serializer(), backup)

            // 2. Escribir a un archivo temporal primero
            FileOutputStream(tempZip).use { fos ->
                ZipOutputStream(fos).use { zipOut ->
                    // Entrada backup.json
                    zipOut.putNextEntry(ZipEntry("backup.json"))
                    zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    // Entradas de archivos deduplicados
                    actualFiles.forEach { file ->
                        zipOut.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            }

            // 3. Copiar al destino final vía ContentResolver con "wt"
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw BackupError.CannotWrite()

            outputStream.use { os ->
                tempZip.inputStream().use { isStream -> isStream.copyTo(os) }
            }

            val totalRecords = backup.vehicles.size + backup.expenses.size + backup.documents.size
            Result.success(BackupStats(totalRecords, actualFiles.size))
        } catch (ce: CancellationException) {
            throw ce
        } catch (be: BackupError) {
            Log.e(TAG, "Error durante la exportación de respaldo: ${be.message}", be)
            Result.failure(be)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada al exportar respaldo", e)
            Result.failure(e)
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
            }
        }
    }

    // =========================================================================
    // RESTAURACIÓN
    // =========================================================================
    suspend fun importFromZip(uri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            // 1. Extraer a directorio temporal con validación Zip-Slip y límite de 1 GB
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw BackupError.CannotRead()

            inputStream.use { isStream ->
                extractZipSafely(isStream, tempDir)
            }

            // 2. Leer directamente backup.json desde el temporal
            val backupJsonFile = File(tempDir, "backup.json")
            if (!backupJsonFile.exists() || !backupJsonFile.isFile) {
                throw BackupError.MissingJson()
            }
            val jsonString = backupJsonFile.readText(Charsets.UTF_8)
            if (jsonString.isBlank()) {
                throw BackupError.MissingJson()
            }

            val backup = try {
                json.decodeFromString(BackupData.serializer(), jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Error al deserializar backup.json", e)
                throw e
            }

            // 3. Validar versión del esquema
            if (backup.schemaVersion > BackupData.CURRENT_SCHEMA_VERSION) {
                throw BackupError.NewerVersion(
                    found = backup.schemaVersion,
                    supported = BackupData.CURRENT_SCHEMA_VERSION
                )
            }

            // 4. Copiar archivos del temporal al destino final (filesDir/attachments/)
            val attachmentsDir = FileUtils.getAttachmentsDir(context)
            val extractedFiles = tempDir.listFiles()?.filter { it.isFile && it.name != "backup.json" } ?: emptyList()

            extractedFiles.forEach { file ->
                val dest = File(attachmentsDir, file.name)
                file.copyTo(dest, overwrite = true)
            }

            // 5. Transacción de Room: limpiar e insertar todo atómicamente
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

            // 6. Validar vehículo activo en SettingsStore
            val activeId = settingsStore.activeVehicleId.first()
            if (activeId != null && db.vehicleDao().get(activeId) == null) {
                settingsStore.setActiveVehicle(null)
            }

            // 7. Reprogramar recordatorios en WorkManager / ReminderPlanner
            try {
                // Re-planificar todos los documentos y mantenimientos para sincronizar triggers
                val docs = db.documentDao().getAllOnce()
                docs.forEach { reminderPlanner.planDocument(it) }

                val schedules = db.scheduleDao().getAllOnce().filter { it.active }
                schedules.forEach { reminderPlanner.planMaintenance(it, it.name) }

                // Asegurar que el worker periódico diario de WorkManager esté activo
                ReminderScheduler.schedule(context)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron sincronizar todos los recordatorios tras restaurar", e)
            }

            val totalRecords = backup.vehicles.size + backup.expenses.size + backup.documents.size
            Result.success(BackupStats(totalRecords, extractedFiles.size))
        } catch (ce: CancellationException) {
            throw ce
        } catch (be: BackupError) {
            Log.e(TAG, "Error durante la restauración de respaldo: ${be.message}", be)
            Result.failure(be)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada al restaurar respaldo", e)
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun extractZipSafely(
        inputStream: InputStream,
        targetDir: File
    ) {
        val canonicalTarget = targetDir.canonicalPath
        var totalBytesRead = 0L

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                val name = entry.name
                val targetFile = File(targetDir, name)
                val canonicalFile = targetFile.canonicalPath

                // Protección Zip-Slip: debe ser igual a canonicalTarget o comenzar con target + separator
                if (canonicalFile != canonicalTarget && !canonicalFile.startsWith(canonicalTarget + File.separator)) {
                    throw SecurityException("Intento de Zip-Slip detectado en: $name")
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            totalBytesRead += len
                            if (totalBytesRead > MAX_UNCOMPRESSED_BYTES) {
                                throw SecurityException("El archivo descomprimido excede el límite de 1 GB")
                            }
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

// =========================================================================
// MAPPERS CON ARGUMENTOS CON NOMBRE
// =========================================================================

private fun VehicleEntity.toDto() = VehicleDto(
    id = id,
    brand = brand,
    model = model,
    year = year,
    trim = trim,
    color = color,
    plates = plates,
    vin = vin,
    fuelType = fuelType,
    tankLiters = tankLiters,
    odometer = odometer,
    photoUri = photoUri,
    purchaseDateEpochDay = purchaseDateEpochDay,
    purchasePriceCents = purchasePriceCents,
    notes = notes,
    currencyCode = currencyCode,
    unitSystem = unitSystem,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ExpenseEntity.toDto() = ExpenseDto(
    id = id,
    vehicleId = vehicleId,
    type = type,
    categoryId = categoryId,
    amountCents = amountCents,
    dateEpochDay = dateEpochDay,
    odometer = odometer,
    description = description,
    customCategoryName = customCategoryName,
    establishment = establishment,
    paymentMethod = paymentMethod,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun FuelDetailEntity.toDto() = FuelDetailDto(
    expenseId = expenseId,
    litersMilli = litersMilli,
    pricePerLiterCents = pricePerLiterCents,
    fuelType = fuelType,
    station = station,
    fullTank = fullTank
)

private fun MaintenanceDetailEntity.toDto() = MaintenanceDetailDto(
    expenseId = expenseId,
    scheduleId = scheduleId,
    serviceName = serviceName,
    workshop = workshop,
    nextOdometer = nextOdometer,
    nextDateEpochDay = nextDateEpochDay
)

private fun DocumentEntity.toDto() = DocumentDto(
    id = id,
    vehicleId = vehicleId,
    type = type,
    name = name,
    issueDateEpochDay = issueDateEpochDay,
    expiryDateEpochDay = expiryDateEpochDay,
    reminderLeadDays = reminderLeadDays,
    fileUri = fileUri,
    notes = notes,
    createdAt = createdAt
)

private fun AttachmentEntity.toDto() = AttachmentDto(
    id = id,
    ownerType = ownerType,
    ownerId = ownerId,
    uri = uri,
    mimeType = mimeType,
    bytes = bytes,
    createdAt = createdAt
)

private fun MaintenanceScheduleEntity.toDto() = MaintenanceScheduleDto(
    id = id,
    vehicleId = vehicleId,
    name = name,
    intervalKm = intervalKm,
    intervalMonths = intervalMonths,
    lastOdometer = lastOdometer,
    lastDateEpochDay = lastDateEpochDay,
    remindBeforeKm = remindBeforeKm,
    remindBeforeDays = remindBeforeDays,
    active = active
)

private fun ReminderEntity.toDto() = ReminderDto(
    id = id,
    vehicleId = vehicleId,
    sourceType = sourceType,
    sourceId = sourceId,
    title = title,
    body = body,
    triggerAtEpochMillis = triggerAtEpochMillis,
    enabled = enabled,
    lastNotifiedAt = lastNotifiedAt,
    templateKey = templateKey,
    templateArgs = templateArgs,
    lastNotifiedStage = lastNotifiedStage
)

private fun GoalEntity.toDto() = GoalDto(
    id = id,
    vehicleId = vehicleId,
    title = title,
    notes = notes,
    estimatedCents = estimatedCents,
    estimatedMaxCents = estimatedMaxCents,
    priority = priority,
    targetDateEpochDay = targetDateEpochDay,
    status = status,
    categoryId = categoryId,
    createdAt = createdAt,
    completedAt = completedAt,
    linkedExpenseId = linkedExpenseId
)

private fun VehicleDto.toEntity() = VehicleEntity(
    id = id,
    brand = brand,
    model = model,
    year = year,
    trim = trim,
    color = color,
    plates = plates,
    vin = vin,
    fuelType = fuelType,
    tankLiters = tankLiters,
    odometer = odometer,
    photoUri = photoUri,
    purchaseDateEpochDay = purchaseDateEpochDay,
    purchasePriceCents = purchasePriceCents,
    notes = notes,
    currencyCode = currencyCode,
    unitSystem = unitSystem,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ExpenseDto.toEntity() = ExpenseEntity(
    id = id,
    vehicleId = vehicleId,
    type = type,
    categoryId = categoryId,
    amountCents = amountCents,
    dateEpochDay = dateEpochDay,
    odometer = odometer,
    description = description,
    customCategoryName = customCategoryName,
    establishment = establishment,
    paymentMethod = paymentMethod,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun FuelDetailDto.toEntity() = FuelDetailEntity(
    expenseId = expenseId,
    litersMilli = litersMilli,
    pricePerLiterCents = pricePerLiterCents,
    fuelType = fuelType,
    station = station,
    fullTank = fullTank
)

private fun MaintenanceDetailDto.toEntity() = MaintenanceDetailEntity(
    expenseId = expenseId,
    scheduleId = scheduleId,
    serviceName = serviceName,
    workshop = workshop,
    nextOdometer = nextOdometer,
    nextDateEpochDay = nextDateEpochDay
)

private fun DocumentDto.toEntity() = DocumentEntity(
    id = id,
    vehicleId = vehicleId,
    type = type,
    name = name,
    issueDateEpochDay = issueDateEpochDay,
    expiryDateEpochDay = expiryDateEpochDay,
    reminderLeadDays = reminderLeadDays,
    fileUri = fileUri,
    notes = notes,
    createdAt = createdAt
)

private fun AttachmentDto.toEntity() = AttachmentEntity(
    id = id,
    ownerType = ownerType,
    ownerId = ownerId,
    uri = uri,
    mimeType = mimeType,
    bytes = bytes,
    createdAt = createdAt
)

private fun MaintenanceScheduleDto.toEntity() = MaintenanceScheduleEntity(
    id = id,
    vehicleId = vehicleId,
    name = name,
    intervalKm = intervalKm,
    intervalMonths = intervalMonths,
    lastOdometer = lastOdometer,
    lastDateEpochDay = lastDateEpochDay,
    remindBeforeKm = remindBeforeKm,
    remindBeforeDays = remindBeforeDays,
    active = active
)

private fun ReminderDto.toEntity() = ReminderEntity(
    id = id,
    vehicleId = vehicleId,
    sourceType = sourceType,
    sourceId = sourceId,
    title = title,
    body = body,
    triggerAtEpochMillis = triggerAtEpochMillis,
    enabled = enabled,
    lastNotifiedAt = lastNotifiedAt,
    templateKey = templateKey,
    templateArgs = templateArgs,
    lastNotifiedStage = lastNotifiedStage
)

private fun GoalDto.toEntity() = GoalEntity(
    id = id,
    vehicleId = vehicleId,
    title = title,
    notes = notes,
    estimatedCents = estimatedCents,
    estimatedMaxCents = estimatedMaxCents,
    priority = priority,
    targetDateEpochDay = targetDateEpochDay,
    status = status,
    categoryId = categoryId,
    createdAt = createdAt,
    completedAt = completedAt,
    linkedExpenseId = linkedExpenseId
)