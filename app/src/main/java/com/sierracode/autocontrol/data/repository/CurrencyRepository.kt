package com.sierracode.autocontrol.data.repository

import androidx.room.withTransaction
import com.sierracode.autocontrol.data.local.AutoControlDatabase
import com.sierracode.autocontrol.domain.calc.CurrencyConverter
import javax.inject.Inject
import javax.inject.Singleton

interface CurrencyRepository {
    suspend fun convertAll(vehicleId: String, newCurrency: String, multiplier: Double)
}

@Singleton
class CurrencyRepositoryImpl @Inject constructor(
    private val db: AutoControlDatabase
) : CurrencyRepository {
    override suspend fun convertAll(vehicleId: String, newCurrency: String, multiplier: Double) {
        db.withTransaction {
            val vehicle = db.vehicleDao().get(vehicleId) ?: return@withTransaction

            // 1. Convertir Gastos
            val expenses = db.expenseDao().getAllOnce().filter { it.vehicleId == vehicleId }
            expenses.forEach { e ->
                db.expenseDao().upsert(e.copy(amountCents = CurrencyConverter.convertCents(e.amountCents, multiplier)))
            }

            // 2. Convertir Detalles de Gasolina (Precio por litro)
            val expenseIds = expenses.map { it.id }.toSet()
            val fuels = db.fuelDao().getAllOnce().filter { expenseIds.contains(it.expenseId) }
            fuels.forEach { f ->
                db.fuelDao().upsert(f.copy(pricePerLiterCents = CurrencyConverter.convertCents(f.pricePerLiterCents, multiplier)))
            }

            // 3. Convertir Objetivos
            val goals = db.goalDao().getAllOnce().filter { it.vehicleId == vehicleId }
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