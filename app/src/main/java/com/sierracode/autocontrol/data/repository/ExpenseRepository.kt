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
    suspend fun saveWithDetails(expense: ExpenseEntity, fuel: FuelDetailEntity?, maint: MaintenanceDetailEntity?, attachment: AttachmentEntity? = null)
    suspend fun delete(id: String)

    // --- NUEVO ---
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

        // 1. Obtener los adjuntos ANTES de borrar la BD para saber qué archivos borrar
        val attachments = db.attachmentDao().getForOwners("expense", ids)

        // 2. Borrar en BD dentro de una transacción atómica
        db.withTransaction {
            // Gracias al CASCADE de Room, fuel_details, maintenance_details se borran solos.
            expenses.deleteByIds(ids)
            // Adjuntos no tienen FK, así que los borramos manualmente
            attachments.forEach { db.attachmentDao().deleteById(it.id) }
        }

        // 3. Borrar archivos físicos (solo si la BD no lanzó excepción)
        withContext(Dispatchers.IO) {
            attachments.forEach { attachment ->
                try {
                    val path = Uri.parse(attachment.uri).path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                } catch (e: Exception) {
                    // Un archivo que ya no existe no debe romper el flujo
                    e.printStackTrace()
                }
            }
        }
    }
}