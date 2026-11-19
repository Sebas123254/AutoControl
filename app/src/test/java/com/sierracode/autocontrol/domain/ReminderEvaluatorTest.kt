package com.sierracode.autocontrol.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReminderEvaluatorTest {

    @Test
    fun `documento a 90 dias no notifica`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val expiry = today + 90
        val result = ReminderEvaluator.evaluateDocument(expiry, today, reminderLeadDays = 30)
        assertNull(result.stage)
    }

    @Test
    fun `documento al leadDays notifica`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val expiry = today + 30
        val result = ReminderEvaluator.evaluateDocument(expiry, today, reminderLeadDays = 30)
        assertEquals("date_lead", result.stage)
    }

    @Test
    fun `documento a 7 dias notifica`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val expiry = today + 7
        val result = ReminderEvaluator.evaluateDocument(expiry, today, reminderLeadDays = 30)
        assertEquals("date_7d", result.stage)
    }

    @Test
    fun `documento a 1 dia notifica`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val expiry = today + 1
        val result = ReminderEvaluator.evaluateDocument(expiry, today, reminderLeadDays = 30)
        assertEquals("date_1d", result.stage)
    }

    @Test
    fun `documento hoy o vencido notifica`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val expiry = today
        val result = ReminderEvaluator.evaluateDocument(expiry, today, reminderLeadDays = 30)
        assertEquals("date_0d", result.stage)
    }

    @Test
    fun `mantenimiento por km notifica en 500`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val result = ReminderEvaluator.evaluateMaintenance(
            todayEpochDay = today,
            currentOdometer = 14500, // Faltan 500 para 15000
            lastDateEpochDay = null, intervalMonths = null, remindBeforeDays = 15,
            lastOdometer = 10000, intervalKm = 5000, remindBeforeKm = 500
        )
        assertEquals("km_lead", result.stage)
    }

    @Test
    fun `mantenimiento por km notifica en 100`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val result = ReminderEvaluator.evaluateMaintenance(
            todayEpochDay = today,
            currentOdometer = 14900,
            lastDateEpochDay = null, intervalMonths = null, remindBeforeDays = 15,
            lastOdometer = 10000, intervalKm = 5000, remindBeforeKm = 500
        )
        assertEquals("km_100", result.stage)
    }

    @Test
    fun `mantenimiento por km vencido`() {
        val today = LocalDate.of(2026, 1, 1).toEpochDay()
        val result = ReminderEvaluator.evaluateMaintenance(
            todayEpochDay = today,
            currentOdometer = 15000,
            lastDateEpochDay = null, intervalMonths = null, remindBeforeDays = 15,
            lastOdometer = 10000, intervalKm = 5000, remindBeforeKm = 500
        )
        assertEquals("km_0", result.stage)
    }

    @Test
    fun `mantenimiento combinado junta ambos stages`() {
        val todayLocal = LocalDate.of(2026, 1, 1)
        val today = todayLocal.toEpochDay()

        // Simulamos que faltan 7 días para el vencimiento
        val lastDateAdj = todayLocal.minusMonths(6).plusDays(7).toEpochDay()

        val result = ReminderEvaluator.evaluateMaintenance(
            todayEpochDay = today,
            currentOdometer = 14900, // km_100
            lastDateEpochDay = lastDateAdj, intervalMonths = 6, remindBeforeDays = 15,
            lastOdometer = 10000, intervalKm = 5000, remindBeforeKm = 500
        )
        assertEquals("date_7d|km_100", result.stage)
    }
}