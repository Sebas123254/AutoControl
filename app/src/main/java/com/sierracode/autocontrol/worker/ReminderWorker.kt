package com.sierracode.autocontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import com.sierracode.autocontrol.data.repository.DocumentRepository
import com.sierracode.autocontrol.data.repository.ReminderRepository
import com.sierracode.autocontrol.data.repository.ScheduleRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Evalúa cada reminder activo y calcula su "hito actual" (stage):
 *
 * Por FECHA (documentos + mantenimiento por fecha):
 *   - date_30d: faltan 30 días o menos para vencer.
 *   - date_7d:  faltan 7 días o menos.
 *   - date_1d:  falta 1 día o menos.
 *   - date_0d:  vence hoy o ya venció.
 *
 * Por KILOMETRAJE (solo mantenimiento):
 *   - km_500: faltan 500 km o menos.
 *   - km_100: faltan 100 km o menos.
 *   - km_0:   se alcanzó o superó el objetivo.
 *
 * Solo se notifica cuando el stage actual difiere del último ya notificado
 * (guardado en ReminderEntity.lastNotifiedStage). Esto evita el spam diario.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepo: ReminderRepository,
    private val documentRepo: DocumentRepository,
    private val scheduleRepo: ScheduleRepository,
    private val vehicleRepo: VehicleRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val reminders = reminderRepo.getAllOnce().filter { it.enabled }

        if (reminders.isEmpty()) return Result.success()

        data class DueItem(
            val reminder: ReminderEntity,
            val stage: String,
            val title: String,
            val body: String
        )

        val dueItems = mutableListOf<DueItem>()

        for (reminder in reminders) {
            val stages = mutableListOf<String>()

            when (reminder.sourceType) {
                "DOCUMENT" -> {
                    val doc = documentRepo.get(reminder.sourceId) ?: continue
                    val expiry = doc.expiryDateEpochDay ?: continue
                    computeDateStage(LocalDate.ofEpochDay(expiry), today)?.let { stages.add(it) }
                }
                "MAINTENANCE" -> {
                    val schedule = scheduleRepo.getById(reminder.sourceId) ?: continue
                    val vehicle = vehicleRepo.get(reminder.vehicleId) ?: continue

                    // 1. Por fecha (si tiene lastDateEpochDay + intervalMonths)
                    if (schedule.lastDateEpochDay != null && schedule.intervalMonths != null) {
                        val nextDate = LocalDate.ofEpochDay(schedule.lastDateEpochDay)
                            .plusMonths(schedule.intervalMonths.toLong())
                        computeDateStage(nextDate, today)?.let { stages.add(it) }
                    }

                    // 2. Por kilometraje (si tiene lastOdometer + intervalKm)
                    if (schedule.lastOdometer != null && schedule.intervalKm != null) {
                        val targetOdo = schedule.lastOdometer + schedule.intervalKm
                        computeKmStage(
                            targetOdo = targetOdo,
                            currentOdo = vehicle.odometer,
                            remindBeforeKm = schedule.remindBeforeKm
                        )?.let { stages.add(it) }
                    }
                }
            }

            if (stages.isEmpty()) continue

            // Compuesto ordenado para que sea estable y comparable.
            val compositeStage = stages.sorted().joinToString("|")

            if (compositeStage != reminder.lastNotifiedStage) {
                val title = resolveTitle(reminder)
                val body = resolveBody(reminder)
                dueItems.add(DueItem(reminder, compositeStage, title, body))
            }
        }

        if (dueItems.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()

        if (dueItems.size == 1) {
            // Notificación individual
            val item = dueItems.first()
            val shown = Notifications.show(
                context = applicationContext,
                id = item.reminder.id,
                title = item.title,
                body = item.body,
                vehicleId = item.reminder.vehicleId,
                sourceType = item.reminder.sourceType,
                sourceId = item.reminder.sourceId
            )
            if (shown) {
                reminderRepo.updateLastNotifiedStage(item.reminder.id, item.stage, now)
            }
        } else {
            // Notificación resumen (InboxStyle)
            val shown = Notifications.showSummary(
                context = applicationContext,
                items = dueItems.map {
                    Notifications.SummaryItem(
                        reminderId = it.reminder.id,
                        vehicleId = it.reminder.vehicleId,
                        sourceType = it.reminder.sourceType,
                        sourceId = it.reminder.sourceId,
                        title = it.title,
                        body = it.body
                    )
                }
            )
            if (shown) {
                dueItems.forEach { item ->
                    reminderRepo.updateLastNotifiedStage(item.reminder.id, item.stage, now)
                }
            }
        }

        return Result.success()
    }

    private fun resolveTitle(reminder: ReminderEntity): String =
        com.sierracode.autocontrol.domain.reminder.ReminderResolver
            .resolveTitle(applicationContext, reminder)

    private fun resolveBody(reminder: ReminderEntity): String =
        com.sierracode.autocontrol.domain.reminder.ReminderResolver
            .resolveBody(applicationContext, reminder)

    private fun computeDateStage(targetDate: LocalDate, today: LocalDate): String? {
        val daysUntil = ChronoUnit.DAYS.between(today, targetDate)
        return when {
            daysUntil <= 0 -> "date_0d"
            daysUntil <= 1 -> "date_1d"
            daysUntil <= 7 -> "date_7d"
            daysUntil <= 30 -> "date_30d"
            else -> null
        }
    }

    /**
     * Hitos fijos de km respetando el remindBeforeKm configurado en el schedule.
     * Si remindBeforeKm = 500 (default), evalúa los hitos 500 / 100 / 0.
     * Si remindBeforeKm = 200, solo evalúa 100 / 0 (respeta el aviso configurado).
     */
    private fun computeKmStage(targetOdo: Long, currentOdo: Long, remindBeforeKm: Long): String? {
        val remainingKm = targetOdo - currentOdo
        if (remainingKm <= 0) return "km_0"

        val hitos = listOf(500L, 100L)
            .filter { it <= remindBeforeKm.coerceAtLeast(0) }

        return hitos.firstOrNull { remainingKm <= it }?.let { "km_$it" }
    }
}