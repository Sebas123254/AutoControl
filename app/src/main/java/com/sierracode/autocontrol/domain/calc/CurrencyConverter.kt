package com.sierracode.autocontrol.domain.calc

import java.math.BigDecimal
import java.math.RoundingMode

object CurrencyConverter {
    /**
     * Convierte un monto en centavos usando un multiplicador.
     */
    fun convertCents(amountCents: Long, multiplier: Double): Long {
        return BigDecimal(amountCents)
            .multiply(BigDecimal(multiplier.toString()))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}