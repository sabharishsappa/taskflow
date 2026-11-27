package com.example.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DateFormatters {

    fun formatFullDate(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun formatShortDate(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun formatCompactDate(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun formatDateTime(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun dayKey(timestampMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun formatDateGroupHeader(dayStartMillis: Long): String {
        val todayStart = dayKey(System.currentTimeMillis())
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
        val shortDate = formatShortDate(dayStartMillis)
        return when {
            dayStartMillis == todayStart -> "Today · $shortDate"
            kotlin.math.abs(dayStartMillis - yesterdayStart) <= TimeUnit.HOURS.toMillis(2) ->
                "Yesterday · $shortDate"
            else -> {
                val formatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                formatter.format(Date(dayStartMillis))
            }
        }
    }

    /**
     * Converts a local timestamp to UTC midnight millis expected by Material 3 DatePickerState.
     */
    fun localMillisToUtcPickerMillis(localMillis: Long): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
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

    /**
     * Converts UTC midnight millis from Material 3 DatePickerState back to local midday millis
     * so timezone offsets never shift the displayed calendar date.
     */
    fun utcPickerMillisToLocalMiddayMillis(utcMillis: Long): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
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

    fun todayMiddayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun addDays(timestampMillis: Long, deltaDays: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            add(Calendar.DAY_OF_YEAR, deltaDays)
        }
        return cal.timeInMillis
    }

    fun addDaysToDayKey(dayKeyMillis: Long, deltaDays: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayKeyMillis
            add(Calendar.DAY_OF_YEAR, deltaDays)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun addMonths(timestampMillis: Long, deltaMonths: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            add(Calendar.MONTH, deltaMonths)
        }
        return cal.timeInMillis
    }

    fun dayOfWeek(timestampMillis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    fun shortWeekdayLabel(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("EEE", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun dayOfMonthNumber(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("d", Locale.getDefault())
        return formatter.format(Date(timestampMillis))
    }

    fun recentDayKeys(daysCount: Int, anchorDayKey: Long = dayKey(System.currentTimeMillis())): List<Long> {
        return ((daysCount - 1) downTo 0).map { offset ->
            addDaysToDayKey(anchorDayKey, -offset)
        }
    }

    fun formatRelativeDueDate(dueMillis: Long): String {
        val todayKey = dayKey(System.currentTimeMillis())
        val dueKey = dayKey(dueMillis)
        val tomorrowKey = addDaysToDayKey(todayKey, 1)
        val yesterdayKey = addDaysToDayKey(todayKey, -1)
        return when (dueKey) {
            todayKey -> "Today"
            tomorrowKey -> "Tomorrow"
            yesterdayKey -> "Yesterday"
            else -> formatCompactDate(dueMillis)
        }
    }
}
