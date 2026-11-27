package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.HabitItem
import com.example.data.HabitWithStats
import com.example.ui.DateFormatters
import com.example.data.DailyBackupConfig
import com.example.ui.HabitDayOverview
import com.example.ui.HabitViewModel
import kotlin.math.roundToInt

@Composable
fun HabitsScreen(
    viewModel: HabitViewModel,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit
) {
    val habits by viewModel.filteredHabits.collectAsStateWithLifecycle()
    val allHabits by viewModel.allHabitsWithStats.collectAsStateWithLifecycle()
    val selectedDayKey by viewModel.selectedDayKey.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val dayOverview by viewModel.dayOverview.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val last7Days = remember { DateFormatters.recentDayKeys(7) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showNewHabitSheet by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<HabitItem?>(null) }
    var inspectingHabitId by rememberSaveable { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val inspectingStats = inspectingHabitId?.let { id ->
        allHabits.firstOrNull { it.habit.id == id }
    }
    if (inspectingStats != null) {
        HabitDetailSheet(
            stats = inspectingStats,
            onDismiss = { inspectingHabitId = null },
            onToggleDay = { dayKey -> viewModel.toggleHabitForDay(inspectingStats.habit, dayKey) },
            onEdit = {
                editingHabit = inspectingStats.habit
                inspectingHabitId = null
            },
            onToggleArchive = {
                viewModel.setArchived(inspectingStats.habit.id, !inspectingStats.habit.isArchived)
                inspectingHabitId = null
            },
            onDelete = {
                viewModel.deleteHabit(inspectingStats.habit.id)
                inspectingHabitId = null
            }
        )
    }

    if (showNewHabitSheet || editingHabit != null) {
        val target = editingHabit
        HabitEditorSheet(
            existingHabit = target,
            availableCategories = availableCategories,
            onDismiss = {
                showNewHabitSheet = false
                editingHabit = null
            },
            onSave = { name, desc, cat, accent, freq, days, weeklyTarget, dailyGoal, unit ->
                viewModel.saveHabit(
                    habitId = target?.id,
                    name = name,
                    description = desc,
                    category = cat,
                    accent = accent,
                    frequency = freq,
                    scheduledDays = days,
                    weeklyTarget = weeklyTarget,
                    dailyGoalCount = dailyGoal,
                    unitLabel = unit,
                    onSuccess = {
                        showNewHabitSheet = false
                        editingHabit = null
                    }
                )
            },
            onDelete = target?.let { h ->
                {
                    viewModel.deleteHabit(h.id)
                    editingHabit = null
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewHabitSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = {
                    Text(
                        text = stringResource(R.string.action_new_habit),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_habit_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("habits_screen"),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                item(key = "habits_header") {
                    HabitsHeader(
                        searchExpanded = searchExpanded,
                        showArchived = filterState.showArchived,
                        archivedCount = archivedCount,
                        isBackupBusy = isBackupBusy,
                        canExportBackup = canExportBackup,
                        dailyBackupConfig = dailyBackupConfig,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.updateSearchQuery("")
                        },
                        onToggleArchived = viewModel::toggleShowArchived,
                        onExportZipClick = onExportZipClick,
                        onExportJsonClick = onExportJsonClick,
                        onImportClick = onImportClick,
                        onOpenBackupCenter = onOpenBackupCenter
                    )
                }

                if (isBackupBusy) {
                    item(key = "habits_backup_progress") {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }

                item(key = "habits_search_bar") {
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = filterState.query,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(R.string.habits_search_placeholder)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                if (filterState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_clear_search)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!filterState.showArchived) {
                    item(key = "habits_day_overview") {
                        HabitOverviewCard(
                            selectedDayKey = selectedDayKey,
                            overview = dayOverview
                        )
                    }

                    item(key = "habits_week_strip") {
                        WeekDaySelectorStrip(
                            days = last7Days,
                            selectedDayKey = selectedDayKey,
                            onSelectDay = viewModel::selectDay
                        )
                    }
                }

                item(key = "habits_category_dropdown") {
                    HabitCategoryDropdownRow(
                        selectedCategory = filterState.selectedCategory,
                        availableCategories = availableCategories,
                        onSelectCategory = viewModel::selectCategory
                    )
                }

                if (habits.isEmpty()) {
                    item(key = "habits_empty") {
                        HabitsEmptyState(
                            showArchived = filterState.showArchived,
                            onCreateHabit = { showNewHabitSheet = true }
                        )
                    }
                } else {
                    items(habits, key = { it.habit.id }) { stats ->
                        HabitCard(
                            stats = stats,
                            selectedDayKey = selectedDayKey,
                            recentDays = last7Days,
                            onToggleSelectedDay = { viewModel.toggleHabitForDay(stats.habit, selectedDayKey) },
                            onStepSelectedDay = { delta ->
                                viewModel.stepHabitForDay(stats.habit, delta, selectedDayKey)
                            },
                            onToggleSpecificDay = { dayKey ->
                                viewModel.toggleHabitForDay(stats.habit, dayKey)
                            },
                            onClick = { inspectingHabitId = stats.habit.id },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitsHeader(
    searchExpanded: Boolean,
    showArchived: Boolean,
    archivedCount: Int,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onToggleSearch: () -> Unit,
    onToggleArchived: () -> Unit,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (showArchived) R.string.habits_archived_subtitle else R.string.habits_subtitle
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = stringResource(
                    if (showArchived) R.string.habits_archived_title else R.string.habits_title
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            if (archivedCount > 0 || showArchived) {
                IconButton(
                    onClick = onToggleArchived,
                    modifier = Modifier.testTag("habits_toggle_archived")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = stringResource(R.string.action_toggle_archived),
                        tint = if (showArchived) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            BackupMenuButton(
                isBackupBusy = isBackupBusy,
                canExportBackup = canExportBackup,
                dailyBackupConfig = dailyBackupConfig,
                onExportZipClick = onExportZipClick,
                onExportJsonClick = onExportJsonClick,
                onImportClick = onImportClick,
                onOpenBackupCenter = onOpenBackupCenter,
                buttonTestTag = "habits_backup_menu_button"
            )
        }
    }
}

@Composable
private fun HabitOverviewCard(
    selectedDayKey: Long,
    overview: HabitDayOverview
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = DateFormatters.formatDateGroupHeader(selectedDayKey),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (overview.scheduledCount == 0) {
                            stringResource(R.string.habits_none_scheduled)
                        } else {
                            stringResource(
                                R.string.habits_day_progress_format,
                                overview.completedCount,
                                overview.scheduledCount,
                                (overview.progress * 100).roundToInt()
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (overview.bestCurrentStreak > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${overview.bestCurrentStreak}d",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (overview.scheduledCount > 0) {
                LinearProgressIndicator(
                    progress = { overview.progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun WeekDaySelectorStrip(
    days: List<Long>,
    selectedDayKey: Long,
    onSelectDay: (Long) -> Unit
) {
    val todayKey = DateFormatters.dayKey(System.currentTimeMillis())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { dayKey ->
            val selected = dayKey == selectedDayKey
            val isToday = dayKey == todayKey
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDay(dayKey) }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = DateFormatters.shortWeekdayLabel(dayKey).take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = DateFormatters.dayOfMonthNumber(dayKey),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitCard(
    stats: HabitWithStats,
    selectedDayKey: Long,
    recentDays: List<Long>,
    onToggleSelectedDay: () -> Unit,
    onStepSelectedDay: (Int) -> Unit,
    onToggleSpecificDay: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = stats.habit
    val accentColor = habitAccentColor(habit.accent)
    val goal = habit.dailyGoalCount.coerceAtLeast(1)
    val isCountable = goal > 1
    val scheduledToday = habit.isScheduledOnWeekday(DateFormatters.dayOfWeek(selectedDayKey))

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (stats.isCompletedOnSelectedDay) 1.5.dp else 1.dp,
            color = if (stats.isCompletedOnSelectedDay) {
                accentColor.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("habit_card_${habit.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (scheduledToday || stats.isCompletedOnSelectedDay) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${habit.category} · ${habit.scheduleSummary()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (stats.currentStreak > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${stats.currentStreak}d",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isCountable) {
                    // Single-tap 48dp check toggle
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleSelectedDay)
                            .testTag("habit_check_${habit.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (stats.isCompletedOnSelectedDay) accentColor else Color.Transparent,
                            border = BorderStroke(2.dp, accentColor),
                            modifier = Modifier.size(34.dp)
                        ) {
                            if (stats.isCompletedOnSelectedDay) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.action_toggle_habit),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Countable habit stepper (- count/goal +)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onStepSelectedDay(-1) },
                            enabled = stats.selectedDayCount > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = stringResource(R.string.action_decrement_habit),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (stats.isCompletedOnSelectedDay) {
                                accentColor
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ) {
                            Text(
                                text = "${stats.selectedDayCount}/$goal",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (stats.isCompletedOnSelectedDay) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                        IconButton(
                            onClick = { onStepSelectedDay(1) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("habit_increment_${habit.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.action_increment_habit),
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (isCountable) {
                val progress = (stats.selectedDayCount.toFloat() / goal).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            // Inline 7-Day Interactive Mini Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                recentDays.forEach { dayKey ->
                    val count = stats.countsByDayKey[dayKey] ?: 0
                    val done = count >= goal
                    val partial = count in 1 until goal
                    val isSelected = dayKey == selectedDayKey
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleSpecificDay(dayKey) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = DateFormatters.shortWeekdayLabel(dayKey).take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = when {
                                done -> accentColor
                                partial -> accentColor.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.size(22.dp)
                        ) {
                            if (done) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitCategoryDropdownRow(
    selectedCategory: String?,
    availableCategories: List<String>,
    onSelectCategory: (String?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FilterChip(
                selected = selectedCategory != null,
                onClick = { menuExpanded = true },
                leadingIcon = {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = selectedCategory ?: stringResource(R.string.habit_filter_all_categories),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    if (selectedCategory != null) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSelectCategory(null) }
                        )
                    } else {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                modifier = Modifier.testTag("habit_category_dropdown_chip")
            )

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.habit_filter_all_categories)) },
                    trailingIcon = if (selectedCategory == null) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        menuExpanded = false
                        onSelectCategory(null)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                availableCategories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        trailingIcon = if (selectedCategory == cat) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            menuExpanded = false
                            onSelectCategory(cat)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitsEmptyState(
    showArchived: Boolean,
    onCreateHabit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Loop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(
                if (showArchived) R.string.habits_empty_archived_title else R.string.habits_empty_title
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (showArchived) R.string.habits_empty_archived_subtitle else R.string.habits_empty_subtitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!showArchived) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onCreateHabit, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_new_habit))
            }
        }
    }
}
