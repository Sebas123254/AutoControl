package com.sierracode.autocontrol.data.repository

import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.dao.ExpenseDao
import com.sierracode.autocontrol.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ExpenseRepository {
    fun observeByVehicle(id: String): Flow<List<ExpenseEntity>>
    fun observeSum(id: String, from: Long, to: Long): Flow<Long?>
    fun observeByCategory(id: String, from: Long, to: Long): Flow<List<com.sierracode.autocontrol.data.local.dao.CategoryTotalRow>>
    suspend fun saveWithDetails(expense: ExpenseEntity, fuel: FuelDetailEntity?, maint: MaintenanceDetailEntity?, attachment: AttachmentEntity? = null)
    suspend fun delete(id: String)
}

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenses: ExpenseDao,
    private val db: AutoControlDatabase
) : ExpenseRepository {

    override fun observeByVehicle(id: String) = expenses.observeByVehicle(id)
    override fun observeSum(id: String, from: Long, to: Long) = expenses.observeSum(id, from, to)
    override fun observeByCategory(id: String, from: Long, to: Long) = expenses.observeByCategory(id, from, to)

    override suspend fun saveWithDetails(
        expense: ExpenseEntity,
        fuel: FuelDetailEntity?,
        maint: MaintenanceDetailEntity?,
        attachment: AttachmentEntity?
    ) {
        db.withTransaction {
            expenses.upsert(expense.copy(updatedAt = System.currentTimeMillis()))
            fuel?.let { db.fuelDao().upsert(it) }
            maint?.let { db.maintenanceDao().upsert(it) }
            attachment?.let { db.attachmentDao().upsert(it) } // <-- NUEVO
        }
    }

    override suspend fun delete(id: String) = expenses.delete(id)
}