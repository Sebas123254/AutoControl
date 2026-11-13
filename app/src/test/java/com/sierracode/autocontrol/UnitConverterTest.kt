package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CostCalcTest {
    @Test
    fun `costo por km correcto`() {
        assertEquals(2.31, costPerKm(CostPerKmInput(231_000, 100_000, 200_000))!!, 0.001)
    }

    @Test
    fun `devuelve null con 0 km`() {
        assertNull(costPerKm(CostPerKmInput(5000, 100_000, 100_000)))
    }

    @Test
    fun `devuelve null con odometro invertido`() {
        assertNull(costPerKm(CostPerKmInput(5000, 100_000, 90_000)))
    }

    @Test
    fun `calcular costo total devuelve null si no hay precio de compra`() {
        assertNull(calculateTotalCost(null, 500_000))
    }

    @Test
    fun `calcular costo total suma precio y gastos`() {
        // Precio 1,000,000 + Gastos 500,000 = 1,500,000
        assertEquals(1_500_000L, calculateTotalCost(1_000_000L, 500_000L))
    }
}