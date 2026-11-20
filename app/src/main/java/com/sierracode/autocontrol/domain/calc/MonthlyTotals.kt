package com.sierracode.autocontrol.domain.calc

import java.time.YearMonth

data class MonthlyTotal(val month: YearMonth, val totalCents: Long)

/**
 * Totales por mes SIN huecos: los meses sin gastos valen 0.
 *
 * @param expenses pares (mes, monto en centavos), uno por gasto.
 * @param monthsBack cuántos meses mostrar, terminando en el mes más reciente
 *   (el actual, o el del último gasto si es futuro). null = desde el primer gasto.
 */
fun buildMonthlyTotals(
    expenses: List<Pair<YearMonth, Long>>,
    monthsBack: Int?,
    now: YearMonth = YearMonth.now()
): List<MonthlyTotal> {
    if (expenses.isEmpty()) return emptyList()

    val byMonth = expenses
        .groupBy({ it.first }, { it.second })
        .mapValues { entry -> entry.value.sum() }

    val last = maxOf(now, byMonth.keys.max())
    val first = if (monthsBack == null) {
        byMonth.keys.min()
    } else {
        last.minusMonths((monthsBack - 1).toLong())
    }

    return generateSequence(first) { it.plusMonths(1) }
        .takeWhile { it <= last }
        .map { MonthlyTotal(it, byMonth[it] ?: 0L) }
        .toList()
}