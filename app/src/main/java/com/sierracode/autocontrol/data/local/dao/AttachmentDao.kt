package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :id ORDER BY createdAt ASC")
    fun observeByOwner(type: String, id: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :id ORDER BY createdAt ASC LIMIT 1")
    suspend fun getForOwner(type: String, id: String): AttachmentEntity?

    @Upsert suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM attachments") suspend fun getAllOnce(): List<AttachmentEntity>
    @Query("DELETE FROM attachments") suspend fun deleteAll()

    // --- NUEVO: Obtener adjuntos de varios gastos ---
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId IN (:ids)")
    suspend fun getForOwners(type: String, ids: List<String>): List<AttachmentEntity>
}