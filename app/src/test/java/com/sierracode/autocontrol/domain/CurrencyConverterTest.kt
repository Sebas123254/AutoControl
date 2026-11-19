package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrencyConverterTest {
    @Test
    fun `conversion basica redondea half up`() {
        // Multiplicador 17.50: 1000 centavos * 17.50 = 17500 centavos
        assertEquals(17500L, CurrencyConverter.convertCents(1000L, 17.50))
        // Multiplicador inverso (1 / 17.50 = ~0.057142857): 1000 centavos * 0.057142857 = 57.14 -> 57 centavos
        assertEquals(57L, CurrencyConverter.convertCents(1000L, 1.0 / 17.50))
    }

    @Test
    fun `conversion con tasa menor a 1`() {
        // 1000 centavos * 0.057 = 57 centavos
        assertEquals(57L, CurrencyConverter.convertCents(1000L, 0.057))
    }

    @Test
    fun `tasa invalida devuelve lo mismo`() {
        assertEquals(1000L, CurrencyConverter.convertCents(1000L, 0.0))
        assertEquals(1000L, CurrencyConverter.convertCents(1000L, -1.0))
    }
}