package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AnnouncementSeverity
import com.example.data.model.CampusAnnouncement
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType

object CampusNotificationManager {
    const val CHANNEL_URGENT_ALERTS = "campus_urgent_alerts"
    const val CHANNEL_SCHEDULE_REMINDERS = "campus_schedule_reminders"

    const val EXTRA_TITLE = "extra_reminder_title"
    const val EXTRA_BODY = "extra_reminder_body"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    private const val TAG = "CampusNotifications"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertsChannel = NotificationChannel(
                CHANNEL_URGENT_ALERTS,
                "Campus Urgent Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time campus announcements and urgent safety/operations alerts."
                enableVibration(true)
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_SCHEDULE_REMINDERS,
                "Class & Exam Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled reminders for upcoming classes and exam deadlines."
                enableVibration(true)
            }

            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(remindersChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showAnnouncementNotification(context: Context, announcement: CampusAnnouncement) {
        if (!hasNotificationPermission(context)) {
            Log.d(TAG, "Skipping system notification: POST_NOTIFICATIONS not granted.")
            return
        }
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            announcement.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = if (announcement.parsedSeverity == AnnouncementSeverity.URGENT) {
            "[URGENT] "
        } else {
            ""
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$prefix${announcement.title}")
            .setContentText("${announcement.department} · ${announcement.body}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${announcement.department}\n${announcement.body}")
            )
            .setPriority(
                if (announcement.parsedSeverity == AnnouncementSeverity.URGENT) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(announcement.id.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied while posting announcement", e)
        }
    }

    fun showScheduleReminderNow(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SCHEDULE_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied while posting schedule reminder", e)
        }
    }

    fun syncScheduleReminder(context: Context, item: ScheduleItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = ("schedule_${item.id}").hashCode()
        val intent = Intent(context, CampusReminderReceiver::class.java).apply {
            val typeTag = if (item.parsedType == ScheduleItemType.EXAM) "Exam Reminder" else "Class Starting Soon"
            val roomSuffix = if (item.location.isNotBlank()) " · ${item.location}" else ""
            putExtra(EXTRA_TITLE, "$typeTag: ${item.courseCode}")
            putExtra(
                EXTRA_BODY,
                "${item.title} begins in ${item.reminderLeadMinutes} min$roomSuffix"
            )
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!item.reminderEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val now = System.currentTimeMillis()
        val eventMillis = ScheduleFormatter.nextOccurrenceMillis(item, now)
        val triggerMillis = eventMillis - (item.reminderLeadMinutes.toLong() * 60_000L)
        if (triggerMillis <= now) {
            alarmManager.cancel(pendingIntent)
            return
        }

        try {
            // Policy-compliant inexact wake alarm (requires no restricted SCHEDULE_EXACT_ALARM)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule local reminder for ${item.id}", e)
        }
    }

    fun cancelScheduleReminder(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = ("schedule_$itemId").hashCode()
        val intent = Intent(context, CampusReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
