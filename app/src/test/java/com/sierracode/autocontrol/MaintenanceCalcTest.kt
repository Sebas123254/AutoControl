package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MaintenanceCalcTest {

    @Test
    fun `mantenimiento vencido por kilometraje`() {
        val status = evaluateSchedule(
            currentOdometer = 16000, todayEpochDay = 100, lastOdometer = 10000, intervalKm = 5000, lastDateEpochDay = null, intervalMonths = null
        )
        assertEquals(Urgency.OVERDUE, status.urgency)
        assertEquals(-1000, status.byKm)
    }

    @Test
    fun `mantenimiento proximo`() {
        val status = evaluateSchedule(
            currentOdometer = 14600, todayEpochDay = 100, lastOdometer = 10000, intervalKm = 5000, lastDateEpochDay = null, intervalMonths = null
        )
        assertEquals(Urgency.SOON, status.urgency)
        assertEquals(400, status.byKm)
    }

    @Test
    fun `mantenimiento al dia`() {
        val status = evaluateSchedule(
            currentOdometer = 11000, todayEpochDay = 100, lastOdometer = 10000, intervalKm = 5000, lastDateEpochDay = null, intervalMonths = null
        )
        assertEquals(Urgency.OK, status.urgency)
    }

    @Test
    fun `editar el intervalo recalcula el estado correctamente`() {
        // Escenario inicial: Vencido (faltan -1000)
        val initialStatus = evaluateSchedule(
            currentOdometer = 16000, todayEpochDay = 100, lastOdometer = 10000, intervalKm = 5000, lastDateEpochDay = null, intervalMonths = null
        )
        assertEquals(Urgency.OVERDUE, initialStatus.urgency)

        // Simulamos que el usuario edita el intervalo a 10,000
        val newStatus = evaluateSchedule(
            currentOdometer = 16000, todayEpochDay = 100, lastOdometer = 10000, intervalKm = 10000, lastDateEpochDay = null, intervalMonths = null
        )

        // Ahora faltan 4000 km, por lo que está OK
        assertEquals(Urgency.OK, newStatus.urgency)
        assertEquals(4000, newStatus.byKm)
    }
}