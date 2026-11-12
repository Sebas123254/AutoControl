package com.sierracode.autocontrol.domain.calc

import java.time.LocalDate
import java.time.YearMonth

/** Punto de carga de combustible. */
data class FuelPoint(
    val odometer: Long?,
    val liters: Double,
    val fullTank: Boolean,
    val amountCents: Long,
    val epochDay: Long
)

data class FuelConsumption(
    val kmPerLiter: Double,
    val litersPer100Km: Double,
    val costPerKmCents: Double,
    val km: Long
)

fun consumptionBetween(previous: FuelPoint, current: FuelPoint): FuelConsumption? {
    if (!previous.fullTank || !current.fullTank) return null
    val pOdo = previous.odometer ?: return null
    val cOdo = current.odometer ?: return null
    val km = cOdo - pOdo
    if (km <= 0L) return null
    val liters = current.liters
    if (liters <= 0.0) return null
    return FuelConsumption(
        kmPerLiter = km / liters,
        litersPer100Km = (liters / km) * 100.0,
        costPerKmCents = current.amountCents.toDouble() / km,
        km = km
    )
}

data class FuelSummary(
    val avgKmPerLiter: Double?,
    val avgPricePerLiterCents: Double?,
    val totalLiters: Double,
    val totalSpentCents: Long,
    val monthlyAvgSpentCents: Long?,
    val annualSpentCents: Long
)

fun summarizeFuel(points: List<FuelPoint>): FuelSummary {
    if (points.isEmpty()) return FuelSummary(null, null, 0.0, 0L, null, 0L)

    val totalLiters = points.sumOf { it.liters }
    val totalSpent = points.sumOf { it.amountCents }

    val consumptions = mutableListOf<Double>()
    for (i in 1 until points.size) {
        consumptionBetween(points[i - 1], points[i])?.let { consumptions += it.kmPerLiter }
    }
    val avgKmPerLiter = if (consumptions.isEmpty()) null else consumptions.average()

    val pricePerLiter = points.filter { it.liters > 0 }
        .map { it.amountCents / it.liters }
        .takeIf { it.isNotEmpty() }?.average()

    // --- CORRECCIÓN DE FECHAS ---
    // Agrupamos por mes/año real, no por bloques de 30 días
    val distinctMonths = points.map {
        YearMonth.from(LocalDate.ofEpochDay(it.epochDay))
    }.distinct().size
    val monthlyAvg = if (distinctMonths > 0) totalSpent / distinctMonths else null

    // Gasto anual: sumamos solo los puntos del mismo año que la última carga registrada
    val lastYear = LocalDate.ofEpochDay(points.last().epochDay).year
    val annualSpent = points.filter {
        LocalDate.ofEpochDay(it.epochDay).year == lastYear
    }.sumOf { it.amountCents }

    return FuelSummary(
        avgKmPerLiter = avgKmPerLiter,
        avgPricePerLiterCents = pricePerLiter,
        totalLiters = totalLiters,
        totalSpentCents = totalSpent,
        monthlyAvgSpentCents = monthlyAvg,
        annualSpentCents = annualSpent // Ahora sí es anual
    )
}