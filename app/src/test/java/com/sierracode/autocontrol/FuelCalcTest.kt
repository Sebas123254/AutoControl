package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FuelCalcTest {
    private val p1 = FuelPoint(1000, 40.0, true, 80000, 100)
    private val p2 = FuelPoint(1500, 40.0, true, 80000, 130)

    private fun pt(odo: Long?, liters: Double, full: Boolean, day: Long) =
        FuelPoint(odo, liters, full, 0L, day)

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

    @Test
    fun `promedio tanque lleno sin cargas parciales`() {
        val pts = listOf(pt(1000, 40.0, true, 1), pt(1500, 40.0, true, 10))
        assertEquals(12.5, averageFullTankConsumption(pts)!!, 0.001)
    }

    @Test
    fun `incluye litros de cargas parciales intermedias`() {
        val pts = listOf(
            pt(1000, 40.0, true, 1),
            pt(1250, 15.0, false, 5),
            pt(1500, 25.0, true, 10)
        )
        // 500 km / (15 + 25) L = 12.5
        assertEquals(12.5, averageFullTankConsumption(pts)!!, 0.001)
    }

    @Test
    fun `devuelve null con un solo tanque lleno`() {
        val pts = listOf(pt(1000, 40.0, true, 1), pt(1200, 20.0, false, 5))
        assertNull(averageFullTankConsumption(pts))
    }

    @Test
    fun `devuelve null si los tanques llenos no tienen odometro`() {
        val pts = listOf(pt(null, 40.0, true, 1), pt(null, 40.0, true, 10))
        assertNull(averageFullTankConsumption(pts))
    }

    @Test
    fun `ignora tramos con odometro que no crece`() {
        val pts = listOf(
            pt(1000, 40.0, true, 1),
            pt(900, 30.0, true, 5),
            pt(1400, 40.0, true, 10)
        )
        // el primer tramo se descarta; queda 500 km / 40 L
        assertEquals(12.5, averageFullTankConsumption(pts)!!, 0.001)
    }

    @Test
    fun `acepta puntos desordenados`() {
        val pts = listOf(pt(1500, 40.0, true, 10), pt(1000, 40.0, true, 1))
        assertEquals(12.5, averageFullTankConsumption(pts)!!, 0.001)
    }

    @Test
    fun `gasto de los ultimos 12 meses usa la fecha de hoy`() {
        val today = LocalDate.of(2026, 11, 17)
        val pts = listOf(
            FuelPoint(1000, 10.0, true, 5000L, LocalDate.of(2026, 10, 1).toEpochDay()),
            FuelPoint(1100, 10.0, true, 3000L, LocalDate.of(2025, 12, 1).toEpochDay()),
            FuelPoint(1200, 10.0, true, 9000L, LocalDate.of(2025, 10, 1).toEpochDay())
        )
        assertEquals(8000L, summarizeFuel(pts, today).last12MonthsSpentCents)
    }

    @Test
    fun `detecta sobreconsumo cuando el ultimo rendimiento cae mas del 15 por ciento`() {
        val pts = listOf(
            pt(1000, 40.0, true, 1),
            pt(1500, 40.0, true, 5),  // 500 km / 40 L = 12.5 km/L
            pt(2000, 40.0, true, 10), // 500 km / 40 L = 12.5 km/L
            pt(2360, 40.0, true, 15)  // 360 km / 40 L = 9.0 km/L (caída de 21% sobre el promedio global)
        )
        val summary = summarizeFuel(pts)
        assertTrue(summary.isOverconsuming)
        assertTrue(summary.consumptionDropPercent > 15)
        assertEquals(9.0, summary.latestKmPerLiter!!, 0.001)
    }
}