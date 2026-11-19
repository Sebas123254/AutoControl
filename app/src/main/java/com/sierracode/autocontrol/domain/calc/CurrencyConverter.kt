package com.sierracode.autocontrol.domain.calc

import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyConverter {
    /**
     * Convierte un monto en centavos usando un multiplicador.
     * Si el multiplicador es menor o igual a 0, devuelve el monto original sin alterar.
     */
    fun convertCents(amountCents: Long, multiplier: Double): Long {
        if (multiplier <= 0.0) return amountCents
        return BigDecimal(amountCents)
            .multiply(BigDecimal(multiplier.toString()))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}