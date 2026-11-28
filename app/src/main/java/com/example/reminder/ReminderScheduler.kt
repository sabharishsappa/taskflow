package com.example.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.data.TaskItem

class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.reminder_channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(
        taskId: String,
        title: String,
        subtitle: String,
        accentColorArgb: Int,
        triggerAtMillis: Long
    ) {
        if (taskId.isBlank()) return
        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancelReminder(taskId)
            return
        }
        val pendingIntent = buildTriggerPendingIntent(
            taskId = taskId,
            title = title,
            subtitle = subtitle,
            accentColorArgb = accentColorArgb
        )
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to schedule reminder alarm for $taskId: ${e.message}")
        }
    }

    fun cancelReminder(taskId: String) {
        if (taskId.isBlank()) return
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            notificationIdFor(taskId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        dismissNotification(appContext, taskId)
    }

    fun syncReminders(tasks: List<TaskItem>) {
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            val reminderAt = task.reminderMillis
            if (!task.completed && reminderAt != null && reminderAt > now) {
                val subtitle = buildString {
                    if (task.notes.isNotBlank()) {
                        append(task.notes.take(80))
                    } else if (task.hasSubtasks) {
                        append("${task.completedSubtasksCount}/${task.subtasks.size} subtasks done")
                    } else {
                        append("")
                    }
                }
                scheduleReminder(
                    taskId = task.id,
                    title = task.title,
                    subtitle = subtitle,
                    accentColorArgb = task.taskColor.notificationArgb(),
                    triggerAtMillis = reminderAt
                )
            } else if (task.completed || reminderAt == null) {
                cancelReminder(task.id)
            }
        }
    }

    private fun buildTriggerPendingIntent(
        taskId: String,
        title: String,
        subtitle: String,
        accentColorArgb: Int
    ): PendingIntent {
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_SUBTITLE, subtitle)
            putExtra(EXTRA_ACCENT_COLOR, accentColorArgb)
        }
        return PendingIntent.getBroadcast(
            appContext,
            notificationIdFor(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ReminderScheduler"
        const val CHANNEL_ID = "prismtask_reminders"
        const val GROUP_KEY_REMINDERS = "com.example.prismtask.REMINDERS_GROUP"
        const val SUMMARY_NOTIFICATION_ID = 900_001

        const val ACTION_TRIGGER_REMINDER = "com.example.prismtask.action.TRIGGER_REMINDER"
        const val ACTION_COMPLETE_TASK = "com.example.prismtask.action.COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.example.prismtask.action.SNOOZE_TASK"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_SUBTITLE = "extra_task_subtitle"
        const val EXTRA_ACCENT_COLOR = "extra_accent_color"

        const val SNOOZE_DURATION_MILLIS = 15L * 60L * 1000L

        fun notificationIdFor(taskId: String): Int {
            val hash = taskId.hashCode()
            return if (hash == SUMMARY_NOTIFICATION_ID || hash == 0) hash + 17 else hash
        }

        fun dismissNotification(context: Context, taskId: String) {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(notificationIdFor(taskId))

            val sysManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val remainingChildren = sysManager?.activeNotifications?.count { statusBarNotification ->
                statusBarNotification.id != SUMMARY_NOTIFICATION_ID &&
                    statusBarNotification.notification.group == GROUP_KEY_REMINDERS
            } ?: 0
            if (remainingChildren == 0) {
                notificationManager.cancel(SUMMARY_NOTIFICATION_ID)
            }
        }
    }
}
