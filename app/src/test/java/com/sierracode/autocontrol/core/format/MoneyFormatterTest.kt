package com.sierracode.autocontrol.core.format

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MoneyFormatterTest {

    @Test
    fun `format con centavos devuelve monto con simbolo de pesos y dos decimales`() {
        assertEquals("$2,475.00", MoneyFormatter.format(247500L))
        assertEquals("$0.71", MoneyFormatter.format(71L))
        assertEquals("$1,000,000.50", MoneyFormatter.format(100000050L))
    }

    @Test
    fun `format con cero devuelve cero con dos decimales`() {
        assertEquals("$0.00", MoneyFormatter.format(0L))
    }

    @Test
    fun `format con null devuelve guion largo y nunca cero`() {
        assertEquals("—", MoneyFormatter.format(null))
        assertEquals("—", MoneyFormatter.formatWithCode(null))
        assertEquals("—", MoneyFormatter.formatPerDistance(null, "km"))
    }

    @Test
    fun `format con valores negativos muestra signo correctamente`() {
        assertEquals("-$2,475.00", MoneyFormatter.format(-247500L))
        assertEquals("-$0.50", MoneyFormatter.format(-50L))
        assertEquals("-$2,475.00 MXN", MoneyFormatter.formatWithCode(-247500L, "MXN"))
        assertEquals("-$0.71/km", MoneyFormatter.formatPerDistance(-71L, "km"))
    }

    @Test
    fun `formatWithCode agrega codigo de divisa al final`() {
        assertEquals("$2,475.00 MXN", MoneyFormatter.formatWithCode(247500L, "MXN"))
        assertEquals("$2,475.00 USD", MoneyFormatter.formatWithCode(247500L, "USD"))
        assertEquals("$2,475.00 EUR", MoneyFormatter.formatWithCode(247500L, "EUR"))
    }

    @Test
    fun `sin codigo duplicado ni pegado`() {
        val result = MoneyFormatter.formatWithCode(247500L, "MXN")
        // No debe tener prefijo MXN pegado ni duplicado
        assertFalse(result.startsWith("MXN"))
        assertFalse(result.startsWith("MX$"))
        assertEquals("$2,475.00 MXN", result)

        val countMxn = "MXN".toRegex().findAll(result).count()
        assertEquals(1, countMxn)
    }

    @Test
    fun `formatPerDistance formatea con unidad sin duplicar barra`() {
        assertEquals("$0.71/km", MoneyFormatter.formatPerDistance(71L, "km"))
        assertEquals("$0.71/km", MoneyFormatter.formatPerDistance(71L, "/km"))
        assertEquals("$1.14/mi", MoneyFormatter.formatPerDistance(114L, "mi"))
        assertEquals("$0.00/km", MoneyFormatter.formatPerDistance(0L, "km"))
    }
}
