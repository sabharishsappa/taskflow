package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DayDate
import com.example.model.ProjectColorPalette
import com.example.model.TaskItem
import com.example.model.TaskList
import com.example.model.TimelineBucketKind
import com.example.model.TimelineGrouper
import com.example.model.TimelineSection
import com.example.ui.components.TaskCard
import kotlinx.coroutines.launch

@Composable
fun TimelineScreen(
    tasks: List<TaskItem>,
    lists: List<TaskList>,
    selectedListFilterId: String?,
    showCompleted: Boolean,
    onSelectListFilter: (String?) -> Unit,
    onToggleShowCompleted: () -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onQuickAddForDate: (DayDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { DayDate.today() }
    val listsById = remember(lists) { lists.associateBy { it.id } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filteredTasks = remember(tasks, selectedListFilterId, showCompleted) {
        tasks.filter { task ->
            val matchesList = selectedListFilterId == null || task.listId == selectedListFilterId
            val matchesCompleted = showCompleted || !task.completed
            matchesList && matchesCompleted
        }
    }

    val sections = remember(filteredTasks, today) {
        TimelineGrouper.groupTasks(filteredTasks, today)
    }

    val activeTodayCount = remember(tasks, today) {
        tasks.count { !it.completed && it.dueDateEpochDay == today.toEpochDay() }
    }

    val next14Days = remember(today) {
        (0..13).map { today.plusDays(it) }
    }

    val colorsByEpochDay = remember(filteredTasks, listsById) {
        filteredTasks
            .groupBy { it.dueDateEpochDay }
            .mapValues { (_, dayTasks) ->
                dayTasks.map { ProjectColorPalette.parseColor(listsById[it.listId]?.colorHex) }
                    .distinct()
                    .take(4)
            }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 104.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("timeline_lazy_column"),
        ) {
            // 1. Editorial Greeting Header
            item(key = "header") {
                TimelineHeader(
                    today = today,
                    activeTodayCount = activeTodayCount,
                )
            }

            // 2. Next 2 Weeks Interactive Horizon Strip
            item(key = "two_week_strip") {
                TwoWeekHorizonStrip(
                    days = next14Days,
                    today = today,
                    colorsByEpochDay = colorsByEpochDay,
                    onDayClicked = { clickedDate ->
                        val targetIndex = findSectionItemIndex(sections, clickedDate)
                        if (targetIndex != null) {
                            scope.launch {
                                listState.animateScrollToItem(targetIndex)
                            }
                        } else {
                            onQuickAddForDate(clickedDate)
                        }
                    },
                )
            }

            // 3. Filter Row (All Lists + Project Lists + Show Completed)
            item(key = "filter_bar") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedListFilterId == null,
                            onClick = { onSelectListFilter(null) },
                            label = { Text(stringResource(id = R.string.all_lists_filter)) },
                            modifier = Modifier.testTag("timeline_filter_all"),
                        )
                    }
                    items(lists, key = { it.id }) { list ->
                        val listColor = ProjectColorPalette.parseColor(list.colorHex)
                        FilterChip(
                            selected = selectedListFilterId == list.id,
                            onClick = {
                                onSelectListFilter(if (selectedListFilterId == list.id) null else list.id)
                            },
                            label = { Text(list.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(listColor)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = listColor.copy(alpha = 0.16f),
                                selectedLabelColor = listColor,
                            ),
                            modifier = Modifier.testTag("timeline_filter_${list.id}"),
                        )
                    }
                    item {
                        FilterChip(
                            selected = showCompleted,
                            onClick = onToggleShowCompleted,
                            label = { Text(if (showCompleted) "Showing Done" else "Show Done") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.testTag("timeline_toggle_completed"),
                        )
                    }
                }
            }

            // 4. Timeline Sections or Calm Empty State
            if (sections.isEmpty()) {
                item(key = "empty_state") {
                    TimelineEmptyState(
                        hasAnyTasks = tasks.isNotEmpty(),
                        onAddTask = { onQuickAddForDate(today) },
                    )
                }
            } else {
                sections.forEach { section ->
                    item(key = "section_header_${section.id}") {
                        TimelineSectionHeader(
                            section = section,
                            onAddForDate = section.date?.let { date -> { onQuickAddForDate(date) } },
                        )
                    }
                    items(
                        items = section.tasks,
                        key = { "${section.id}_${it.id}" },
                    ) { task ->
                        val showDateOnCard = section.kind != TimelineBucketKind.DAY_WITHIN_TWO_WEEKS
                        TaskCard(
                            task = task,
                            taskList = listsById[task.listId],
                            onToggleComplete = { onToggleTaskComplete(task) },
                            onClick = { onEditTask(task) },
                            showDueDateBadge = showDateOnCard,
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

private fun findSectionItemIndex(sections: List<TimelineSection>, targetDate: DayDate): Int? {
    // 3 fixed header items before sections: header (0), two_week_strip (1), filter_bar (2)
    var index = 3
    for (section in sections) {
        if (section.date == targetDate) {
            return index
        }
        index += 1 + section.tasks.size
    }
    return null
}

@Composable
private fun TimelineHeader(
    today: DayDate,
    activeTodayCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Text(
            text = "${today.fullWeekday()}, ${today.shortMonth()} ${today.day}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your Timeline",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = when (activeTodayCount) {
                0 -> "Everything for today is clear"
                1 -> "1 task due today"
                else -> "$activeTodayCount tasks due today"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TwoWeekHorizonStrip(
    days: List<DayDate>,
    today: DayDate,
    colorsByEpochDay: Map<Long, List<Color>>,
    onDayClicked: (DayDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Next 2 Weeks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${days.first().formatShortDate()} – ${days.last().formatShortDate()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.toEpochDay() }) { day ->
                val isToday = day == today
                val dotColors = colorsByEpochDay[day.toEpochDay()].orEmpty()

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    border = if (isToday) null else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .width(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onDayClicked(day) }
                        .testTag("horizon_day_${day.toEpochDay()}"),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = day.shortWeekday(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = day.day.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.height(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dotColors.forEach { dotColor ->
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isToday) Color.White else dotColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineSectionHeader(
    section: TimelineSection,
    onAddForDate: (() -> Unit)?,
) {
    val accentColor = when (section.kind) {
        TimelineBucketKind.OVERDUE -> MaterialTheme.colorScheme.secondary
        TimelineBucketKind.DAY_WITHIN_TWO_WEEKS -> MaterialTheme.colorScheme.primary
        TimelineBucketKind.LATER_TWO_WEEKS_TO_TWO_MONTHS -> MaterialTheme.colorScheme.tertiary
        TimelineBucketKind.AFTER_TWO_MONTHS -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            if (section.kind == TimelineBucketKind.OVERDUE) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (section.kind == TimelineBucketKind.AFTER_TWO_MONTHS) {
                Icon(
                    imageVector = Icons.Rounded.EventNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                )
                if (!section.subtitle.isNullOrBlank()) {
                    Text(
                        text = section.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accentColor.copy(alpha = 0.12f),
            ) {
                Text(
                    text = section.tasks.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            if (onAddForDate != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    onClick = onAddForDate,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add task for ${section.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEmptyState(
    hasAnyTasks: Boolean,
    onAddTask: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_calm),
                contentDescription = "Calm botanical empty list illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (hasAnyTasks) "All caught up here" else stringResource(id = R.string.empty_timeline_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasAnyTasks) {
                "No pending tasks match this filter. Toggle \"Show Done\" or add a new task below."
            } else {
                stringResource(id = R.string.empty_timeline_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onAddTask,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("empty_add_task_button"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add a Task")
        }
    }
}
