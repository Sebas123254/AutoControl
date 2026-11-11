package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FuelCalcTest {
    private val p1 = FuelPoint(1000, 40.0, true, 80000, 100)
    private val p2 = FuelPoint(1500, 40.0, true, 80000, 130)

    @Test
    fun `calcula km por litro entre dos llenos`() {
        val r = consumptionBetween(p1, p2)!!
        assertEquals(12.5, r.kmPerLiter, 0.001) // 500 km / 40 L = 12.5
        assertEquals(8.0, r.litersPer100Km, 0.001)
        assertEquals(500L, r.km)
    }

    @Test
    fun `devuelve null si la carga anterior no fue tanque lleno`() {
        assertNull(consumptionBetween(p1.copy(fullTank = false), p2))
    }

    @Test
    fun `devuelve null si odometro no crece`() {
        assertNull(consumptionBetween(p2, p1))
    }

    @Test
    fun `devuelve null con litros cero`() {
        assertNull(consumptionBetween(p1, p2.copy(liters = 0.0)))
    }

    @Test
    fun `devuelve null sin odometro`() {
        assertNull(consumptionBetween(p1.copy(odometer = null), p2))
    }
}