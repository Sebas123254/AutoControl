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

/**
 * Snapshot completo de un gasto para poder borrarlo y restaurarlo con undo.
 * Incluye los detalles de fuel/maintenance (que CASCADE borraría) y el attachment.
 */
data class ExpenseDeletionSnapshot(
    val expense: ExpenseEntity,
    val fuel: FuelDetailEntity?,
    val maintenance: MaintenanceDetailEntity?,
    val attachment: AttachmentEntity?
)

interface ExpenseRepository {
    fun observeByVehicle(id: String): Flow<List<ExpenseEntity>>
    fun observeSum(id: String, from: Long, to: Long): Flow<Long?>

    fun observeMinOdometer(id: String): Flow<Long?>
    fun observeByCategory(id: String, from: Long, to: Long): Flow<List<com.sierracode.autocontrol.data.local.dao.CategoryTotalRow>>
    fun observeOne(id: String): Flow<ExpenseEntity?>
    suspend fun get(id: String): ExpenseEntity?
    suspend fun getFuelDetail(id: String): FuelDetailEntity?
    suspend fun getMaintenanceDetail(id: String): MaintenanceDetailEntity?
    suspend fun saveWithDetails(
        expense: ExpenseEntity,
        fuel: FuelDetailEntity?,
        maint: MaintenanceDetailEntity?,
        attachment: AttachmentEntity? = null
    )
    suspend fun delete(id: String)
    suspend fun deleteMultiple(ids: List<String>)

    /** Borra expense + fuel + maint + attachment de Room, PERO no el archivo físico. */
    suspend fun deleteDeferred(id: String)

    /** Captura un snapshot completo del expense (para undo). */
    suspend fun captureForDeletion(id: String): ExpenseDeletionSnapshot?

    /** Restaura un snapshot (reinserta en Room). */
    suspend fun restoreFromSnapshot(snapshot: ExpenseDeletionSnapshot)

    /** Borra el archivo físico del attachment cuando NO se hace undo. */
    fun purgeAttachmentFile(uriString: String?)
}

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenses: ExpenseDao,
    private val db: AutoControlDatabase
) : ExpenseRepository {

    override fun observeByVehicle(id: String) = expenses.observeByVehicle(id)
    override fun observeSum(id: String, from: Long, to: Long) = expenses.observeSum(id, from, to)
    override fun observeMinOdometer(id: String) = expenses.observeMinOdometer(id)
    override fun observeByCategory(id: String, from: Long, to: Long) = expenses.observeByCategory(id, from, to)
    override fun observeOne(id: String) = expenses.observeOne(id)
    override suspend fun get(id: String) = expenses.get(id)
    override suspend fun getFuelDetail(id: String) = expenses.getFuelDetail(id)
    override suspend fun getMaintenanceDetail(id: String) = expenses.getMaintenanceDetail(id)

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

        db.withTransaction {
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

            // Room onDelete = CASCADE elimina automáticamente fuel_details y maintenance_details.
            // Los attachments de gastos se eliminan de la base de datos Room,
            // pero NO se borran los archivos físicos (los limpia OrphanFileSweeper respetando la ventana de 24h).
            val attachments = db.attachmentDao().getForOwners("expense", ids)
            attachments.forEach { db.attachmentDao().deleteById(it.id) }

            expenses.deleteByIds(ids)
        }
    }

    override suspend fun deleteDeferred(id: String) {
        db.withTransaction {
            // FK CASCADE borra fuel_details y maintenance_details.
            expenses.delete(id)
            // Attachments no tienen FK: borrar manual.
            val att = db.attachmentDao().getForOwner("expense", id)
            att?.let { db.attachmentDao().deleteById(it.id) }
            // Si algún goal apuntaba a este expense, vuelve a PENDIENTE.
            val affectedGoals = db.goalDao().getAllOnce().filter { it.linkedExpenseId == id }
            affectedGoals.forEach { goal ->
                db.goalDao().upsert(
                    goal.copy(
                        status = "PENDIENTE",
                        linkedExpenseId = null,
                        completedAt = null
                    )
                )
            }
        }
    }

    override suspend fun captureForDeletion(id: String): ExpenseDeletionSnapshot? {
        val expense = expenses.get(id) ?: return null
        val fuel = expenses.getFuelDetail(id)
        val maint = expenses.getMaintenanceDetail(id)
        val attachment = db.attachmentDao().getForOwner("expense", id)
        return ExpenseDeletionSnapshot(expense, fuel, maint, attachment)
    }

    override suspend fun restoreFromSnapshot(snapshot: ExpenseDeletionSnapshot) {
        db.withTransaction {
            expenses.upsert(snapshot.expense)
            snapshot.fuel?.let { db.fuelDao().upsert(it) }
            snapshot.maintenance?.let { db.maintenanceDao().upsert(it) }
            snapshot.attachment?.let { db.attachmentDao().upsert(it) }
        }
    }

    override fun purgeAttachmentFile(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        withContext_noop {
            try {
                val path = Uri.parse(uriString).path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private inline fun withContext_noop(block: () -> Unit) = block()
}