package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DocumentCalcTest {
    @Test
    fun `documento vencido`() {
        val eval = evaluateDocument(expiryDateEpochDay = 100, todayEpochDay = 110, leadDays = 30)
        assertEquals(DocumentStatus.EXPIRED, eval.status)
        assertEquals(-10, eval.daysRemaining)
    }

    @Test
    fun `documento por vencer`() {
        val eval = evaluateDocument(expiryDateEpochDay = 120, todayEpochDay = 100, leadDays = 30)
        assertEquals(DocumentStatus.EXPIRING_SOON, eval.status)
        assertEquals(20, eval.daysRemaining)
    }

    @Test
    fun `documento vigente`() {
        val eval = evaluateDocument(expiryDateEpochDay = 200, todayEpochDay = 100, leadDays = 30)
        assertEquals(DocumentStatus.VALID, eval.status)
        assertEquals(100, eval.daysRemaining)
    }
}