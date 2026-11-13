package com.sierracode.autocontrol.domain.calc

import com.sierracode.autocontrol.data.local.entity.GoalEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoalCalcTest {

    private val g1 = GoalEntity(id = "1", vehicleId = "v", title = "A", estimatedCents = 100, priority = "ALTA", targetDateEpochDay = 100, createdAt = 1)
    private val g2 = GoalEntity(id = "2", vehicleId = "v", title = "B", estimatedCents = 500, priority = "BAJA", targetDateEpochDay = null, createdAt = 2)
    private val g3 = GoalEntity(id = "3", vehicleId = "v", title = "C", estimatedCents = 300, priority = "MEDIA", targetDateEpochDay = 50, createdAt = 3)
    private val g4 = GoalEntity(id = "4", vehicleId = "v", title = "D", estimatedCents = 100, priority = "ALTA", targetDateEpochDay = null, createdAt = 4)

    private val goals = listOf(g1, g2, g3, g4)

    @Test
    fun `filtro Alta devuelve solo los de prioridad ALTA`() {
        val result = filterAndSortGoals(goals, GoalFilter.HIGH, GoalSort.PRIORITY_DESC)
        assertEquals(2, result.size)
        assertEquals(listOf("1", "4"), result.map { it.id })
    }

    @Test
    fun `orden PRIORITY_DESC ordena ALTA, MEDIA, BAJA y desempata por fecha`() {
        val result = filterAndSortGoals(goals, GoalFilter.ALL, GoalSort.PRIORITY_DESC)
        // ALTA: g1 (fecha 100), g4 (nula) -> g1 luego g4
        // MEDIA: g3
        // BAJA: g2
        assertEquals(listOf("1", "4", "3", "2"), result.map { it.id })
    }

    @Test
    fun `orden PRIORITY_ASC ordena BAJA, MEDIA, ALTA`() {
        val result = filterAndSortGoals(goals, GoalFilter.ALL, GoalSort.PRIORITY_ASC)
        assertEquals(listOf("2", "3", "1", "4"), result.map { it.id })
    }

    @Test
    fun `orden TARGET_DATE pone nulos al final y desempata por prioridad`() {
        val result = filterAndSortGoals(goals, GoalFilter.ALL, GoalSort.TARGET_DATE)
        // Fechas: g3 (50), g1 (100), luego g4 (nula, ALTA), g2 (nula, BAJA)
        assertEquals(listOf("3", "1", "4", "2"), result.map { it.id })
    }

    @Test
    fun `orden AMOUNT_DESC ordena por monto y desempata por prioridad`() {
        val result = filterAndSortGoals(goals, GoalFilter.ALL, GoalSort.AMOUNT_DESC)
        // Montos: g2 (500), g3 (300), g1 (100), g4 (100). g1 y g4 empatan en 100, pero g1 es ALTA y g4 es ALTA, desempatan por fecha (g1 100, g4 null) -> g1 luego g4.
        assertEquals(listOf("2", "3", "1", "4"), result.map { it.id })
    }
}