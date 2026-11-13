package com.sierracode.autocontrol.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sierracode.autocontrol.data.repository.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepo: ReminderRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        val dueReminders = reminderRepo.getDueReminders(now, startOfDay)

        for (reminder in dueReminders) {
            Notifications.show(
                context = applicationContext,
                id = reminder.id,
                title = reminder.title,
                body = reminder.body,
                vehicleId = reminder.vehicleId,
                sourceType = reminder.sourceType,
                sourceId = reminder.sourceId
            )
            reminderRepo.markNotified(reminder.id, now)
        }

        return Result.success()
    }
}