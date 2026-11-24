package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class CurrencyConverterTest {

    @Test
    fun `ida y vuelta conserva el valor`() {
        val originalCents = 175000L // 1750.00
        val rate = BigDecimal("17.5")
        val inverseRate = BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_UP)

        // Convertir a USD
        val usdCents = CurrencyConverter.convertCents(originalCents, inverseRate)

        // Volver a MXN
        val backToMxnCents = CurrencyConverter.convertCents(usdCents, rate)

        assertEquals(originalCents, backToMxnCents)
    }

    @Test
    fun `multiplicador invalido no altera`() {
        val originalCents = 1250L
        val result = CurrencyConverter.convertCents(originalCents, BigDecimal.ZERO)
        assertEquals(originalCents, result)
    }
}