package com.sierracode.autocontrol.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sierracode.autocontrol.data.repository.DocumentRepository
import com.sierracode.autocontrol.data.repository.ReminderRepository
import com.sierracode.autocontrol.data.repository.ScheduleRepository
import com.sierracode.autocontrol.data.repository.VehicleRepository
import com.sierracode.autocontrol.domain.reminder.ReminderEvaluator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

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
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val reminders = reminderRepo.getAllOnce().filter { it.enabled }

        if (reminders.isEmpty()) return Result.success()

        // 1. Verificar si las notificaciones están habilitadas a nivel de sistema
        val notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!notificationsEnabled || !permissionGranted) {
            // No marcar nada, salir silenciosamente para reintentar cuando se conceda el permiso
            return Result.success()
        }

        data class DueItem(
            val reminder: com.sierracode.autocontrol.data.local.entity.ReminderEntity,
            val stage: String,
            val title: String,
            val body: String
        )

        val dueItems = mutableListOf<DueItem>()

        for (reminder in reminders) {
            // Respetar triggerAtEpochMillis (Snooze)
            if (reminder.triggerAtEpochMillis > now) continue

            val result = when (reminder.sourceType) {
                "DOCUMENT" -> {
                    val doc = documentRepo.get(reminder.sourceId) ?: continue
                    val expiry = doc.expiryDateEpochDay ?: continue
                    ReminderEvaluator.evaluateDocument(expiry, today, doc.reminderLeadDays)
                }
                "MAINTENANCE" -> {
                    val schedule = scheduleRepo.getById(reminder.sourceId) ?: continue
                    val vehicle = vehicleRepo.get(reminder.vehicleId) ?: continue
                    ReminderEvaluator.evaluateMaintenance(
                        todayEpochDay = today,
                        currentOdometer = vehicle.odometer,
                        lastDateEpochDay = schedule.lastDateEpochDay,
                        intervalMonths = schedule.intervalMonths,
                        remindBeforeDays = schedule.remindBeforeDays,
                        lastOdometer = schedule.lastOdometer,
                        intervalKm = schedule.intervalKm,
                        remindBeforeKm = schedule.remindBeforeKm
                    )
                }
                else -> continue
            }

            val currentStage = result.stage ?: continue

            if (currentStage != reminder.lastNotifiedStage) {
                val title = com.sierracode.autocontrol.domain.reminder.ReminderResolver.resolveTitle(applicationContext, reminder)
                val body = com.sierracode.autocontrol.domain.reminder.ReminderResolver.resolveBody(applicationContext, reminder)
                dueItems.add(DueItem(reminder, currentStage, title, body))
            }
        }

        if (dueItems.isEmpty()) return Result.success()

        if (dueItems.size == 1) {
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
}