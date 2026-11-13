package com.sierracode.autocontrol.domain.calc

import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import java.text.Normalizer
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

enum class ExpensePeriod { ALL, THIS_MONTH, LAST_MONTH, CUSTOM }

data class ExpenseFilterState(
    val query: String = "",
    val period: ExpensePeriod = ExpensePeriod.ALL,
    val customMonth: YearMonth? = null,
    val categoryIds: Set<String> = emptySet()
)

// Normaliza texto: minúsculas, sin acentos, sin espacios extra
fun normalizeText(text: String): String {
    if (text.isBlank()) return ""
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    return regex.replace(normalized, "")
        .lowercase(Locale.getDefault())
        .trim()
}

fun filterExpenses(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    filterState: ExpenseFilterState,
    today: YearMonth = YearMonth.now(ZoneId.systemDefault())
): List<ExpenseEntity> {
    val normalizedQuery = normalizeText(filterState.query)

    // 1. Filtro por periodo
    val periodFiltered = expenses.filter { expense ->
        val expenseDate = java.time.LocalDate.ofEpochDay(expense.dateEpochDay)
        val expenseYearMonth = YearMonth.from(expenseDate)

        when (filterState.period) {
            ExpensePeriod.ALL -> true
            ExpensePeriod.THIS_MONTH -> expenseYearMonth == today
            ExpensePeriod.LAST_MONTH -> expenseYearMonth == today.minusMonths(1)
            ExpensePeriod.CUSTOM -> filterState.customMonth == null || expenseYearMonth == filterState.customMonth
        }
    }

    // 2. Filtro por categoría
    val categoryFiltered = if (filterState.categoryIds.isEmpty()) {
        periodFiltered
    } else {
        periodFiltered.filter { it.categoryId in filterState.categoryIds }
    }

    // 3. Filtro por texto y monto
    if (normalizedQuery.isEmpty()) return categoryFiltered

    return categoryFiltered.filter { expense ->
        // Categoría
        val categoryName = categories.find { it.id == expense.categoryId }?.name ?: ""
        val matchesCategory = normalizeText(categoryName).contains(normalizedQuery)

        // Otros textos
        val matchesDescription = normalizeText(expense.description ?: "").contains(normalizedQuery)
        val matchesCustom = normalizeText(expense.customCategoryName ?: "").contains(normalizedQuery)
        val matchesNotes = normalizeText(expense.notes ?: "").contains(normalizedQuery)
        val matchesEstablishment = normalizeText(expense.establishment ?: "").contains(normalizedQuery)

        // Monto (ej. "450" o "450.50")
        val amountStr = String.format(Locale.US, "%.2f", expense.amountCents / 100.0)
        val matchesAmount = amountStr.contains(normalizedQuery)

        matchesCategory || matchesDescription || matchesCustom || matchesNotes || matchesEstablishment || matchesAmount
    }
}

fun calculateTotalCents(expenses: List<ExpenseEntity>): Long {
    return expenses.sumOf { it.amountCents }
}