package com.example.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
import com.example.data.TaskCompletionOutcome
import com.example.data.TaskRepository
import com.example.data.local.PrismTaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return

        when (intent.action) {
            ReminderScheduler.ACTION_COMPLETE_TASK -> handleCompleteAction(context, taskId)
            ReminderScheduler.ACTION_SNOOZE_TASK -> handleSnoozeAction(context, taskId, intent)
            else -> showReminderNotification(context, taskId, intent)
        }
    }

    private fun showReminderNotification(
        context: Context,
        taskId: String,
        intent: Intent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val taskTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        val subtitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_SUBTITLE).orEmpty()
        val accentColor = intent.getIntExtra(
            ReminderScheduler.EXTRA_ACCENT_COLOR,
            0xFF0EA5E9.toInt()
        )

        val scheduler = ReminderScheduler(context)
        scheduler.ensureNotificationChannel()

        val notifId = ReminderScheduler.notificationIdFor(taskId)
        val nowMillis = System.currentTimeMillis()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            (taskId + "_open").hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_COMPLETE_TASK
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId + "_complete").hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_SNOOZE_TASK
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
            putExtra(ReminderScheduler.EXTRA_TASK_TITLE, taskTitle)
            putExtra(ReminderScheduler.EXTRA_TASK_SUBTITLE, subtitle)
            putExtra(ReminderScheduler.EXTRA_ACCENT_COLOR, accentColor)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reminderHeader = context.getString(R.string.notification_reminder_title)

        val childNotification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_check)
            .setColor(accentColor)
            .setContentTitle(reminderHeader)
            .setContentText(taskTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskTitle))
            .setWhen(nowMillis)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup(ReminderScheduler.GROUP_KEY_REMINDERS)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addAction(
                0,
                context.getString(R.string.notification_action_complete),
                completePendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_snooze),
                snoozePendingIntent
            )
            .build()

        val summaryNotification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_check)
            .setColor(accentColor)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(taskTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(ReminderScheduler.GROUP_KEY_REMINDERS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(notifId, childNotification)
            notificationManager.notify(ReminderScheduler.SUMMARY_NOTIFICATION_ID, summaryNotification)
        }
    }

    private fun handleCompleteAction(context: Context, taskId: String) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        ReminderScheduler.dismissNotification(appContext, taskId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = PrismTaskDatabase.getInstance(appContext).taskDao()
                val existing = dao.getTaskById(taskId)?.toDomain() ?: return@launch
                if (!existing.completed) {
                    val repository = TaskRepository(dao)
                    val scheduler = ReminderScheduler(appContext)
                    repository.toggleTaskCompletion(existing).onSuccess { outcome ->
                        when (outcome) {
                            is TaskCompletionOutcome.Repeated -> {
                                if (outcome.nextReminderMillis != null) {
                                    scheduler.scheduleReminder(
                                        taskId = existing.id,
                                        title = existing.title,
                                        subtitle = existing.notes,
                                        accentColorArgb = existing.taskColor.notificationArgb(),
                                        triggerAtMillis = outcome.nextReminderMillis
                                    )
                                } else {
                                    scheduler.cancelReminder(existing.id)
                                }
                            }
                            is TaskCompletionOutcome.Toggled -> {
                                if (outcome.completed) {
                                    scheduler.cancelReminder(existing.id)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to complete task $taskId from notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnoozeAction(context: Context, taskId: String, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        ReminderScheduler.dismissNotification(appContext, taskId)

        val fallbackTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE)
            ?: appContext.getString(R.string.app_name)
        val fallbackSubtitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_SUBTITLE).orEmpty()
        val fallbackAccent = intent.getIntExtra(ReminderScheduler.EXTRA_ACCENT_COLOR, 0xFF0EA5E9.toInt())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snoozeAtMillis = System.currentTimeMillis() + ReminderScheduler.SNOOZE_DURATION_MILLIS
                val dao = PrismTaskDatabase.getInstance(appContext).taskDao()
                val existing = dao.getTaskById(taskId)
                val scheduler = ReminderScheduler(appContext)

                if (existing != null && !existing.completed) {
                    dao.upsertTask(
                        existing.copy(
                            reminderMillis = snoozeAtMillis,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    val domain = existing.toDomain()
                    scheduler.scheduleReminder(
                        taskId = domain.id,
                        title = domain.title,
                        subtitle = domain.notes,
                        accentColorArgb = domain.taskColor.notificationArgb(),
                        triggerAtMillis = snoozeAtMillis
                    )
                } else {
                    scheduler.scheduleReminder(
                        taskId = taskId,
                        title = fallbackTitle,
                        subtitle = fallbackSubtitle,
                        accentColorArgb = fallbackAccent,
                        triggerAtMillis = snoozeAtMillis
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to snooze task $taskId from notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TaskReminderReceiver"
    }
}
