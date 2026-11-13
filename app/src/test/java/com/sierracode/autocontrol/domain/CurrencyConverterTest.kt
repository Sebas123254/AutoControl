package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CurrencyConverterTest {
    @Test
    fun `conversion basica redondea half up`() {
        // 1000 centavos (10.00 MXN) / 17.50 = 57.14 -> 57 centavos
        assertEquals(57L, CurrencyConverter.convertCents(1000L, 17.50))
    }

    @Test
    fun `conversion con tasa menor a 1`() {
        // 1000 centavos (10.00 USD) / 0.057 = 17543.8 -> 17544 centavos
        assertEquals(17544L, CurrencyConverter.convertCents(1000L, 0.057))
    }

    @Test
    fun `tasa invalida devuelve lo mismo`() {
        assertEquals(1000L, CurrencyConverter.convertCents(1000L, 0.0))
        assertEquals(1000L, CurrencyConverter.convertCents(1000L, -1.0))
    }
}