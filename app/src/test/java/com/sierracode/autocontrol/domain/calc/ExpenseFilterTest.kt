package com.sierracode.autocontrol.domain.calc

import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseFilterTest {

    private val catGas = CategoryEntity(id = "gas", group = "FUEL", name = "Gasolina", iconKey = "")
    private val catLav = CategoryEntity(id = "lav", group = "OTHER", name = "Lavado", iconKey = "")
    private val categories = listOf(catGas, catLav)

    private val dateJan2024 = LocalDate.of(2024, 1, 15).toEpochDay()
    private val dateFeb2024 = LocalDate.of(2024, 2, 10).toEpochDay()

    private val e1 = ExpenseEntity(id = "1", vehicleId = "v", type = "FUEL", categoryId = "gas", amountCents = 45000, dateEpochDay = dateJan2024, description = "Cámbio de aceite")
    private val e2 = ExpenseEntity(id = "2", vehicleId = "v", type = "OTHER", categoryId = "lav", amountCents = 15000, dateEpochDay = dateFeb2024, notes = "Lavado completo")
    private val e3 = ExpenseEntity(id = "3", vehicleId = "v", type = "OTHER", categoryId = "lav", amountCents = 20000, dateEpochDay = dateFeb2024, customCategoryName = "Lavado de motor")

    private val expenses = listOf(e1, e2, e3)

    @Test
    fun `filtro de texto ignora acentos y mayusculas`() {
        val state = ExpenseFilterState(query = "cambio")
        val result = filterExpenses(expenses, categories, state)
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun `filtro por monto`() {
        val state = ExpenseFilterState(query = "150")
        val result = filterExpenses(expenses, categories, state)
        assertEquals(1, result.size)
        assertEquals("2", result.first().id)
    }

    @Test
    fun `filtro por periodo este mes`() {
        val state = ExpenseFilterState(period = ExpensePeriod.THIS_MONTH)
        val today = YearMonth.of(2024, 2)
        val result = filterExpenses(expenses, categories, state, today)
        assertEquals(2, result.size) // e2 y e3
    }

    @Test
    fun `filtro combinado texto y categoria`() {
        val state = ExpenseFilterState(query = "lavado", categoryIds = setOf("lav"))
        val result = filterExpenses(expenses, categories, state)
        assertEquals(2, result.size)
    }

    @Test
    fun `total no desborda`() {
        val result = calculateTotalCents(expenses)
        assertEquals(80000L, result)
    }
}