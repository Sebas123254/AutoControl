package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId ORDER BY triggerAtEpochMillis ASC")
    fun observeByVehicle(vehicleId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Upsert suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE sourceType = :type AND sourceId = :sourceId")
    suspend fun deleteBySource(type: String, sourceId: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("UPDATE reminders SET lastNotifiedStage = :stage, lastNotifiedAt = :timestamp WHERE id = :id")
    suspend fun updateLastNotifiedStage(id: String, stage: String?, timestamp: Long)

    @Query("SELECT * FROM reminders") suspend fun getAllOnce(): List<ReminderEntity>
}