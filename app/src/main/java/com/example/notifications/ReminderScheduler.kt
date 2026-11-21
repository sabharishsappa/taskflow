package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.model.TaskItem
import com.example.model.TaskList

class ReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    init {
        TaskReminderReceiver.ensureNotificationChannel(appContext)
    }

    fun syncReminders(tasks: List<TaskItem>, listsById: Map<String, TaskList>) {
        val now = System.currentTimeMillis()
        for (task in tasks) {
            val reminderAt = task.reminderEpochMillis
            if (!task.completed && reminderAt != null && reminderAt > now) {
                val listName = listsById[task.listId]?.name ?: "Personal"
                scheduleReminder(
                    taskId = task.id,
                    title = task.title,
                    listName = listName,
                    triggerAtMillis = reminderAt
                )
            } else {
                cancelReminder(task.id)
            }
        }
    }

    fun scheduleReminder(
        taskId: String,
        title: String,
        listName: String,
        triggerAtMillis: Long,
    ) {
        if (taskId.isBlank() || triggerAtMillis <= System.currentTimeMillis()) return
        val manager = alarmManager ?: return

        val pendingIntent = buildPendingIntent(taskId, title, listName)
        try {
            val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                manager.canScheduleExactAlarms()
            if (canUseExact) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Falling back to inexact alarm for reminder", e)
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule reminder for $taskId", e)
        }
    }

    fun cancelReminder(taskId: String) {
        if (taskId.isBlank()) return
        val manager = alarmManager ?: return
        val intent = Intent(appContext, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun buildPendingIntent(
        taskId: String,
        title: String,
        listName: String,
    ): PendingIntent {
        val intent = Intent(appContext, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_LIST_NAME, listName)
        }
        return PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ReminderScheduler"
    }
}
