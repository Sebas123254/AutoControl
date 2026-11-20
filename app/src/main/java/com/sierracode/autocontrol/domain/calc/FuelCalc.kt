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

/**
 * Consumo promedio (km/L) con el método tanque lleno a tanque lleno.
 * Entre dos tanques llenos con odómetro suma los litros de TODAS las cargas posteriores
 * al primero (incluidas las parciales) hasta el segundo. Los tramos con odómetro que no
 * crece se ignoran. Devuelve null si no hay al menos un tramo válido.
 */
fun averageFullTankConsumption(points: List<FuelPoint>): Double? {
    val sorted = points.sortedWith(
        compareBy<FuelPoint>({ it.epochDay }, { it.odometer ?: Long.MAX_VALUE })
    )
    var totalKm = 0L
    var totalLiters = 0.0
    var anchorOdo: Long? = null
    var litersSinceAnchor = 0.0

    for (p in sorted) {
        if (anchorOdo != null) litersSinceAnchor += p.liters
        val odo = p.odometer
        if (p.fullTank && odo != null) {
            val prev = anchorOdo
            if (prev != null) {
                val km = odo - prev
                if (km > 0L && litersSinceAnchor > 0.0) {
                    totalKm += km
                    totalLiters += litersSinceAnchor
                }
            }
            anchorOdo = odo
            litersSinceAnchor = 0.0
        }
    }
    return if (totalKm > 0L && totalLiters > 0.0) totalKm / totalLiters else null
}

data class FuelSummary(
    val avgKmPerLiter: Double?,
    val avgPricePerLiterCents: Double?,
    val totalLiters: Double,
    val totalSpentCents: Long,
    val monthlyAvgSpentCents: Long?,
    val last12MonthsSpentCents: Long
)

fun summarizeFuel(points: List<FuelPoint>, today: LocalDate = LocalDate.now()): FuelSummary {
    if (points.isEmpty()) return FuelSummary(null, null, 0.0, 0L, null, 0L)

    val totalLiters = points.sumOf { it.liters }
    val totalSpent = points.sumOf { it.amountCents }

    val avgKmPerLiter = averageFullTankConsumption(points)

    val pricePerLiter = points.filter { it.liters > 0 }
        .map { it.amountCents / it.liters }
        .takeIf { it.isNotEmpty() }?.average()

    // Promedio mensual: agrupa por mes/año real, no por bloques de 30 días
    val distinctMonths = points.map {
        YearMonth.from(LocalDate.ofEpochDay(it.epochDay))
    }.distinct().size
    val monthlyAvg = if (distinctMonths > 0) totalSpent / distinctMonths else null

    // Gasto de los últimos 12 meses reales, contados hacia atrás desde hoy
    val windowStart = today.minusYears(1).toEpochDay()
    val windowEnd = today.toEpochDay()
    val last12Months = points
        .filter { it.epochDay in windowStart..windowEnd }
        .sumOf { it.amountCents }

    return FuelSummary(
        avgKmPerLiter = avgKmPerLiter,
        avgPricePerLiterCents = pricePerLiter,
        totalLiters = totalLiters,
        totalSpentCents = totalSpent,
        monthlyAvgSpentCents = monthlyAvg,
        last12MonthsSpentCents = last12Months
    )
}