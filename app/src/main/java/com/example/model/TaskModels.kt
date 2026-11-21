package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class TaskList(
    val id: String = "",
    val name: String = "",
    val colorHex: String = ProjectColorPalette.DEFAULT_HEX,
    val iconKey: String = ListIconOption.PERSONAL.key,
    val sortOrder: Long = 0L,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

data class TaskItem(
    val id: String = "",
    val listId: String = "",
    val title: String = "",
    val notes: String? = null,
    val dueDateEpochDay: Long = DayDate.today().toEpochDay(),
    val reminderEpochMillis: Long? = null,
    val completed: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    fun dueDate(): DayDate = DayDate.fromEpochDay(dueDateEpochDay)
}

enum class ListIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
) {
    PERSONAL("personal", "Personal", Icons.Rounded.Person),
    WORK("work", "Work", Icons.Rounded.Work),
    HOME("home", "Home", Icons.Rounded.Home),
    WELLNESS("wellness", "Wellness", Icons.Rounded.Favorite),
    ERRANDS("errands", "Errands", Icons.Rounded.ShoppingBag),
    STUDY("study", "Study", Icons.Rounded.School),
    FINANCE("finance", "Finance", Icons.Rounded.AccountBalanceWallet),
    CREATIVE("creative", "Creative", Icons.Rounded.Palette),
    TRAVEL("travel", "Travel", Icons.Rounded.Flight),
    STAR("star", "Focus", Icons.Rounded.AutoAwesome);

    companion object {
        fun fromKey(key: String?): ListIconOption {
            return entries.firstOrNull { it.key == key } ?: PERSONAL
        }
    }
}

data class ProjectColorOption(
    val hex: String,
    val name: String,
    val color: Color,
)

object ProjectColorPalette {
    const val DEFAULT_HEX = "#235340"

    val options: List<ProjectColorOption> = listOf(
        ProjectColorOption("#235340", "Pine", Color(0xFF235340)),
        ProjectColorOption("#C4643E", "Terracotta", Color(0xFFC4643E)),
        ProjectColorOption("#2D6387", "Ocean", Color(0xFF2D6387)),
        ProjectColorOption("#C28324", "Amber", Color(0xFFC28324)),
        ProjectColorOption("#7A4988", "Plum", Color(0xFF7A4988)),
        ProjectColorOption("#2D7F7B", "Teal", Color(0xFF2D7F7B)),
        ProjectColorOption("#B85068", "Rose", Color(0xFFB85068)),
        ProjectColorOption("#4A5992", "Indigo", Color(0xFF4A5992)),
    )

    fun parseColor(hex: String?): Color {
        if (hex == null || !hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
            return Color(0xFF235340)
        }
        return try {
            val rgb = hex.substring(1).toLong(16)
            Color((0xFF000000L or rgb))
        } catch (_: Exception) {
            Color(0xFF235340)
        }
    }
}

enum class TimelineBucketKind {
    OVERDUE,
    DAY_WITHIN_TWO_WEEKS,
    LATER_TWO_WEEKS_TO_TWO_MONTHS,
    AFTER_TWO_MONTHS,
}

data class TimelineSection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val kind: TimelineBucketKind,
    val date: DayDate?,
    val tasks: List<TaskItem>,
)

object TimelineGrouper {
    /**
     * Groups tasks cleanly for the common timeline:
     * 1. Overdue (incomplete tasks before today)
     * 2. Date-by-date for the next 2 weeks (today through today + 13 days = 14 calendar days)
     * 3. Later (Day +14 through +2 months)
     * 4. After 2 Months (strictly beyond today + 2 months)
     */
    fun groupTasks(
        tasks: List<TaskItem>,
        today: DayDate = DayDate.today(),
    ): List<TimelineSection> {
        val todayEpoch = today.toEpochDay()
        val twoWeeksEndEpoch = today.plusDays(13).toEpochDay() // Inclusive 14-day window (0..13)
        val twoMonthsEndEpoch = today.plusMonths(2).toEpochDay()

        val sorted = tasks.sortedWith(
            compareBy<TaskItem>(
                { it.dueDateEpochDay },
                { it.completed },
                { it.reminderEpochMillis ?: Long.MAX_VALUE },
                { it.title.lowercase() }
            )
        )

        val overdue = mutableListOf<TaskItem>()
        val withinTwoWeeks = linkedMapOf<Long, MutableList<TaskItem>>()
        val twoWeeksToTwoMonths = mutableListOf<TaskItem>()
        val afterTwoMonths = mutableListOf<TaskItem>()

        for (task in sorted) {
            val due = task.dueDateEpochDay
            when {
                due < todayEpoch -> overdue.add(task)
                due <= twoWeeksEndEpoch -> {
                    withinTwoWeeks.getOrPut(due) { mutableListOf() }.add(task)
                }
                due <= twoMonthsEndEpoch -> twoWeeksToTwoMonths.add(task)
                else -> afterTwoMonths.add(task)
            }
        }

        val sections = mutableListOf<TimelineSection>()

        if (overdue.isNotEmpty()) {
            sections.add(
                TimelineSection(
                    id = "overdue",
                    title = "Overdue",
                    subtitle = "${overdue.size} ${if (overdue.size == 1) "task" else "tasks"} to catch up",
                    kind = TimelineBucketKind.OVERDUE,
                    date = null,
                    tasks = overdue,
                )
            )
        }

        for ((epochDay, dayTasks) in withinTwoWeeks) {
            val date = DayDate.fromEpochDay(epochDay)
            val dayOffset = (epochDay - todayEpoch).toInt()
            val sub = when (dayOffset) {
                0 -> "Focus for today"
                1 -> "Coming up tomorrow"
                in 2..6 -> "This week"
                else -> "Next week"
            }
            sections.add(
                TimelineSection(
                    id = "day_$epochDay",
                    title = date.formatRelativeHeader(today),
                    subtitle = sub,
                    kind = TimelineBucketKind.DAY_WITHIN_TWO_WEEKS,
                    date = date,
                    tasks = dayTasks,
                )
            )
        }

        if (twoWeeksToTwoMonths.isNotEmpty()) {
            val startLabel = today.plusDays(14).formatShortDate()
            val endLabel = today.plusMonths(2).formatShortDate()
            sections.add(
                TimelineSection(
                    id = "two_weeks_to_two_months",
                    title = "Later · 2 Weeks to 2 Months",
                    subtitle = "$startLabel – $endLabel",
                    kind = TimelineBucketKind.LATER_TWO_WEEKS_TO_TWO_MONTHS,
                    date = null,
                    tasks = twoWeeksToTwoMonths,
                )
            )
        }

        if (afterTwoMonths.isNotEmpty()) {
            sections.add(
                TimelineSection(
                    id = "after_two_months",
                    title = "After 2 Months",
                    subtitle = "Beyond ${today.plusMonths(2).formatFullDate(today.year)}",
                    kind = TimelineBucketKind.AFTER_TWO_MONTHS,
                    date = null,
                    tasks = afterTwoMonths,
                )
            )
        }

        return sections
    }
}
