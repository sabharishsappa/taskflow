package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DayDate
import com.example.model.ProjectColorPalette
import com.example.model.TaskItem
import com.example.model.TaskList
import com.example.ui.components.TaskCard

private val WEEKDAY_HEADERS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun CalendarScreen(
    tasks: List<TaskItem>,
    lists: List<TaskList>,
    selectedDate: DayDate,
    displayedMonth: DayDate,
    selectedListFilterId: String?,
    onSelectDate: (DayDate) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onJumpToToday: () -> Unit,
    onSelectListFilter: (String?) -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onAddTaskForDate: (DayDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { DayDate.today() }
    val listsById = remember(lists) { lists.associateBy { it.id } }

    val filteredTasks = remember(tasks, selectedListFilterId) {
        if (selectedListFilterId == null) tasks
        else tasks.filter { it.listId == selectedListFilterId }
    }

    val tasksByEpochDay = remember(filteredTasks) {
        filteredTasks.groupBy { it.dueDateEpochDay }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareBy<TaskItem>(
                        { it.completed },
                        { it.reminderEpochMillis ?: Long.MAX_VALUE },
                        { it.title.lowercase() }
                    )
                )
            }
    }

    val selectedDayTasks = remember(tasksByEpochDay, selectedDate) {
        tasksByEpochDay[selectedDate.toEpochDay()].orEmpty()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isTwoPane = maxWidth >= 720.dp

        if (isTwoPane) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1080.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(modifier = Modifier.weight(1.15f)) {
                    CalendarTopHeader(
                        displayedMonth = displayedMonth,
                        onPrevMonth = { onShiftMonth(-1) },
                        onNextMonth = { onShiftMonth(1) },
                        onJumpToToday = onJumpToToday,
                    )
                    CalendarCategoryLegend(
                        lists = lists,
                        selectedListFilterId = selectedListFilterId,
                        onSelectListFilter = onSelectListFilter,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CalendarMonthCard(
                        displayedMonth = displayedMonth,
                        selectedDate = selectedDate,
                        today = today,
                        tasksByEpochDay = tasksByEpochDay,
                        listsById = listsById,
                        onSelectDate = onSelectDate,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                ) {
                    SelectedDayAgendaHeader(
                        selectedDate = selectedDate,
                        today = today,
                        taskCount = selectedDayTasks.size,
                        onAddForDate = { onAddTaskForDate(selectedDate) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (selectedDayTasks.isEmpty()) {
                            item {
                                CalendarDayEmptyCard(
                                    selectedDate = selectedDate,
                                    onAddForDate = { onAddTaskForDate(selectedDate) },
                                )
                            }
                        } else {
                            items(selectedDayTasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    taskList = listsById[task.listId],
                                    onToggleComplete = { onToggleTaskComplete(task) },
                                    onClick = { onEditTask(task) },
                                    showDueDateBadge = false,
                                    showListBadge = true,
                                    today = today,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 108.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("calendar_lazy_column"),
            ) {
                item(key = "cal_header") {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        CalendarTopHeader(
                            displayedMonth = displayedMonth,
                            onPrevMonth = { onShiftMonth(-1) },
                            onNextMonth = { onShiftMonth(1) },
                            onJumpToToday = onJumpToToday,
                        )
                    }
                }

                item(key = "cal_legend") {
                    CalendarCategoryLegend(
                        lists = lists,
                        selectedListFilterId = selectedListFilterId,
                        onSelectListFilter = onSelectListFilter,
                    )
                }

                item(key = "cal_grid") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        CalendarMonthCard(
                            displayedMonth = displayedMonth,
                            selectedDate = selectedDate,
                            today = today,
                            tasksByEpochDay = tasksByEpochDay,
                            listsById = listsById,
                            onSelectDate = onSelectDate,
                        )
                    }
                }

                item(key = "agenda_header") {
                    Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) {
                        SelectedDayAgendaHeader(
                            selectedDate = selectedDate,
                            today = today,
                            taskCount = selectedDayTasks.size,
                            onAddForDate = { onAddTaskForDate(selectedDate) },
                        )
                    }
                }

                if (selectedDayTasks.isEmpty()) {
                    item(key = "agenda_empty") {
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            CalendarDayEmptyCard(
                                selectedDate = selectedDate,
                                onAddForDate = { onAddTaskForDate(selectedDate) },
                            )
                        }
                    }
                } else {
                    items(selectedDayTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            taskList = listsById[task.listId],
                            onToggleComplete = { onToggleTaskComplete(task) },
                            onClick = { onEditTask(task) },
                            showDueDateBadge = false,
                            showListBadge = true,
                            today = today,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarTopHeader(
    displayedMonth: DayDate,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Calendar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = displayedMonth.formatMonthYear(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onJumpToToday,
                modifier = Modifier.testTag("calendar_today_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Today,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Today")
            }
            IconButton(
                onClick = onPrevMonth,
                modifier = Modifier.testTag("calendar_prev_month"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                )
            }
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.testTag("calendar_next_month"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next month",
                )
            }
        }
    }
}

@Composable
private fun CalendarCategoryLegend(
    lists: List<TaskList>,
    selectedListFilterId: String?,
    onSelectListFilter: (String?) -> Unit,
) {
    if (lists.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedListFilterId == null,
                onClick = { onSelectListFilter(null) },
                label = { Text("All Categories") },
                modifier = Modifier.testTag("calendar_filter_all"),
            )
        }
        items(lists, key = { it.id }) { list ->
            val color = ProjectColorPalette.parseColor(list.colorHex)
            FilterChip(
                selected = selectedListFilterId == list.id,
                onClick = {
                    onSelectListFilter(if (selectedListFilterId == list.id) null else list.id)
                },
                label = { Text(list.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.16f),
                    selectedLabelColor = color,
                ),
                modifier = Modifier.testTag("calendar_filter_${list.id}"),
            )
        }
    }
}

@Composable
private fun CalendarMonthCard(
    displayedMonth: DayDate,
    selectedDate: DayDate,
    today: DayDate,
    tasksByEpochDay: Map<Long, List<TaskItem>>,
    listsById: Map<String, TaskList>,
    onSelectDate: (DayDate) -> Unit,
) {
    val firstOfMonth = displayedMonth.firstOfMonth()
    val leadingEmptyCells = firstOfMonth.dayOfWeekSunStart()
    val daysInMonth = displayedMonth.lengthOfMonth()
    val totalCells = ((leadingEmptyCells + daysInMonth + 6) / 7) * 7
    val rows = totalCells / 7

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)) {
            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAY_HEADERS.forEach { dayName ->
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            for (rowIndex in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (colIndex in 0 until 7) {
                        val cellIndex = rowIndex * 7 + colIndex
                        val dayNumber = cellIndex - leadingEmptyCells + 1
                        if (dayNumber in 1..daysInMonth) {
                            val cellDate = DayDate(displayedMonth.year, displayedMonth.month, dayNumber)
                            val dayTasks = tasksByEpochDay[cellDate.toEpochDay()].orEmpty()
                            val taskColors = dayTasks.map { task ->
                                val base = ProjectColorPalette.parseColor(listsById[task.listId]?.colorHex)
                                if (task.completed) base.copy(alpha = 0.45f) else base
                            }
                            CalendarDayCell(
                                date = cellDate,
                                isSelected = cellDate == selectedDate,
                                isToday = cellDate == today,
                                taskColors = taskColors,
                                onClick = { onSelectDate(cellDate) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(56.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: DayDate,
    isSelected: Boolean,
    isToday: Boolean,
    taskColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 3.dp)
            .testTag("calendar_day_${date.toEpochDay()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = date.day.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
            ),
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        // Visually color-coded project bars / dots for tasks on this date
        if (taskColors.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Show up to 2 crisp color-coded pill bars + overflow count/dots
                if (taskColors.size <= 3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        taskColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(color)
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        taskColors.take(3).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                        Text(
                            text = "+${taskColors.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SelectedDayAgendaHeader(
    selectedDate: DayDate,
    today: DayDate,
    taskCount: Int,
    onAddForDate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedDate.formatRelativeHeader(today),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when (taskCount) {
                    0 -> "No tasks scheduled"
                    1 -> "1 task scheduled"
                    else -> "$taskCount tasks scheduled"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = onAddForDate,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("calendar_add_for_day_button"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CalendarDayEmptyCard(
    selectedDate: DayDate,
    onAddForDate: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Open day on ${selectedDate.formatShortDate()}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Schedule a task with a reminder and project color.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAddForDate) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add task for ${selectedDate.formatShortDate()}")
            }
        }
    }
}
