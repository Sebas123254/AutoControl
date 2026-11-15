package com.sierracode.autocontrol.data.repository

import androidx.room.withTransaction
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.dao.DocumentDao
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import com.sierracode.autocontrol.domain.reminder.ReminderResolver
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface DocumentRepository {
    fun observeByVehicle(vehicleId: String): Flow<List<DocumentEntity>>
    fun observeOne(id: String): Flow<DocumentEntity?>
    suspend fun get(id: String): DocumentEntity?
    suspend fun save(document: DocumentEntity)
    suspend fun delete(id: String)
    suspend fun deleteMultiple(ids: List<String>)

    /**
     * Borra el documento de Room + su recordatorio, PERO NO el archivo físico.
     * Se usa para undo: si el usuario hace undo, el archivo sigue ahí.
     */
    suspend fun deleteDeferred(id: String)

    /** Elimina el archivo físico. Solo cuando el undo NO se toma. */
    fun purgeFile(fileUri: String?)
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val db: AutoControlDatabase
) : DocumentRepository {
    override fun observeByVehicle(vehicleId: String) = dao.observeAll(vehicleId)
    override fun observeOne(id: String) = dao.observeOne(id)
    override suspend fun get(id: String) = dao.getById(id)

    override suspend fun save(document: DocumentEntity) {
        db.withTransaction {
            dao.upsert(document)

            db.reminderDao().deleteBySource("DOCUMENT", document.id)

            val expiry = document.expiryDateEpochDay ?: return@withTransaction
            val triggerDay = expiry - document.reminderLeadDays

            val triggerMillis = LocalDate.ofEpochDay(triggerDay)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val reminder = ReminderEntity(
                vehicleId = document.vehicleId,
                sourceType = "DOCUMENT",
                sourceId = document.id,
                title = "Documento por vencer: ${document.name}",
                body = "Tu documento vence pronto. ¡Revisa AutoControl!",
                triggerAtEpochMillis = triggerMillis,
                templateKey = "document_expiring",
                templateArgs = ReminderResolver.encodeDocumentExpiringArgs(document.name)
            )
            db.reminderDao().upsert(reminder)
        }
    }

    override suspend fun delete(id: String) {
        val doc = dao.getById(id)
        db.withTransaction {
            dao.deleteById(id)
            db.reminderDao().deleteBySource("DOCUMENT", id)
        }
        ImageUtils.deleteLocalFile(doc?.fileUri)
    }

    override suspend fun deleteMultiple(ids: List<String>) {
        val docs = ids.mapNotNull { dao.getById(it) }
        db.withTransaction {
            dao.deleteAllByIds(ids)
            ids.forEach { docId ->
                db.reminderDao().deleteBySource("DOCUMENT", docId)
            }
        }
        docs.forEach { ImageUtils.deleteLocalFile(it.fileUri) }
    }

    override suspend fun deleteDeferred(id: String) {
        db.withTransaction {
            dao.deleteById(id)
            db.reminderDao().deleteBySource("DOCUMENT", id)
        }
    }

    override fun purgeFile(fileUri: String?) {
        ImageUtils.deleteLocalFile(fileUri)
    }
}