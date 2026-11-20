package com.sierracode.autocontrol.data.repository

import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.domain.calc.CurrencyConverter
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

interface CurrencyRepository {
    suspend fun convertAll(vehicleId: String, newCurrency: String, multiplier: BigDecimal)
}

@Singleton
class CurrencyRepositoryImpl @Inject constructor(
    private val db: AutoControlDatabase
) : CurrencyRepository {
    override suspend fun convertAll(vehicleId: String, newCurrency: String, multiplier: BigDecimal) {
        db.withTransaction {
            val vehicle = db.vehicleDao().get(vehicleId) ?: return@withTransaction

            // 1. Convertir Gastos
            val expenses = db.expenseDao().getByVehicleOnce(vehicleId)
            expenses.forEach { e ->
                db.expenseDao().upsert(e.copy(amountCents = CurrencyConverter.convertCents(e.amountCents, multiplier)))
            }

            // 2. Convertir Detalles de Gasolina
            val fuels = db.fuelDao().getByVehicleOnce(vehicleId)
            fuels.forEach { f ->
                db.fuelDao().upsert(f.copy(pricePerLiterCents = CurrencyConverter.convertCents(f.pricePerLiterCents, multiplier)))
            }

            // 3. Convertir Objetivos
            val goals = db.goalDao().getByVehicleOnce(vehicleId)
            goals.forEach { g ->
                db.goalDao().upsert(g.copy(
                    estimatedCents = CurrencyConverter.convertCents(g.estimatedCents, multiplier),
                    estimatedMaxCents = g.estimatedMaxCents?.let { CurrencyConverter.convertCents(it, multiplier) }
                ))
            }

            // 4. Actualizar el Vehículo
            db.vehicleDao().upsert(vehicle.copy(
                currencyCode = newCurrency,
                purchasePriceCents = vehicle.purchasePriceCents?.let { CurrencyConverter.convertCents(it, multiplier) }
            ))
        }
    }
}