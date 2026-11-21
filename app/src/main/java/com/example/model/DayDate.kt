package com.example.model

import java.util.Calendar
import java.util.Locale

/**
 * MinSdk-24-safe, timezone-stable local calendar date representation.
 * Uses Howard Hinnant's civil-from-days / days-from-civil integer algorithms
 * so epochDay values match java.time.LocalDate.toEpochDay() with zero DST drift.
 */
data class DayDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int,   // 1..31
) : Comparable<DayDate> {

    fun toEpochDay(): Long {
        val y = year.toLong() - if (month <= 2) 1L else 0L
        val era = (if (y >= 0) y else y - 399L) / 400L
        val yoe = y - era * 400L
        val m = month.toLong()
        val doy = (153L * (m + if (m > 2) -3L else 9L) + 2L) / 5L + day.toLong() - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        return era * 146097L + doe - 719468L
    }

    fun plusDays(days: Int): DayDate = fromEpochDay(toEpochDay() + days.toLong())

    fun plusMonths(monthsToAdd: Int): DayDate {
        val totalMonths = (year * 12 + (month - 1)) + monthsToAdd
        val newYear = Math.floorDiv(totalMonths, 12)
        val newMonth = Math.floorMod(totalMonths, 12) + 1
        val maxDay = daysInMonth(newYear, newMonth)
        return DayDate(newYear, newMonth, day.coerceAtMost(maxDay))
    }

    /** 1 = Monday .. 7 = Sunday */
    fun dayOfWeekIso(): Int {
        // 1970-01-01 (epochDay 0) was Thursday (4)
        val mod = Math.floorMod(toEpochDay() + 3L, 7L).toInt()
        return mod + 1
    }

    /** 0 = Sunday .. 6 = Saturday */
    fun dayOfWeekSunStart(): Int {
        val iso = dayOfWeekIso()
        return if (iso == 7) 0 else iso
    }

    fun firstOfMonth(): DayDate = DayDate(year, month, 1)

    fun lengthOfMonth(): Int = daysInMonth(year, month)

    fun shortWeekday(): String = SHORT_WEEKDAYS[dayOfWeekSunStart()]

    fun fullWeekday(): String = FULL_WEEKDAYS[dayOfWeekSunStart()]

    fun shortMonth(): String = SHORT_MONTHS[month - 1]

    fun fullMonth(): String = FULL_MONTHS[month - 1]

    fun formatMonthYear(): String = "${fullMonth()} $year"

    fun formatShortDate(): String = "${shortMonth()} $day"

    fun formatFullDate(includeYearIfDifferentFrom: Int = today().year): String {
        val base = "${shortWeekday()}, ${shortMonth()} $day"
        return if (year != includeYearIfDifferentFrom) "$base, $year" else base
    }

    fun formatRelativeHeader(today: DayDate = today()): String {
        val diff = toEpochDay() - today.toEpochDay()
        return when (diff) {
            0L -> "Today · ${shortWeekday()}, ${shortMonth()} $day"
            1L -> "Tomorrow · ${shortWeekday()}, ${shortMonth()} $day"
            -1L -> "Yesterday · ${shortWeekday()}, ${shortMonth()} $day"
            else -> formatFullDate(today.year)
        }
    }

    fun toLocalMillis(hour24: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour24, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun toUtcMidnightMillis(): Long = toEpochDay() * MILLIS_PER_DAY

    override fun compareTo(other: DayDate): Int = toEpochDay().compareTo(other.toEpochDay())

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L

        private val SHORT_WEEKDAYS = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        private val FULL_WEEKDAYS = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        private val SHORT_MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        private val FULL_MONTHS = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        fun today(): DayDate {
            val cal = Calendar.getInstance()
            return DayDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun fromEpochDay(epochDay: Long): DayDate {
            val z = epochDay + 719468L
            val era = (if (z >= 0) z else z - 146096L) / 146097L
            val doe = z - era * 146097L
            val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
            val y = yoe + era * 400L
            val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
            val mp = (5L * doy + 2L) / 153L
            val d = doy - (153L * mp + 2L) / 5L + 1L
            val m = mp + if (mp < 10L) 3L else -9L
            val finalYear = y + if (m <= 2L) 1L else 0L
            return DayDate(finalYear.toInt(), m.toInt(), d.toInt())
        }

        fun fromUtcMidnightMillis(utcMillis: Long): DayDate {
            return fromEpochDay(Math.floorDiv(utcMillis, MILLIS_PER_DAY))
        }

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            else -> 30
        }

        fun formatTimeFromMillis(epochMillis: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            val hour24 = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            return formatTime(hour24, minute)
        }

        fun formatTime(hour24: Int, minute: Int): String {
            val amPm = if (hour24 < 12) "AM" else "PM"
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }

        fun extractHourMinute(epochMillis: Long): Pair<Int, Int> {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
        }

        fun extractDayDate(epochMillis: Long): DayDate {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            return DayDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
