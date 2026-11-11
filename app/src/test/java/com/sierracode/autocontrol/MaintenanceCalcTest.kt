package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MaintenanceCalcTest {

    @Test
    fun `mantenimiento vencido por kilometraje`() {
        // Último a 10,000; intervalo 5,000; actual 16,000 (se pasó por 1,000)
        val status = evaluateSchedule(
            currentOdometer = 16000,
            todayEpochDay = 100,
            lastOdometer = 10000,
            intervalKm = 5000,
            lastDateEpochDay = null,
            intervalMonths = null
        )
        assertEquals(Urgency.OVERDUE, status.urgency)
        assertEquals(-1000, status.byKm)
    }

    @Test
    fun `mantenimiento proximo`() {
        // Último a 10,000; intervalo 5,000; actual 14,600 (faltan 400)
        val status = evaluateSchedule(
            currentOdometer = 14600,
            todayEpochDay = 100,
            lastOdometer = 10000,
            intervalKm = 5000,
            lastDateEpochDay = null,
            intervalMonths = null
        )
        assertEquals(Urgency.SOON, status.urgency)
        assertEquals(400, status.byKm)
    }

    @Test
    fun `mantenimiento al dia`() {
        // Último a 10,000; intervalo 5,000; actual 11,000
        val status = evaluateSchedule(
            currentOdometer = 11000,
            todayEpochDay = 100,
            lastOdometer = 10000,
            intervalKm = 5000,
            lastDateEpochDay = null,
            intervalMonths = null
        )
        assertEquals(Urgency.OK, status.urgency)
    }
}