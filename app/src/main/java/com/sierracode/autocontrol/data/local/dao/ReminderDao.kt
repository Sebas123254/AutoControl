package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    /** Recordatorios que estaban programados para hoy o antes y no se han notificado hoy. */
    @Query("SELECT * FROM reminders WHERE enabled = 1 AND triggerAtEpochMillis <= :now AND (lastNotifiedAt IS NULL OR lastNotifiedAt < :startOfDay)")
    suspend fun getDueReminders(now: Long, startOfDay: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId ORDER BY triggerAtEpochMillis ASC")
    fun observeByVehicle(vehicleId: String): Flow<List<ReminderEntity>>

    @Upsert suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE sourceType = :type AND sourceId = :sourceId")
    suspend fun deleteBySource(type: String, sourceId: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("UPDATE reminders SET lastNotifiedAt = :time WHERE id = :id")
    suspend fun markNotified(id: String, time: Long)

    /**
     * Actualiza el hito notificado + timestamp, en una sola operación.
     * Se usa desde ReminderWorker para evitar notificaciones repetidas.
     */
    @Query("UPDATE reminders SET lastNotifiedStage = :stage, lastNotifiedAt = :timestamp WHERE id = :id")
    suspend fun updateLastNotifiedStage(id: String, stage: String?, timestamp: Long)

    @Query("SELECT * FROM reminders") suspend fun getAllOnce(): List<ReminderEntity>
}