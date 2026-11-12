package com.sierracode.autocontrol.data.repository

import androidx.room.withTransaction // <-- IMPORT QUE FALTABA
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.dao.GoalDao
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface GoalRepository {
    fun observeByVehicle(vehicleId: String): Flow<List<GoalEntity>>
    suspend fun get(id: String): GoalEntity?
    suspend fun save(goal: GoalEntity)
    suspend fun delete(id: String)
    suspend fun markCompleted(goalId: String, expenseId: String)
}

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val dao: GoalDao,
    private val db: AutoControlDatabase
) : GoalRepository {
    override fun observeByVehicle(vehicleId: String) = dao.observeByVehicle(vehicleId)
    override suspend fun get(id: String) = dao.get(id)
    override suspend fun save(goal: GoalEntity) = dao.upsert(goal)
    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun markCompleted(goalId: String, expenseId: String) {
        db.withTransaction {
            val goal = dao.get(goalId) ?: return@withTransaction
            dao.upsert(goal.copy(
                status = "COMPRADO",
                linkedExpenseId = expenseId,
                completedAt = System.currentTimeMillis()
            ))
        }
    }
}