package com.sierracode.autocontrol.data.backup

import androidx.room.withTransaction // <-- ESTE ERA EL IMPORT QUE FALTABA
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

    // --- EXPORTAR ---
    suspend fun exportToJson(outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val backup = BackupData(
                exportedAt = System.currentTimeMillis(),
                vehicles = db.vehicleDao().getAllOnce().map { it.toDto() },
                expenses = db.expenseDao().getAllOnce().map { it.toDto() },
                fuelDetails = db.fuelDao().getAllOnce().map { it.toDto() },
                maintenanceDetails = db.maintenanceDao().getAllOnce().map { it.toDto() },
                documents = db.documentDao().getAllOnce().map { it.toDto() }
            )
            val jsonString = json.encodeToString(BackupData.serializer(), backup)
            outputStream.write(jsonString.toByteArray())
            outputStream.flush()
        }
    }

    // --- IMPORTAR ---
    suspend fun importFromJson(inputStream: InputStream): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val backup = json.decodeFromString(BackupData.serializer(), jsonString)

                if (backup.schemaVersion > 1) {
                    return@withContext Result.failure(Exception("Versión de respaldo no soportada."))
                }

                // Hacemos todo en una transacción para evitar datos a medias
                db.withTransaction {
                    // 1. Borrar todo (en orden inverso a las llaves foráneas)
                    db.reminderDao().deleteAll()
                    db.documentDao().deleteAll()
                    db.maintenanceDao().deleteAll()
                    db.fuelDao().deleteAll()
                    db.expenseDao().deleteAll()
                    db.vehicleDao().deleteAll()

                    // 2. Insertar de nuevo (en orden de dependencias)
                    backup.vehicles.forEach { db.vehicleDao().upsert(it.toEntity()) }
                    backup.expenses.forEach { db.expenseDao().upsert(it.toEntity()) }
                    backup.fuelDetails.forEach { db.fuelDao().upsert(it.toEntity()) }
                    backup.maintenanceDetails.forEach { db.maintenanceDao().upsert(it.toEntity()) }
                    backup.documents.forEach { db.documentDao().upsert(it.toEntity()) }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

// --- Conversiones Entity -> DTO ---
private fun VehicleEntity.toDto() = VehicleDto(id, brand, model, year, trim, color, plates, vin, fuelType, tankLiters, odometer, photoUri, purchaseDateEpochDay, purchasePriceCents, notes, createdAt, updatedAt)
private fun ExpenseEntity.toDto() = ExpenseDto(id, vehicleId, type, categoryId, amountCents, dateEpochDay, odometer, description, establishment, paymentMethod, notes, createdAt, updatedAt)
private fun FuelDetailEntity.toDto() = FuelDetailDto(expenseId, litersMilli, pricePerLiterCents, fuelType, station, fullTank)
private fun MaintenanceDetailEntity.toDto() = MaintenanceDetailDto(expenseId, scheduleId, serviceName, workshop, nextOdometer, nextDateEpochDay)
private fun DocumentEntity.toDto() = DocumentDto(id, vehicleId, type, name, issueDateEpochDay, expiryDateEpochDay, reminderLeadDays, fileUri, notes, createdAt)

// --- Conversiones DTO -> Entity ---
private fun VehicleDto.toEntity() = VehicleEntity(id, brand, model, year, trim, color, plates, vin, fuelType, tankLiters, odometer, photoUri, purchaseDateEpochDay, purchasePriceCents, notes, createdAt, updatedAt)
private fun ExpenseDto.toEntity() = ExpenseEntity(id, vehicleId, type, categoryId, amountCents, dateEpochDay, odometer, description, establishment, paymentMethod, notes, createdAt, updatedAt)
private fun FuelDetailDto.toEntity() = FuelDetailEntity(expenseId, litersMilli, pricePerLiterCents, fuelType, station, fullTank)
private fun MaintenanceDetailDto.toEntity() = MaintenanceDetailEntity(expenseId, scheduleId, serviceName, workshop, nextOdometer, nextDateEpochDay)
private fun DocumentDto.toEntity() = DocumentEntity(id, vehicleId, type, name, issueDateEpochDay, expiryDateEpochDay, reminderLeadDays, fileUri, notes, createdAt)