package com.sierracode.autocontrol.domain.calc

data class GoalCalcData(val estimatedCents: Long, val estimatedMaxCents: Long?)
data class GoalTotals(val minTotal: Long, val maxTotal: Long)

fun calculatePendingTotals(pendingGoals: List<GoalCalcData>): GoalTotals? {
    if (pendingGoals.isEmpty()) return null
    val min = pendingGoals.sumOf { it.estimatedCents }
    val max = pendingGoals.sumOf { it.estimatedMaxCents ?: it.estimatedCents }
    return GoalTotals(min, max)
}