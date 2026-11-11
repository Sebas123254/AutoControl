package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.MaintenanceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM maintenance_schedules WHERE vehicleId = :v AND active = 1")
    fun observeActive(v: String): Flow<List<MaintenanceScheduleEntity>>

    @Upsert suspend fun upsert(s: MaintenanceScheduleEntity)
}