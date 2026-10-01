package com.openfit.mobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openfit.mobile.MainActivity
import com.openfit.mobile.R

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MORNING, "Morning sleep summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily AI summary of how you slept, delivered each morning."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENING, "Evening activity summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily AI summary of your activity, delivered each evening."
            },
        )
    }

    fun showMorningSummary(title: String, body: String) = show(NOTIFICATION_ID_MORNING, CHANNEL_MORNING, title, body)

    fun showEveningSummary(title: String, body: String) = show(NOTIFICATION_ID_EVENING, CHANNEL_EVENING, title, body)

    private fun show(id: Int, channel: String, title: String, body: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAVIGATE_TO_COACH)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, id, openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return // POST_NOTIFICATIONS not granted; the worker still ran and data is fresh in-app.
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val NAVIGATE_TO_COACH = "coach"
        private const val CHANNEL_MORNING = "morning_sleep_summary"
        private const val CHANNEL_EVENING = "evening_activity_summary"
        private const val NOTIFICATION_ID_MORNING = 1001
        private const val NOTIFICATION_ID_EVENING = 1002
    }
}
