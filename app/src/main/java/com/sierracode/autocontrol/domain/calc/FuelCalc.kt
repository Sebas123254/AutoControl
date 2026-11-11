package com.sierracode.autocontrol.domain.calc

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

/**
 * Calcula el consumo real entre dos cargas consecutivas, SOLO si ambas son tanque lleno
 * y hay odómetro válido y creciente. Devuelve null en caso contrario (nunca inventa).
 */
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

/**
 * Resume histórico. Promedio km/L se calcula solo entre pares full-tank consecutivos.
 */
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

    val distinctMonths = points.map { it.epochDay / 30 }.distinct().size
    val monthlyAvg = if (distinctMonths > 0) totalSpent / distinctMonths else null

    return FuelSummary(
        avgKmPerLiter = avgKmPerLiter,
        avgPricePerLiterCents = pricePerLiter,
        totalLiters = totalLiters,
        totalSpentCents = totalSpent,
        monthlyAvgSpentCents = monthlyAvg,
        annualSpentCents = totalSpent // se ajustará por rango en el use-case
    )
}