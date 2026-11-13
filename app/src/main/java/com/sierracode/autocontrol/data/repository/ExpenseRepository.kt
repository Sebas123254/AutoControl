package com.sierracode.autocontrol.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.data.local.dao.ExpenseDao
import com.sierracode.autocontrol.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ExpenseRepository {
    fun observeByVehicle(id: String): Flow<List<ExpenseEntity>>
    fun observeSum(id: String, from: Long, to: Long): Flow<Long?>
    fun observeByCategory(id: String, from: Long, to: Long): Flow<List<com.sierracode.autocontrol.data.local.dao.CategoryTotalRow>>
    suspend fun get(id: String): ExpenseEntity?
    suspend fun saveWithDetails(
        expense: ExpenseEntity,
        fuel: FuelDetailEntity?,
        maint: MaintenanceDetailEntity?,
        attachment: AttachmentEntity? = null
    )
    suspend fun delete(id: String)
    suspend fun deleteMultiple(ids: List<String>)
}

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenses: ExpenseDao,
    private val db: AutoControlDatabase
) : ExpenseRepository {

    override fun observeByVehicle(id: String) = expenses.observeByVehicle(id)
    override fun observeSum(id: String, from: Long, to: Long) = expenses.observeSum(id, from, to)
    override fun observeByCategory(id: String, from: Long, to: Long) = expenses.observeByCategory(id, from, to)
    override suspend fun get(id: String) = expenses.get(id)

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
            attachment?.let { db.attachmentDao().upsert(it) }
        }
    }

    override suspend fun delete(id: String) = expenses.delete(id)

    override suspend fun deleteMultiple(ids: List<String>) {
        if (ids.isEmpty()) return

        // 1. Adjuntos antes de borrar la BD (para saber qué archivos borrar)
        val attachments = db.attachmentDao().getForOwners("expense", ids)

        // 2. Transacción atómica
        db.withTransaction {
            // O2: si algún objetivo está vinculado a un gasto que se está borrando,
            // lo devolvemos a PENDIENTE. Sin esto queda "COMPRADO" apuntando a un gasto inexistente.
            val affectedGoals = db.goalDao().getAllOnce().filter { it.linkedExpenseId in ids }
            affectedGoals.forEach { goal ->
                db.goalDao().upsert(
                    goal.copy(
                        status = "PENDIENTE",
                        linkedExpenseId = null,
                        completedAt = null
                    )
                )
            }

            // CASCADE de Room borra fuel_details y maintenance_details
            expenses.deleteByIds(ids)
            // Attachments no tienen FK: borrar manualmente
            attachments.forEach { db.attachmentDao().deleteById(it.id) }
        }

        // 3. Archivos físicos (solo si la BD no lanzó excepción)
        withContext(Dispatchers.IO) {
            attachments.forEach { attachment ->
                try {
                    val path = Uri.parse(attachment.uri).path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}