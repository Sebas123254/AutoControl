package com.sierracode.autocontrol.data.repository

import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.dao.DocumentDao
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

interface DocumentRepository {
    fun observeByVehicle(vehicleId: String): Flow<List<DocumentEntity>>
    suspend fun get(id: String): DocumentEntity?
    suspend fun save(document: DocumentEntity)
    suspend fun delete(id: String)

    suspend fun deleteMultiple(ids: List<String>)
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val db: AutoControlDatabase // <-- Necesitamos la DB para la transacción
) : DocumentRepository {
    override fun observeByVehicle(vehicleId: String) = dao.observeAll(vehicleId)
    override suspend fun get(id: String) = dao.getById(id)

    override suspend fun save(document: DocumentEntity) {
        db.withTransaction {
            dao.upsert(document)

            // Calculamos cuándo debe sonar la alarma
            val expiry = document.expiryDateEpochDay ?: return@withTransaction
            val triggerDay = expiry - document.reminderLeadDays
            val triggerMillis = TimeUnit.DAYS.toMillis(triggerDay)

            // Creamos el recordatorio
            val reminder = ReminderEntity(
                vehicleId = document.vehicleId,
                sourceType = "DOCUMENT",
                sourceId = document.id,
                title = "Documento por vencer: ${document.name}",
                body = "Tu documento vence pronto. ¡Revisa AutoControl!",
                triggerAtEpochMillis = triggerMillis
            )
            db.reminderDao().upsert(reminder)
        }
    }

    override suspend fun delete(id: String) {
        db.withTransaction {
            dao.deleteById(id)
            db.reminderDao().deleteBySource("DOCUMENT", id)
        }
    }
    override suspend fun deleteMultiple(ids: List<String>) {
        db.withTransaction {
            dao.deleteAllByIds(ids)
            // Borrar recordatorios asociados si es necesario (opcional por ahora)
        }
    }
}