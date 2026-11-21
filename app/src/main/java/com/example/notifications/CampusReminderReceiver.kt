package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CampusReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(CampusNotificationManager.EXTRA_TITLE)
            ?: "CampusBot Reminder"
        val body = intent.getStringExtra(CampusNotificationManager.EXTRA_BODY)
            ?: "You have an upcoming class or exam deadline."
        val notificationId = intent.getIntExtra(
            CampusNotificationManager.EXTRA_NOTIFICATION_ID,
            System.currentTimeMillis().toInt()
        )
        CampusNotificationManager.showScheduleReminderNow(
            context = context,
            notificationId = notificationId,
            title = title,
            body = body
        )
    }
}
