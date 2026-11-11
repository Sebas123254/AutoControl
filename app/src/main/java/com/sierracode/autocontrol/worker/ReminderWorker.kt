package com.sierracode.autocontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sierracode.autocontrol.data.local.dao.ReminderDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminders: ReminderDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        // Calculamos la hora de inicio del día de hoy (00:00 hrs)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        // Buscamos los recordatorios que ya deberían sonar y que no se hayan notificado hoy
        val dueReminders = reminders.getDueReminders(now, startOfDay)

        for (reminder in dueReminders) {
            // Mostrar la notificación en el celular
            Notifications.show(applicationContext, reminder.id, reminder.title, reminder.body)

            // Marcarlo como notificado para que no vuelva a sonar hoy
            reminders.markNotified(reminder.id, now)
        }

        return Result.success()
    }
}