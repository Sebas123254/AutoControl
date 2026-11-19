package com.sierracode.autocontrol.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sierracode.autocontrol.core.util.ImageUtils
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.dao.DocumentDao
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import com.sierracode.autocontrol.domain.reminder.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface DocumentRepository {
    fun observeByVehicle(vehicleId: String): Flow<List<DocumentEntity>>
    fun observeOne(id: String): Flow<DocumentEntity?>
    suspend fun get(id: String): DocumentEntity?
    suspend fun save(document: DocumentEntity)
    suspend fun delete(id: String)
    suspend fun deleteMultiple(ids: List<String>)
    suspend fun deleteDeferred(id: String)
    fun purgeFile(fileUri: String?)
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    private val db: AutoControlDatabase,
    private val reminderPlanner: ReminderPlanner,
    @ApplicationContext private val context: Context
) : DocumentRepository {
    override fun observeByVehicle(vehicleId: String) = dao.observeAll(vehicleId)
    override fun observeOne(id: String) = dao.observeOne(id)
    override suspend fun get(id: String) = dao.getById(id)

    override suspend fun save(document: DocumentEntity) {
        db.withTransaction {
            dao.upsert(document)
        }
        reminderPlanner.planDocument(document)
    }

    override suspend fun delete(id: String) {
        val doc = dao.getById(id)
        db.withTransaction {
            dao.deleteById(id)
            db.reminderDao().deleteBySource("DOCUMENT", id)
        }
        ImageUtils.deleteLocalFile(context, doc?.fileUri)
    }

    override suspend fun deleteMultiple(ids: List<String>) {
        val docs = ids.mapNotNull { dao.getById(it) }
        db.withTransaction {
            dao.deleteAllByIds(ids)
            ids.forEach { docId ->
                db.reminderDao().deleteBySource("DOCUMENT", docId)
            }
        }
        docs.forEach { ImageUtils.deleteLocalFile(context, it.fileUri) }
    }

    override suspend fun deleteDeferred(id: String) {
        db.withTransaction {
            dao.deleteById(id)
            db.reminderDao().deleteBySource("DOCUMENT", id)
        }
    }

    override fun purgeFile(fileUri: String?) {
        ImageUtils.deleteLocalFile(context, fileUri)
    }
}