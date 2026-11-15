package com.sierracode.autocontrol.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.sierracode.autocontrol.data.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe acciones de las notificaciones (snooze) sin abrir la app.
 * Usa `goAsync()` para poder hacer trabajo de I/O (Room + WorkManager)
 * sin bloquear el hilo principal del sistema.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepo: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

        val delayMillis = when (action) {
            ACTION_SNOOZE_1H -> 1L * 60L * 60L * 1000L
            ACTION_SNOOZE_1D -> 24L * 60L * 60L * 1000L
            else -> return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val reminder = reminderRepo.getAllOnce().find { it.id == reminderId } ?: return@launch
                val snoozeUntil = System.currentTimeMillis() + delayMillis

                reminderRepo.upsert(
                    reminder.copy(
                        triggerAtEpochMillis = snoozeUntil,
                        lastNotifiedAt = null
                    )
                )

                // Cerrar la notificación original (ya la estamos "consumiendo").
                NotificationManagerCompat.from(context).cancel(reminderId.hashCode())

                // Reprogramar un worker one-shot para que dispare exactamente
                // a la hora del snooze (el worker diario correría hasta mañana).
                ReminderScheduler.scheduleSnooze(context, reminderId, delayMillis)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_1H = "com.sierracode.autocontrol.SNOOZE_1H"
        const val ACTION_SNOOZE_1D = "com.sierracode.autocontrol.SNOOZE_1D"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}