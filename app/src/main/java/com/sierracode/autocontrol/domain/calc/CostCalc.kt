package com.sierracode.autocontrol.domain.calc

data class CostPerKmInput(
    val totalSpentCents: Long,
    val odometerStart: Long,
    val odometerEnd: Long
)

/**
 * Costo por km = gasto total / km recorridos.
 * Devuelve null si los km recorridos son 0 o negativos (no inventa).
 */
fun costPerKm(input: CostPerKmInput): Double? {
    val km = input.odometerEnd - input.odometerStart
    if (km <= 0L) return null
    return input.totalSpentCents.toDouble() / km
}

/**
 * Costo total acumulado = precio de compra + gastos totales.
 * Devuelve null si no hay precio de compra registrado.
 */
fun calculateTotalCost(purchasePriceCents: Long?, totalSpentCents: Long): Long? {
    if (purchasePriceCents == null) return null
    return purchasePriceCents + totalSpentCents
}