package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE vehicleId = :v ORDER BY expiryDateEpochDay ASC")
    fun observeAll(v: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents") suspend fun getAllOnce(): List<DocumentEntity>

    @Upsert suspend fun upsert(d: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents") suspend fun deleteAll()

    @Query("DELETE FROM documents WHERE id IN (:ids)")
    suspend fun deleteAllByIds(ids: List<String>)
}