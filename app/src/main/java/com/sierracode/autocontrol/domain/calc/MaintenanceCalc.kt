package com.sierracode.autocontrol.domain.calc

data class MaintenanceStatus(
    val byKm: Long?,          // km restantes; negativo si vencido
    val byDate: Long?,        // días restantes; negativo si vencido
    val urgency: Urgency
)
enum class Urgency { OK, SOON, OVERDUE, UNKNOWN }

fun evaluateSchedule(
    currentOdometer: Long,
    todayEpochDay: Long,
    lastOdometer: Long?,
    intervalKm: Long?,
    lastDateEpochDay: Long?,
    intervalMonths: Int?,
    remindBeforeKm: Long = 500,
    remindBeforeDays: Int = 15
): MaintenanceStatus {
    val byKm = if (lastOdometer != null && intervalKm != null) {
        (lastOdometer + intervalKm) - currentOdometer
    } else null

    val byDate = if (lastDateEpochDay != null && intervalMonths != null) {
        val next = lastDateEpochDay + intervalMonths * 30L // aproximación; la UI usa LocalDate para precisión
        next - todayEpochDay
    } else null

    val urgency = when {
        byKm == null && byDate == null -> Urgency.UNKNOWN
        (byKm != null && byKm <= 0) || (byDate != null && byDate <= 0) -> Urgency.OVERDUE
        (byKm != null && byKm <= remindBeforeKm) || (byDate != null && byDate <= remindBeforeDays) -> Urgency.SOON
        else -> Urgency.OK
    }
    return MaintenanceStatus(byKm, byDate, urgency)
}