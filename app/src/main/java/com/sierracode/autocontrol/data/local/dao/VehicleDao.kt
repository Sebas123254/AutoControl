package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC") fun observeAll(): Flow<List<VehicleEntity>>
    @Query("SELECT * FROM vehicles WHERE id = :id") fun observe(id: String): Flow<VehicleEntity?>
    @Query("SELECT * FROM vehicles WHERE id = :id") suspend fun get(id: String): VehicleEntity?
    @Upsert suspend fun upsert(v: VehicleEntity)
    @Query("DELETE FROM vehicles WHERE id = :id") suspend fun delete(id: String)

    @Query("UPDATE vehicles SET odometer = :odo, updatedAt = :now WHERE id = :id AND odometer < :odo")
    suspend fun bumpOdometer(id: String, odo: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM vehicles") suspend fun count(): Int

    @Query("SELECT * FROM vehicles") suspend fun getAllOnce(): List<VehicleEntity>

    @Query("DELETE FROM vehicles") suspend fun deleteAll()

    @Query("DELETE FROM vehicles WHERE id IN (:ids)")
    suspend fun deleteAllByIds(ids: List<String>)

}