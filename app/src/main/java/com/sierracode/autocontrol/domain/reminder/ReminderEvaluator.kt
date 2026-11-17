package com.sierracode.autocontrol.domain.reminder

import java.time.LocalDate

/**
 * Resultado de evaluar un recordatorio.
 *
 * @property stage Identificador del hito actual (ej. "date_30d", "km_100").
 *                 Puede ser un compuesto "date_7d|km_100" si aplican ambos.
 *                 Es null si aún no toca avisar.
 * @property isDue Si el hito actual ya se alcanzó y toca notificar.
 */
data class EvaluationResult(
    val stage: String?,
    val isDue: Boolean
) {
    val compositeStage: String? get() = stage
}

object ReminderEvaluator {

    /**
     * Regla para DOCUMENTOS:
     * - leadDays: primer aviso (ej. 30 días antes).
     * - Seguimiento: 7 días, 1 día, 0 días (vencimiento).
     * - No avisa antes de leadDays.
     */
    fun evaluateDocument(
        expiryDateEpochDay: Long,
        todayEpochDay: Long,
        reminderLeadDays: Int
    ): EvaluationResult {
        val daysUntil = expiryDateEpochDay - todayEpochDay
        val stage = when {
            daysUntil <= 0 -> "date_0d"
            daysUntil <= 1 -> "date_1d"
            daysUntil <= 7 -> "date_7d"
            daysUntil <= reminderLeadDays -> "date_lead"
            else -> null
        }
        return EvaluationResult(stage, stage != null)
    }

    /**
     * Regla para MANTENIMIENTOS:
     * - Fecha: remindBeforeDays, luego 7d, 1d, 0d.
     * - Km: remindBeforeKm, luego 100km, 0km.
     * - Si aplican ambos, el stage se compone con "|" ordenado alfabéticamente.
     */
    fun evaluateMaintenance(
        todayEpochDay: Long,
        currentOdometer: Long,
        lastDateEpochDay: Long?,
        intervalMonths: Int?,
        remindBeforeDays: Int,
        lastOdometer: Long?,
        intervalKm: Long?,
        remindBeforeKm: Long
    ): EvaluationResult {
        val stages = mutableListOf<String>()

        // 1. Evaluar fecha
        if (lastDateEpochDay != null && intervalMonths != null) {
            val nextDate = LocalDate.ofEpochDay(lastDateEpochDay).plusMonths(intervalMonths.toLong())
            val daysUntil = nextDate.toEpochDay() - todayEpochDay
            when {
                daysUntil <= 0 -> stages.add("date_0d")
                daysUntil <= 1 -> stages.add("date_1d")
                daysUntil <= 7 -> stages.add("date_7d")
                daysUntil <= remindBeforeDays -> stages.add("date_lead")
            }
        }

        // 2. Evaluar kilometraje
        if (lastOdometer != null && intervalKm != null) {
            val targetOdo = lastOdometer + intervalKm
            val kmUntil = targetOdo - currentOdometer
            when {
                kmUntil <= 0 -> stages.add("km_0")
                kmUntil <= 100 -> stages.add("km_100")
                kmUntil <= remindBeforeKm -> stages.add("km_lead")
            }
        }

        if (stages.isEmpty()) return EvaluationResult(null, false)

        val composite = stages.sorted().joinToString("|")
        return EvaluationResult(composite, true)
    }
}