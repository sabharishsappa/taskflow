package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.PrismAmber
import com.example.ui.theme.PrismAmberContainer
import com.example.ui.theme.PrismAmberContainerDark
import com.example.ui.theme.PrismAmberDark
import com.example.ui.theme.PrismBerry
import com.example.ui.theme.PrismBerryContainer
import com.example.ui.theme.PrismBerryContainerDark
import com.example.ui.theme.PrismBerryDark
import com.example.ui.theme.PrismCoral
import com.example.ui.theme.PrismCoralContainer
import com.example.ui.theme.PrismCoralContainerDark
import com.example.ui.theme.PrismCoralDark
import com.example.ui.theme.PrismSky
import com.example.ui.theme.PrismSkyContainer
import com.example.ui.theme.PrismSkyContainerDark
import com.example.ui.theme.PrismSkyDark
import com.example.ui.theme.PrismTeal
import com.example.ui.theme.PrismTealContainer
import com.example.ui.theme.PrismTealContainerDark
import com.example.ui.theme.PrismTealDark
import com.example.ui.theme.PrismViolet
import com.example.ui.theme.PrismVioletContainer
import com.example.ui.theme.PrismVioletContainerDark
import com.example.ui.theme.PrismVioletDark
import com.example.ui.theme.TaskAccentPalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TaskColor(val label: String) {
    CORAL("Coral"),
    TEAL("Mint"),
    AMBER("Sunny"),
    VIOLET("Violet"),
    SKY("Sky"),
    BERRY("Berry");

    fun palette(darkTheme: Boolean): TaskAccentPalette = when (this) {
        CORAL -> if (darkTheme) {
            TaskAccentPalette(PrismCoralDark, PrismCoralContainerDark, Color(0xFFFFD6D8), PrismCoralDark.copy(alpha = 0.45f))
        } else {
            TaskAccentPalette(PrismCoral, PrismCoralContainer, Color(0xFF7D1116), PrismCoral.copy(alpha = 0.35f))
        }
        TEAL -> if (darkTheme) {
            TaskAccentPalette(PrismTealDark, PrismTealContainerDark, Color(0xFFB5F9E9), PrismTealDark.copy(alpha = 0.45f))
        } else {
            TaskAccentPalette(PrismTeal, PrismTealContainer, Color(0xFF004D3E), PrismTeal.copy(alpha = 0.35f))
        }
        AMBER -> if (darkTheme) {
            TaskAccentPalette(PrismAmberDark, PrismAmberContainerDark, Color(0xFFFFEAB5), PrismAmberDark.copy(alpha = 0.45f))
        } else {
            TaskAccentPalette(PrismAmber, PrismAmberContainer, Color(0xFF6B4300), PrismAmber.copy(alpha = 0.35f))
        }
        VIOLET -> if (darkTheme) {
            TaskAccentPalette(PrismVioletDark, PrismVioletContainerDark, Color(0xFFE5E0FF), PrismVioletDark.copy(alpha = 0.45f))
        } else {
            TaskAccentPalette(PrismViolet, PrismVioletContainer, Color(0xFF28187A), PrismViolet.copy(alpha = 0.35f))
        }
        SKY -> if (darkTheme) {
            TaskAccentPalette(PrismSkyDark, PrismSkyContainerDark, Color(0xFFC9ECFF), PrismSkyDark.copy(alpha = 0.45f))
        } else {
            TaskAccentPalette(PrismSky, PrismSkyContainer, Color(0xFF004469), PrismSky.copy(alpha = 0.35f))
        }
        BERRY -> if (darkTheme) {
            TaskAccentPalette(PrismBerryDark, PrismBerryContainerDark, Color(0xFFFFD2EA), PrismBerryDark.copy(alpha = 0.45f))
        } else {
            TaskAccentPalette(PrismBerry, PrismBerryContainer, Color(0xFF751245), PrismBerry.copy(alpha = 0.35f))
        }
    }

    fun notificationArgb(): Int = when (this) {
        CORAL -> 0xFFFF5A5F.toInt()
        TEAL -> 0xFF00B894.toInt()
        AMBER -> 0xFFF59E0B.toInt()
        VIOLET -> 0xFF6C5CE7.toInt()
        SKY -> 0xFF0EA5E9.toInt()
        BERRY -> 0xFFEC4899.toInt()
    }

    companion object {
        fun fromKey(raw: String?): TaskColor =
            entries.firstOrNull { it.name == raw } ?: CORAL
    }
}

enum class RepeatRule(val label: String, val unitSingular: String, val unitPlural: String) {
    NONE("No repeat", "", ""),
    DAILY("Daily", "day", "days"),
    WEEKDAYS("Weekdays", "weekday", "weekdays"),
    WEEKLY("Weekly", "week", "weeks"),
    MONTHLY("Monthly", "month", "months");

    fun formatBadge(interval: Int): String {
        val safeInterval = interval.coerceIn(1, 365)
        return when (this) {
            NONE -> ""
            WEEKDAYS -> "Mon–Fri"
            DAILY -> if (safeInterval == 1) "Daily" else "Every $safeInterval days"
            WEEKLY -> if (safeInterval == 1) "Weekly" else "Every $safeInterval wks"
            MONTHLY -> if (safeInterval == 1) "Monthly" else "Every $safeInterval mos"
        }
    }

    companion object {
        fun fromKey(raw: String?): RepeatRule =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class TaskSectionTab {
    TIMELINE,
    OTHER_TASKS
}

enum class DateGroupStyle {
    OVERDUE,
    TODAY,
    TOMORROW,
    UPCOMING,
    NO_DATE
}

data class TaskDateGroup(
    val key: String,
    val dayStartMillis: Long?,
    val title: String,
    val subtitle: String?,
    val dayOfMonth: String?,
    val dayOfWeekShort: String?,
    val style: DateGroupStyle,
    val tasks: List<TaskItem>
)

data class SubtaskItem(
    val id: String = "",
    val title: String = "",
    val done: Boolean = false
)

data class TaskItem(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val completed: Boolean = false,
    val colorKey: String = TaskColor.CORAL.name,
    val dueDateMillis: Long? = null,
    val reminderMillis: Long? = null,
    val repeatRule: String = RepeatRule.NONE.name,
    val repeatInterval: Long = 1L,
    val subtasks: List<SubtaskItem> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val taskColor: TaskColor
        get() = TaskColor.fromKey(colorKey)

    val repeatRuleEnum: RepeatRule
        get() = RepeatRule.fromKey(repeatRule)

    val repeatIntervalInt: Int
        get() = repeatInterval.toInt().coerceIn(1, 365)

    val completedSubtasksCount: Int
        get() = subtasks.count { it.done }

    val hasSubtasks: Boolean
        get() = subtasks.isNotEmpty()
}

sealed interface TaskCompletionOutcome {
    data class Toggled(val completed: Boolean) : TaskCompletionOutcome
    data class Repeated(
        val nextDueDateMillis: Long,
        val nextReminderMillis: Long?
    ) : TaskCompletionOutcome
}

object TaskDateUtils {
    fun startOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun daysFromTodayMillis(daysOffset: Int): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysOffset)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun combineDateAndTime(dateMillis: Long, hourOfDay: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Converts a UTC midnight date picker selection to local midday so timezone offsets never shift the date.
     */
    fun utcDatePickerMillisToLocalMidday(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        val localCal = Calendar.getInstance().apply {
            clear()
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                12,
                0,
                0
            )
        }
        return localCal.timeInMillis
    }

    fun localMillisToUtcMidnight(localMillis: Long): Long {
        val localCal = Calendar.getInstance().apply {
            timeInMillis = localMillis
        }
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
        }
        return utcCal.timeInMillis
    }

    fun isDueToday(dueDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (dueDateMillis == null) return false
        return dueDateMillis in startOfDayMillis(nowMillis)..endOfDayMillis(nowMillis)
    }

    fun isOverdue(dueDateMillis: Long?, completed: Boolean, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (dueDateMillis == null || completed) return false
        return dueDateMillis < startOfDayMillis(nowMillis)
    }

    fun isUpcoming(dueDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (dueDateMillis == null) return false
        return dueDateMillis > endOfDayMillis(nowMillis)
    }

    fun computeNextOccurrence(
        baseMillis: Long,
        rule: RepeatRule,
        interval: Int,
        minMillis: Long
    ): Long {
        val safeInterval = interval.coerceIn(1, 365)
        val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }
        var guard = 0
        do {
            when (rule) {
                RepeatRule.NONE -> return baseMillis
                RepeatRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, safeInterval)
                RepeatRule.WEEKDAYS -> {
                    do {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        val dow = cal.get(Calendar.DAY_OF_WEEK)
                    } while (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)
                }
                RepeatRule.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, safeInterval)
                RepeatRule.MONTHLY -> cal.add(Calendar.MONTH, safeInterval)
            }
            guard++
        } while (cal.timeInMillis <= minMillis && guard < 400)
        return cal.timeInMillis
    }

    fun formatDueDate(dueDateMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val todayStart = startOfDayMillis(nowMillis)
        val tomorrowStart = todayStart + 86_400_000L
        val tomorrowEnd = tomorrowStart + 86_399_999L
        val yesterdayStart = todayStart - 86_400_000L

        return when (dueDateMillis) {
            in todayStart..endOfDayMillis(nowMillis) -> "Today"
            in tomorrowStart..tomorrowEnd -> "Tomorrow"
            in yesterdayStart until todayStart -> "Yesterday"
            else -> {
                val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val targetCal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
                val pattern = if (nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)) {
                    "EEE, MMM d"
                } else {
                    "MMM d, yyyy"
                }
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date(dueDateMillis))
            }
        }
    }

    fun formatTimeOnly(timeMillis: Long, use24Hour: Boolean = false): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMillis))
    }

    fun formatReminder(
        reminderMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        use24Hour: Boolean = false
    ): String {
        val timeText = formatTimeOnly(reminderMillis, use24Hour)
        val dayText = formatDueDate(reminderMillis, nowMillis)
        return "$dayText · $timeText"
    }

    fun effectiveDateMillis(task: TaskItem): Long? {
        return task.dueDateMillis ?: task.reminderMillis
    }

    fun hasTimeline(task: TaskItem): Boolean {
        return effectiveDateMillis(task) != null
    }

    fun sortTasksChronologically(tasks: List<TaskItem>): List<TaskItem> {
        return tasks.sortedWith(
            compareBy<TaskItem> { task ->
                effectiveDateMillis(task)?.let { startOfDayMillis(it) } ?: Long.MAX_VALUE
            }
                .thenBy { it.completed }
                .thenBy { it.reminderMillis ?: Long.MAX_VALUE }
                .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
                .thenBy { it.createdAtMillis }
        )
    }

    fun groupTasksByDate(
        tasks: List<TaskItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TaskDateGroup> {
        if (tasks.isEmpty()) return emptyList()

        val todayStart = startOfDayMillis(nowMillis)
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val yesterdayStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val nowYear = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.YEAR)

        val groupedMap = linkedMapOf<Long?, MutableList<TaskItem>>()
        for (task in tasks) {
            val dayKey = effectiveDateMillis(task)?.let { startOfDayMillis(it) }
            groupedMap.getOrPut(dayKey) { mutableListOf() }.add(task)
        }

        val sortedKeys = groupedMap.keys.sortedWith(
            compareBy<Long?> { it ?: Long.MAX_VALUE }
        )

        return sortedKeys.map { dayStart ->
            val groupTasks = groupedMap[dayStart].orEmpty().sortedWith(
                compareBy<TaskItem> { it.completed }
                    .thenBy { it.reminderMillis ?: Long.MAX_VALUE }
                    .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
                    .thenBy { it.createdAtMillis }
            )

            if (dayStart == null) {
                TaskDateGroup(
                    key = "no_date",
                    dayStartMillis = null,
                    title = "No Date",
                    subtitle = "Unscheduled",
                    dayOfMonth = null,
                    dayOfWeekShort = null,
                    style = DateGroupStyle.NO_DATE,
                    tasks = groupTasks
                )
            } else {
                val targetDate = Date(dayStart)
                val targetYear = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.YEAR)
                val fullPattern = if (targetYear == nowYear) "MMMM d" else "MMM d, yyyy"
                val weekdayDatePattern = if (targetYear == nowYear) "EEEE, MMM d" else "EEE, MMM d, yyyy"
                val monthDayText = SimpleDateFormat(fullPattern, Locale.getDefault()).format(targetDate)
                val weekdayDateText = SimpleDateFormat(weekdayDatePattern, Locale.getDefault()).format(targetDate)
                val dayNumText = SimpleDateFormat("d", Locale.getDefault()).format(targetDate)
                val dowShortText = SimpleDateFormat("EEE", Locale.getDefault()).format(targetDate).uppercase(Locale.getDefault())
                val hasActiveTasks = groupTasks.any { !it.completed }

                when {
                    dayStart == todayStart -> TaskDateGroup(
                        key = "day_$dayStart",
                        dayStartMillis = dayStart,
                        title = "Today",
                        subtitle = monthDayText,
                        dayOfMonth = dayNumText,
                        dayOfWeekShort = dowShortText,
                        style = DateGroupStyle.TODAY,
                        tasks = groupTasks
                    )
                    dayStart == tomorrowStart -> TaskDateGroup(
                        key = "day_$dayStart",
                        dayStartMillis = dayStart,
                        title = "Tomorrow",
                        subtitle = monthDayText,
                        dayOfMonth = dayNumText,
                        dayOfWeekShort = dowShortText,
                        style = DateGroupStyle.TOMORROW,
                        tasks = groupTasks
                    )
                    dayStart == yesterdayStart -> TaskDateGroup(
                        key = "day_$dayStart",
                        dayStartMillis = dayStart,
                        title = "Yesterday",
                        subtitle = if (hasActiveTasks) "Overdue · $monthDayText" else monthDayText,
                        dayOfMonth = dayNumText,
                        dayOfWeekShort = dowShortText,
                        style = if (hasActiveTasks) DateGroupStyle.OVERDUE else DateGroupStyle.UPCOMING,
                        tasks = groupTasks
                    )
                    dayStart < todayStart -> TaskDateGroup(
                        key = "day_$dayStart",
                        dayStartMillis = dayStart,
                        title = weekdayDateText,
                        subtitle = if (hasActiveTasks) "Overdue" else "Completed earlier",
                        dayOfMonth = dayNumText,
                        dayOfWeekShort = dowShortText,
                        style = if (hasActiveTasks) DateGroupStyle.OVERDUE else DateGroupStyle.UPCOMING,
                        tasks = groupTasks
                    )
                    else -> TaskDateGroup(
                        key = "day_$dayStart",
                        dayStartMillis = dayStart,
                        title = weekdayDateText,
                        subtitle = null,
                        dayOfMonth = dayNumText,
                        dayOfWeekShort = dowShortText,
                        style = DateGroupStyle.UPCOMING,
                        tasks = groupTasks
                    )
                }
            }
        }
    }
}
