package com.sierracode.autocontrol.domain.calc

import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyConverter {
    /**
     * Convierte un monto en centavos usando un multiplicador.
     * Si el multiplicador es menor o igual a 0, devuelve el monto original sin alterar.
     */
    fun convertCents(amountCents: Long, multiplier: BigDecimal): Long {
        if (multiplier <= BigDecimal.ZERO) return amountCents
        return BigDecimal(amountCents)
            .multiply(multiplier)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}