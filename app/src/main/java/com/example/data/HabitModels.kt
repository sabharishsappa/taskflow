package com.example.data

import java.util.Calendar

enum class HabitFrequency(val label: String) {
    DAILY("Every day"),
    SPECIFIC_DAYS("Specific days"),
    WEEKLY_TARGET("Times per week");

    companion object {
        fun fromName(raw: String?): HabitFrequency =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DAILY
    }
}

enum class HabitAccent(val key: String, val label: String) {
    SAGE("sage", "Sage"),
    TERRACOTTA("terracotta", "Terracotta"),
    BRASS("brass", "Brass"),
    CRIMSON("crimson", "Berry"),
    SLATE("slate", "Slate");

    companion object {
        fun fromKey(raw: String?): HabitAccent =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SAGE
    }
}

data class HabitItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "Health",
    val accent: HabitAccent = HabitAccent.SAGE,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    /** Calendar.SUNDAY (1) .. Calendar.SATURDAY (7) */
    val scheduledDays: Set<Int> = ALL_WEEKDAYS,
    val weeklyTarget: Int = 5,
    val dailyGoalCount: Int = 1,
    val unitLabel: String = "",
    val isArchived: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun isScheduledOnWeekday(calendarDayOfWeek: Int): Boolean {
        return when (frequency) {
            HabitFrequency.DAILY -> true
            HabitFrequency.WEEKLY_TARGET -> true
            HabitFrequency.SPECIFIC_DAYS -> scheduledDays.isEmpty() || calendarDayOfWeek in scheduledDays
        }
    }

    fun scheduleSummary(): String {
        return when (frequency) {
            HabitFrequency.DAILY -> if (dailyGoalCount > 1) {
                val unit = unitLabel.ifBlank { "times" }
                "Daily · $dailyGoalCount $unit"
            } else {
                "Daily"
            }

            HabitFrequency.WEEKLY_TARGET -> {
                val base = "${weeklyTarget}x / week"
                if (dailyGoalCount > 1) "$base · $dailyGoalCount ${unitLabel.ifBlank { "times" }}" else base
            }

            HabitFrequency.SPECIFIC_DAYS -> {
                val ordered = listOf(
                    Calendar.MONDAY to "Mon",
                    Calendar.TUESDAY to "Tue",
                    Calendar.WEDNESDAY to "Wed",
                    Calendar.THURSDAY to "Thu",
                    Calendar.FRIDAY to "Fri",
                    Calendar.SATURDAY to "Sat",
                    Calendar.SUNDAY to "Sun"
                ).filter { it.first in scheduledDays }.map { it.second }
                if (ordered.size == 7 || ordered.isEmpty()) "Daily" else ordered.joinToString(" · ")
            }
        }
    }

    companion object {
        val ALL_WEEKDAYS: Set<Int> = setOf(
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
        )
    }
}

data class HabitCompletion(
    val habitId: String,
    val dayKeyMillis: Long,
    val count: Int
)

data class HabitWithStats(
    val habit: HabitItem,
    val selectedDayCount: Int,
    val isCompletedOnSelectedDay: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRate30d: Float,
    val totalCompletions: Int,
    val weekCompletedDays: Int,
    val countsByDayKey: Map<Long, Int>
)

object HabitConstraints {
    const val MAX_NAME_LENGTH = 100
    const val MAX_DESCRIPTION_LENGTH = 500
    const val MAX_CATEGORY_LENGTH = 30
    const val MAX_UNIT_LENGTH = 20
    const val MAX_DAILY_GOAL = 99

    val DEFAULT_CATEGORIES = listOf("Health", "Fitness", "Mind", "Learning", "Work", "Lifestyle")
}
