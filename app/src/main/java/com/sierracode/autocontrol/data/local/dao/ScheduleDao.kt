package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :v AND active = 1")
    fun observeActive(v: String): Flow<List<MaintenanceScheduleEntity>>

    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :v AND name = :name AND active = 1 LIMIT 1")
    suspend fun getActiveByName(v: String, name: String): MaintenanceScheduleEntity?

    @Query("DELETE FROM maintenance_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Upsert suspend fun upsert(s: MaintenanceScheduleEntity)

    // --- NUEVOS PARA BACKUP ---
    @Query("SELECT * FROM maintenance_schedules") suspend fun getAllOnce(): List<MaintenanceScheduleEntity>
    @Query("DELETE FROM maintenance_schedules") suspend fun deleteAll()
}