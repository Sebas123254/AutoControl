package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE vehicleId = :vehicleId ORDER BY status ASC, priority ASC, targetDateEpochDay ASC")
    fun observeByVehicle(vehicleId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): GoalEntity?

    @Upsert suspend fun upsert(goal: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM goals") suspend fun getAllOnce(): List<GoalEntity>
    @Query("DELETE FROM goals") suspend fun deleteAll()
}