package com.sierracode.autocontrol.domain.calc

import com.sierracode.autocontrol.data.local.entity.GoalEntity

data class GoalCalcData(val estimatedCents: Long, val estimatedMaxCents: Long?, val savedCents: Long = 0L)
data class GoalTotals(val minTotal: Long, val maxTotal: Long, val totalSaved: Long = 0L)

fun calculatePendingTotals(pendingGoals: List<GoalCalcData>): GoalTotals? {
    if (pendingGoals.isEmpty()) return null
    val min = pendingGoals.sumOf { it.estimatedCents }
    val max = pendingGoals.sumOf { it.estimatedMaxCents ?: it.estimatedCents }
    val saved = pendingGoals.sumOf { it.savedCents }
    return GoalTotals(min, max, saved)
}

fun goalProgress(savedCents: Long, targetCents: Long): Float {
    if (targetCents <= 0L) return 0f
    return (savedCents.toFloat() / targetCents.toFloat()).coerceIn(0f, 1f)
}

// --- NUEVA LÓGICA DE FILTRO Y ORDEN ---

enum class GoalFilter { ALL, HIGH, MEDIUM, LOW }
enum class GoalSort { PRIORITY_DESC, PRIORITY_ASC, TARGET_DATE, AMOUNT_DESC }

fun filterAndSortGoals(goals: List<GoalEntity>, filter: GoalFilter, sort: GoalSort): List<GoalEntity> {
    val filtered = goals.filter {
        when (filter) {
            GoalFilter.ALL -> true
            GoalFilter.HIGH -> it.priority == "ALTA"
            GoalFilter.MEDIUM -> it.priority == "MEDIA"
            GoalFilter.LOW -> it.priority == "BAJA"
        }
    }
    return filtered.sortedWith(getComparator(sort))
}

private fun getComparator(sort: GoalSort): Comparator<GoalEntity> {
    val priorityOrder = mapOf("ALTA" to 1, "MEDIA" to 2, "BAJA" to 3)
    // Comparador para fecha: los nulos van al final
    val dateComparator = compareBy<GoalEntity> { it.targetDateEpochDay == null }
        .thenBy { it.targetDateEpochDay }
    val amountComparator = compareByDescending<GoalEntity> { it.estimatedCents }
    val createdComparator = compareBy<GoalEntity> { it.createdAt }

    return when (sort) {
        GoalSort.PRIORITY_DESC -> compareBy<GoalEntity> { priorityOrder[it.priority] ?: 99 }
            .then(dateComparator)
            .then(amountComparator)
            .then(createdComparator)

        GoalSort.PRIORITY_ASC -> compareByDescending<GoalEntity> { priorityOrder[it.priority] ?: 99 }
            .then(dateComparator)
            .then(amountComparator)
            .then(createdComparator)

        GoalSort.TARGET_DATE -> dateComparator
            .thenBy { priorityOrder[it.priority] ?: 99 }
            .then(amountComparator)
            .then(createdComparator)

        GoalSort.AMOUNT_DESC -> amountComparator
            .thenBy { priorityOrder[it.priority] ?: 99 }
            .then(dateComparator)
            .then(createdComparator)
    }
}