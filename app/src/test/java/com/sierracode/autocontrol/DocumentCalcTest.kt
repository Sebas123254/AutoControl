package com.sierracode.autocontrol.domain.calc

import com.sierracode.autocontrol.data.local.entity.DocumentEntity
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

    private fun createDoc(id: String, name: String, expiry: Long?): DocumentEntity {
        return DocumentEntity(
            id = id,
            vehicleId = "v1",
            type = "SEGURO",
            name = name,
            expiryDateEpochDay = expiry
        )
    }

    @Test
    fun `filtro de vencimientos clasifica vencido, menor o igual a 30 dias, mayor a 30 dias y sin fecha`() {
        val today = 1000L
        val docExpired = createDoc("1", "Vencido", 995L) // -5 días (vencido)
        val docDueSoon = createDoc("2", "Por vencer en 15 dias", 1015L) // +15 días (<= 30)
        val docDueExact30 = createDoc("3", "Por vencer en 30 dias", 1030L) // +30 días (<= 30)
        val docDueLater = createDoc("4", "Vence en 45 dias", 1045L) // +45 días (> 30)
        val docNoExpiry = createDoc("5", "Sin fecha", null) // sin fecha

        val allDocs = listOf(docDueLater, docExpired, docNoExpiry, docDueExact30, docDueSoon)

        val filtered = filterExpiringDocuments(allDocs, todayEpochDay = today, maxDaysAhead = 30L)

        // Deben incluirse docExpired, docDueSoon y docDueExact30; excluir docDueLater y docNoExpiry
        assertEquals(3, filtered.size)

        // Ordenados por fecha de vencimiento ascendente
        assertEquals("1", filtered[0].document.id)
        assertTrue(filtered[0].isExpired)
        assertEquals(-5L, filtered[0].daysRemaining)

        assertEquals("2", filtered[1].document.id)
        assertFalse(filtered[1].isExpired)
        assertEquals(15L, filtered[1].daysRemaining)

        assertEquals("3", filtered[2].document.id)
        assertFalse(filtered[2].isExpired)
        assertEquals(30L, filtered[2].daysRemaining)
    }

    @Test
    fun `filtro de vencimientos limita a un maximo de 3 documentos`() {
        val today = 1000L
        val docs = listOf(
            createDoc("1", "Vencido 1", 990L),
            createDoc("2", "Vencido 2", 992L),
            createDoc("3", "Vence pronto 1", 1005L),
            createDoc("4", "Vence pronto 2", 1010L)
        )

        val filtered = filterExpiringDocuments(docs, todayEpochDay = today, maxDaysAhead = 30L, limit = 3)

        assertEquals(3, filtered.size)
        assertEquals(listOf("1", "2", "3"), filtered.map { it.document.id })
    }

    @Test
    fun `filtro de vencimientos devuelve lista vacia si no hay documentos aplicables`() {
        val today = 1000L
        val docs = listOf(
            createDoc("1", "Sin fecha", null),
            createDoc("2", "Vence en 60 dias", 1060L)
        )

        val filtered = filterExpiringDocuments(docs, todayEpochDay = today, maxDaysAhead = 30L)
        assertTrue(filtered.isEmpty())
    }
}
