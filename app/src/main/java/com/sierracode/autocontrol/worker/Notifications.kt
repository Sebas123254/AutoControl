package com.sierracode.autocontrol.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sierracode.autocontrol.MainActivity

object Notifications {
    private const val CHANNEL_ID = "autocontrol_reminders"

    fun show(
        context: Context,
        id: String,
        title: String,
        body: String,
        vehicleId: String,
        sourceType: String,
        sourceId: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de AutoControl",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de mantenimiento y documentos"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("vehicleId", vehicleId)
            putExtra("sourceType", sourceType)
            putExtra("sourceId", sourceId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Si el usuario no dio permisos de notificación, falla silenciosamente
        }
    }
}