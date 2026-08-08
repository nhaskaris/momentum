package com.eliteonetube.momentum.logic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eliteonetube.momentum.MainActivity
import com.eliteonetube.momentum.R

object NotificationHelper {
    private const val ADJUSTMENT_CHANNEL_ID = "weekly_adjustment"
    private const val REMINDER_CHANNEL_ID = "daily_weighin_reminder"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ADJUSTMENT_CHANNEL_ID, "Weekly check-in", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Daily weigh-in reminder", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun showAdjustmentNotification(context: Context, adjustment: WeeklyAdjustment) {
        ensureChannels(context)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(context, "Notifications are disabled for this app.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ADJUSTMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your weekly check-in is ready")
            .setContentText(adjustment.reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(adjustment.reason))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(1001, notification)
        }
    }

    fun showWeighInReminder(context: Context) {
        ensureChannels(context)

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(context, "Notifications are disabled for this app.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("No weigh-in yet today")
            .setContentText("Log today's weight to keep your trend accurate.")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1002, notification)
    }
}