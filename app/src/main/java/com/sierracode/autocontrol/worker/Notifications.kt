package com.sierracode.autocontrol.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sierracode.autocontrol.MainActivity
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.data.local.entity.ReminderEntity
import com.sierracode.autocontrol.domain.reminder.ReminderResolver

object Notifications {
    private const val CHANNEL_MAINTENANCE = "autocontrol_maintenance_v1"
    private const val CHANNEL_DOCUMENTS = "autocontrol_documents_v1"
    private const val GROUP_KEY = "autocontrol_reminders_group"
    private const val SUMMARY_ID = "summary"

    data class SummaryItem(
        val reminderId: String,
        val vehicleId: String,
        val sourceType: String,
        val sourceId: String,
        val title: String,
        val body: String
    )

    /**
     * Publica la notificación de un [ReminderEntity] resolviendo título y
     * cuerpo con el locale del dispositivo.
     */
    fun show(context: Context, reminder: ReminderEntity): Boolean {
        val title = ReminderResolver.resolveTitle(context, reminder)
        val body = ReminderResolver.resolveBody(context, reminder)
        return show(
            context = context,
            id = reminder.id,
            title = title,
            body = body,
            vehicleId = reminder.vehicleId,
            sourceType = reminder.sourceType,
            sourceId = reminder.sourceId
        )
    }

    fun show(
        context: Context,
        id: String,
        title: String,
        body: String,
        vehicleId: String,
        sourceType: String,
        sourceId: String
    ): Boolean {
        val channelId = channelFor(sourceType)
        ensureChannel(context, channelId)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("vehicleId", vehicleId)
            putExtra("sourceType", sourceType)
            putExtra("sourceId", sourceId)
        }
        val requestCode = id.hashCode()
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            ("snooze1h_$id").hashCode(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SNOOZE_1H
                putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1dPendingIntent = PendingIntent.getBroadcast(
            context,
            ("snooze1d_$id").hashCode(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SNOOZE_1D
                putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(false)
            .addAction(
                NotificationCompat.Action.Builder(
                    0, context.getString(R.string.notif_action_view), contentPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0, context.getString(R.string.notif_action_snooze_1h), snooze1hPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0, context.getString(R.string.notif_action_snooze_1d), snooze1dPendingIntent
                ).build()
            )
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Notificación resumen (InboxStyle) que agrupa varios recordatorios del día
     * para no inundar al usuario con una notificación por cada uno.
     */
    fun showSummary(context: Context, items: List<SummaryItem>): Boolean {
        if (items.isEmpty()) return false

        // Elegimos el canal del primer item (los que no tienen clasificación van a maintenance).
        val channelId = channelFor(items.first().sourceType)
        ensureChannel(context, channelId)

        val title = context.getString(R.string.notif_summary_title, items.size)
        val summaryText = context.getString(R.string.notif_summary_body, items.size)

        // Intent al primer vehículo (más accionable que ir a la lista vacía).
        val firstVehicleId = items.first().vehicleId
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("vehicleId", firstVehicleId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_ID.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(summaryText)
        items.take(5).forEach { item ->
            inboxStyle.addLine("• ${item.title}")
        }
        if (items.size > 5) {
            inboxStyle.addLine(context.getString(R.string.notif_summary_more, items.size - 5))
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setStyle(inboxStyle)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(SUMMARY_ID.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun channelFor(sourceType: String): String = when (sourceType) {
        "DOCUMENT" -> CHANNEL_DOCUMENTS
        else -> CHANNEL_MAINTENANCE
    }

    private fun ensureChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val (name, description) = when (channelId) {
            CHANNEL_DOCUMENTS -> context.getString(R.string.notif_channel_documents) to
                    context.getString(R.string.notif_channel_documents_desc)
            else -> context.getString(R.string.notif_channel_maintenance) to
                    context.getString(R.string.notif_channel_maintenance_desc)
        }

        val channel = NotificationChannel(
            channelId,
            name,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            this.description = description
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}