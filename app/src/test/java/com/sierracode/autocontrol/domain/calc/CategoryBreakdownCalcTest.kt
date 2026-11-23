package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CategoryBreakdownCalcTest {

    @Test
    fun `devuelve null cuando la lista de inputs es vacia`() {
        val result = calculateCategoryBreakdown(emptyList())
        assertNull(result)
    }

    @Test
    fun `devuelve null cuando todos los montos son cero o negativos`() {
        val inputs = listOf(
            CategoryAmountInput("fuel_gas", "Gasolina", 0L),
            CategoryAmountInput("maint_oil", "Aceite", -100L)
        )
        val result = calculateCategoryBreakdown(inputs)
        assertNull(result)
    }

    @Test
    fun `calcula correctamente porcentajes y montos cuando hay hasta 4 categorias`() {
        val inputs = listOf(
            CategoryAmountInput("fuel_gas", "Gasolina", 50000L), // $500.00 (50%)
            CategoryAmountInput("maint_oil", "Mantenimiento", 30000L), // $300.00 (30%)
            CategoryAmountInput("other_wash", "Lavado", 20000L) // $200.00 (20%)
        )

        val result = calculateCategoryBreakdown(inputs)

        assertNotNull(result)
        assertEquals(3, result!!.size)

        assertEquals("fuel_gas", result[0].categoryId)
        assertEquals(50000L, result[0].amountCents)
        assertEquals(50.0, result[0].percentage, 0.001)

        assertEquals("maint_oil", result[1].categoryId)
        assertEquals(30000L, result[1].amountCents)
        assertEquals(30.0, result[1].percentage, 0.001)

        assertEquals("other_wash", result[2].categoryId)
        assertEquals(20000L, result[2].amountCents)
        assertEquals(20.0, result[2].percentage, 0.001)
    }

    @Test
    fun `agrupa en Otros a partir de la 4ta fila cuando hay mas de 4 categorias`() {
        val inputs = listOf(
            CategoryAmountInput("fuel_gas", "Gasolina", 40000L), // 40%
            CategoryAmountInput("maint_oil", "Mantenimiento", 30000L), // 30%
            CategoryAmountInput("other_ins", "Seguro", 15000L), // 15%
            CategoryAmountInput("other_wash", "Lavado", 10000L), // 10%
            CategoryAmountInput("other_park", "Estacionamiento", 5000L) // 5%
        )

        val result = calculateCategoryBreakdown(inputs, otherLabel = "Otros", maxRows = 4)

        assertNotNull(result)
        assertEquals(4, result!!.size)

        // Top 3 intactas
        assertEquals("fuel_gas", result[0].categoryId)
        assertEquals(40000L, result[0].amountCents)
        assertEquals(40.0, result[0].percentage, 0.001)

        assertEquals("maint_oil", result[1].categoryId)
        assertEquals(30000L, result[1].amountCents)
        assertEquals(30.0, result[1].percentage, 0.001)

        assertEquals("other_ins", result[2].categoryId)
        assertEquals(15000L, result[2].amountCents)
        assertEquals(15.0, result[2].percentage, 0.001)

        // Fila 4: agrupada en Otros (10000 + 5000 = 15000, 15%)
        assertEquals("other", result[3].categoryId)
        assertEquals("Otros", result[3].name)
        assertEquals(15000L, result[3].amountCents)
        assertEquals(15.0, result[3].percentage, 0.001)
    }

    @Test
    fun `combina entradas con el mismo categoryId antes del desglose`() {
        val inputs = listOf(
            CategoryAmountInput("fuel_gas", "Gasolina", 20000L),
            CategoryAmountInput("fuel_gas", "Gasolina", 30000L),
            CategoryAmountInput("maint_oil", "Mantenimiento", 50000L)
        )

        val result = calculateCategoryBreakdown(inputs)

        assertNotNull(result)
        assertEquals(2, result!!.size)
        // Ambos tienen 50000L (50%)
        assertEquals(50000L, result[0].amountCents)
        assertEquals(50.0, result[0].percentage, 0.001)
        assertEquals(50000L, result[1].amountCents)
        assertEquals(50.0, result[1].percentage, 0.001)
    }
}
