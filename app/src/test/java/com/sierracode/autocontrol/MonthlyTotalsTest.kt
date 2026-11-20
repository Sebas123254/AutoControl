package com.sierracode.autocontrol.domain.calc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.YearMonth

class MonthlyTotalsTest {
    private val now = YearMonth.of(2026, 11)

    @Test
    fun `sin gastos devuelve lista vacia`() {
        assertTrue(buildMonthlyTotals(emptyList(), 12, now).isEmpty())
    }

    @Test
    fun `rellena con cero los meses sin gastos`() {
        val expenses = listOf(
            YearMonth.of(2026, 9) to 1000L,
            YearMonth.of(2026, 11) to 500L
        )
        val r = buildMonthlyTotals(expenses, 3, now)
        assertEquals(listOf(1000L, 0L, 500L), r.map { it.totalCents })
        assertEquals(YearMonth.of(2026, 9), r.first().month)
        assertEquals(YearMonth.of(2026, 11), r.last().month)
    }

    @Test
    fun `suma varios gastos del mismo mes`() {
        val expenses = listOf(
            YearMonth.of(2026, 11) to 300L,
            YearMonth.of(2026, 11) to 200L
        )
        val r = buildMonthlyTotals(expenses, 1, now)
        assertEquals(1, r.size)
        assertEquals(500L, r[0].totalCents)
    }

    @Test
    fun `limita a los ultimos N meses`() {
        val expenses = listOf(
            YearMonth.of(2025, 1) to 999L,
            YearMonth.of(2026, 11) to 100L
        )
        val r = buildMonthlyTotals(expenses, 6, now)
        assertEquals(6, r.size)
        assertEquals(YearMonth.of(2026, 6), r.first().month)
        assertEquals(100L, r.last().totalCents)
    }

    @Test
    fun `todo empieza en el primer gasto`() {
        val expenses = listOf(
            YearMonth.of(2026, 9) to 100L,
            YearMonth.of(2026, 10) to 200L
        )
        val r = buildMonthlyTotals(expenses, null, now)
        assertEquals(3, r.size) // sep, oct y nov (mes actual, en cero)
        assertEquals(0L, r.last().totalCents)
    }

    @Test
    fun `un gasto con fecha futura extiende el rango`() {
        val expenses = listOf(YearMonth.of(2027, 1) to 700L)
        val r = buildMonthlyTotals(expenses, 3, now)
        assertEquals(YearMonth.of(2027, 1), r.last().month)
        assertEquals(700L, r.last().totalCents)
    }
}