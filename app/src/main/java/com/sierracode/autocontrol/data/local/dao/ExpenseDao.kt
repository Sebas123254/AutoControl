package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import com.sierracode.autocontrol.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses") suspend fun getAllOnce(): List<ExpenseEntity>

    @Query("DELETE FROM expenses") suspend fun deleteAll()

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeByVehicle(vehicleId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId AND dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeByVehicleAndRange(vehicleId: String, from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun get(id: String): ExpenseEntity?

    /** Reactivo: para ExpenseDetailScreen (se actualiza si se restaura tras undo). */
    @Query("SELECT * FROM expenses WHERE id = :id") fun observeOne(id: String): Flow<ExpenseEntity?>

    @Upsert suspend fun upsert(e: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: String)

    @Query("DELETE FROM expenses WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT SUM(amountCents) FROM expenses WHERE vehicleId = :vehicleId AND dateEpochDay BETWEEN :from AND :to")
    fun observeSum(vehicleId: String, from: Long, to: Long): Flow<Long?>

    /** Odómetro más bajo registrado en los gastos del vehículo (null si ninguno lo tiene). */
    @Query("SELECT MIN(odometer) FROM expenses WHERE vehicleId = :vehicleId AND odometer IS NOT NULL")
    fun observeMinOdometer(vehicleId: String): Flow<Long?>

    @Query("""
        SELECT categoryId AS categoryId, SUM(amountCents) AS totalCents, COUNT(*) AS count
        FROM expenses WHERE vehicleId = :vehicleId AND dateEpochDay BETWEEN :from AND :to
        GROUP BY categoryId
    """)
    fun observeByCategory(vehicleId: String, from: Long, to: Long): Flow<List<CategoryTotalRow>>

    // --- Nuevos: lookups para el detalle ---
    @Query("SELECT * FROM fuel_details WHERE expenseId = :id")
    suspend fun getFuelDetail(id: String): FuelDetailEntity?

    @Query("SELECT * FROM maintenance_details WHERE expenseId = :id")
    suspend fun getMaintenanceDetail(id: String): MaintenanceDetailEntity?

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun getByVehicleOnce(vehicleId: String): List<ExpenseEntity>
}

data class CategoryTotalRow(val categoryId: String, val totalCents: Long, val count: Int)