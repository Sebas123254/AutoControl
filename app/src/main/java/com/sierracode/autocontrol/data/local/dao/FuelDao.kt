package com.sierracode.autocontrol.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.sierracode.autocontrol.data.local.entity.FuelDetailEntity
@Dao
interface FuelDao {
    @Query("""
        SELECT e.id, e.vehicleId, e.amountCents, e.dateEpochDay, e.odometer,
               f.litersMilli, f.pricePerLiterCents, f.fuelType, f.station, f.fullTank
        FROM expenses e INNER JOIN fuel_details f ON f.expenseId = e.id
        WHERE e.vehicleId = :vehicleId
        ORDER BY e.dateEpochDay DESC, e.createdAt DESC
    """)
    fun observeFuel(vehicleId: String): Flow<List<FuelWithExpense>>

    @Query("DELETE FROM fuel_details") suspend fun deleteAll()

    @Upsert suspend fun upsert(detail: com.sierracode.autocontrol.data.local.entity.FuelDetailEntity)

    @Query("SELECT * FROM fuel_details") suspend fun getAllOnce(): List<FuelDetailEntity>
    data class FuelWithExpense(
        val id: String, val vehicleId: String, val amountCents: Long,
        val dateEpochDay: Long, val odometer: Long?, val litersMilli: Long,
        val pricePerLiterCents: Long, val fuelType: String, val station: String?, val fullTank: Boolean
)
}