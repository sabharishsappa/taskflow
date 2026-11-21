package com.example

import com.example.model.DayDate
import com.example.model.TaskItem
import com.example.model.TimelineBucketKind
import com.example.model.TimelineGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayDateTimelineTest {

    @Test
    fun epochDayRoundTripAndLeapYearAreExact() {
        val leapDay = DayDate(2024, 2, 29)
        assertEquals(leapDay, DayDate.fromEpochDay(leapDay.toEpochDay()))
        assertEquals(DayDate(2024, 3, 1), leapDay.plusDays(1))
        assertEquals(29, DayDate.daysInMonth(2024, 2))
        assertEquals(28, DayDate.daysInMonth(2026, 2))

        val nov20 = DayDate(2026, 11, 20)
        assertEquals(nov20, DayDate.fromEpochDay(nov20.toEpochDay()))
        assertEquals(5, nov20.dayOfWeekIso()) // Friday
        assertEquals(DayDate(2027, 1, 20), nov20.plusMonths(2))
    }

    @Test
    fun timelineGroupsOverdueTwoWeeksTwoMonthsAndAfterTwoMonths() {
        val today = DayDate(2026, 11, 20)
        val tasks = listOf(
            TaskItem(id = "t_overdue", listId = "l1", title = "Overdue", dueDateEpochDay = today.plusDays(-2).toEpochDay()),
            TaskItem(id = "t_today", listId = "l1", title = "Today", dueDateEpochDay = today.toEpochDay()),
            TaskItem(id = "t_day13", listId = "l1", title = "End of 2 weeks", dueDateEpochDay = today.plusDays(13).toEpochDay()),
            TaskItem(id = "t_day20", listId = "l1", title = "Between 2w and 2m", dueDateEpochDay = today.plusDays(20).toEpochDay()),
            TaskItem(id = "t_2m_exact", listId = "l1", title = "Exactly 2 months", dueDateEpochDay = today.plusMonths(2).toEpochDay()),
            TaskItem(id = "t_after_2m", listId = "l1", title = "After 2 months", dueDateEpochDay = today.plusMonths(2).plusDays(1).toEpochDay()),
        )

        val sections = TimelineGrouper.groupTasks(tasks, today)
        assertEquals(5, sections.size)

        assertEquals(TimelineBucketKind.OVERDUE, sections[0].kind)
        assertEquals(listOf("t_overdue"), sections[0].tasks.map { it.id })

        assertEquals(TimelineBucketKind.DAY_WITHIN_TWO_WEEKS, sections[1].kind)
        assertEquals(today, sections[1].date)
        assertEquals(listOf("t_today"), sections[1].tasks.map { it.id })

        assertEquals(TimelineBucketKind.DAY_WITHIN_TWO_WEEKS, sections[2].kind)
        assertEquals(today.plusDays(13), sections[2].date)
        assertEquals(listOf("t_day13"), sections[2].tasks.map { it.id })

        assertEquals(TimelineBucketKind.LATER_TWO_WEEKS_TO_TWO_MONTHS, sections[3].kind)
        assertEquals(listOf("t_day20", "t_2m_exact"), sections[3].tasks.map { it.id })

        assertEquals(TimelineBucketKind.AFTER_TWO_MONTHS, sections[4].kind)
        assertEquals(listOf("t_after_2m"), sections[4].tasks.map { it.id })
        assertTrue(sections[4].title.contains("After 2 Months"))
    }
}
