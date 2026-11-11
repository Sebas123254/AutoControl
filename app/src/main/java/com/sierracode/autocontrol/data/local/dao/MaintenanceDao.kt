package com.sierracode.autocontrol.data.local.dao
import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.MaintenanceDetailEntity


@Dao
interface MaintenanceDao {
    @Upsert suspend fun upsert(detail: MaintenanceDetailEntity)

    @Query("SELECT * FROM maintenance_details") suspend fun getAllOnce(): List<MaintenanceDetailEntity>

    @Query("DELETE FROM maintenance_details") suspend fun deleteAll()
}