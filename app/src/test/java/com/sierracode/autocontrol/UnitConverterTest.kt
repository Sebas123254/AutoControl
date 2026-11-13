package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UnitConverterTest {
    @Test
    fun `costo por km correcto`() {
        // 231,000 centavos ($2,310) / 100,000 km = 2.31 centavos por km
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
}