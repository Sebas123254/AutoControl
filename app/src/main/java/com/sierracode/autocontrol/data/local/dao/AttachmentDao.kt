package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :id")
    fun observeByOwner(type: String, id: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :id LIMIT 1")
    suspend fun getForOwner(type: String, id: String): AttachmentEntity?

    @Upsert suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}