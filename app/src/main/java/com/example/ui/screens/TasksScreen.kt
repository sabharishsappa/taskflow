package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.DailyBackupConfig
import com.example.data.TaskItem
import com.example.data.TaskPriority
import com.example.ui.TaskSortOption
import com.example.ui.TaskSummaryStats
import com.example.ui.TaskViewFilter
import com.example.ui.TaskViewModel

@Composable
fun TasksScreen(
    viewModel: TaskViewModel,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit
) {
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val summaryStats by viewModel.summaryStats.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showNewTaskSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    if (showNewTaskSheet || editingTask != null) {
        val target = editingTask
        TaskEditorSheet(
            existingTask = target,
            availableCategories = availableCategories,
            onDismiss = {
                showNewTaskSheet = false
                editingTask = null
            },
            onSave = { title, notes, due, priority, category, recurrence, subTasks, isCompleted ->
                viewModel.saveTask(
                    taskId = target?.id,
                    title = title,
                    notes = notes,
                    dueDateMillis = due,
                    priority = priority,
                    category = category,
                    recurrence = recurrence,
                    subTasks = subTasks,
                    isCompleted = isCompleted,
                    onSuccess = {
                        showNewTaskSheet = false
                        editingTask = null
                    }
                )
            },
            onDelete = target?.let { t ->
                {
                    viewModel.deleteTask(t.id)
                    editingTask = null
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
                onClick = { showNewTaskSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = {
                    Text(
                        text = stringResource(R.string.action_new_task),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_task_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("tasks_screen"),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                item(key = "tasks_header") {
                    TasksHeader(
                        searchExpanded = searchExpanded,
                        sortOption = filterState.sortOption,
                        isBackupBusy = isBackupBusy,
                        canExportBackup = canExportBackup,
                        dailyBackupConfig = dailyBackupConfig,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.updateSearchQuery("")
                        },
                        onSelectSort = viewModel::selectSortOption,
                        onExportZipClick = onExportZipClick,
                        onExportJsonClick = onExportJsonClick,
                        onImportClick = onImportClick,
                        onOpenBackupCenter = onOpenBackupCenter
                    )
                }

                if (isBackupBusy) {
                    item(key = "tasks_backup_progress") {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }

                item(key = "tasks_search_bar") {
                    AnimatedVisibility(
                        visible = searchExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = filterState.query,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text(stringResource(R.string.tasks_search_placeholder)) },
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
                                .testTag("tasks_search_input")
                        )
                    }
                }

                item(key = "tasks_summary") {
                    TaskProgressBanner(
                        stats = summaryStats,
                        onOverdueClick = { viewModel.selectViewFilter(TaskViewFilter.OVERDUE) }
                    )
                }

                item(key = "tasks_dropdown_filters") {
                    TasksFilterDropdownBar(
                        viewFilter = filterState.viewFilter,
                        stats = summaryStats,
                        selectedCategory = filterState.selectedCategory,
                        selectedPriority = filterState.selectedPriority,
                        availableCategories = availableCategories,
                        hasExtraFilters = filterState.hasExtraFilters,
                        onSelectViewFilter = viewModel::selectViewFilter,
                        onSelectCategory = viewModel::selectCategory,
                        onSelectPriority = viewModel::selectPriority,
                        onClearExtraFilters = viewModel::clearExtraFilters,
                        onClearCompleted = viewModel::clearCompletedTasks
                    )
                }

                if (filteredTasks.isEmpty()) {
                    item(key = "tasks_empty") {
                        TasksEmptyState(
                            viewFilter = filterState.viewFilter,
                            hasExtraFilters = filterState.hasExtraFilters,
                            onCreateTask = { showNewTaskSheet = true },
                            onClearFilters = viewModel::clearExtraFilters
                        )
                    }
                } else {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskCompleted(task) },
                            onToggleSubTask = { subId -> viewModel.toggleSubTask(task, subId) },
                            onAddSubTask = { title, due, priority ->
                                viewModel.addSubTask(task, title, due, priority)
                            },
                            onUpdateSubTask = { updatedSub ->
                                viewModel.updateSubTask(task, updatedSub)
                            },
                            onDeleteSubTask = { subId ->
                                viewModel.deleteSubTask(task, subId)
                            },
                            onSetAllSubTasksCompleted = { done ->
                                viewModel.setAllSubTasksCompleted(task, done)
                            },
                            onDeleteTask = { viewModel.deleteTask(task.id) },
                            onClick = { editingTask = task },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksHeader(
    searchExpanded: Boolean,
    sortOption: TaskSortOption,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onToggleSearch: () -> Unit,
    onSelectSort: (TaskSortOption) -> Unit,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tasks_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = stringResource(R.string.tasks_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.testTag("tasks_search_toggle")
            ) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Box {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.testTag("tasks_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sort,
                        contentDescription = stringResource(R.string.action_sort_tasks),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    TaskSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = if (option == sortOption) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                sortMenuExpanded = false
                                onSelectSort(option)
                            }
                        )
                    }
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
                buttonTestTag = "tasks_backup_menu_button"
            )
        }
    }
}

@Composable
private fun TaskProgressBanner(
    stats: TaskSummaryStats,
    onOverdueClick: () -> Unit
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
                        text = stringResource(R.string.tasks_today_progress_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (stats.todayTotalCount == 0) {
                            stringResource(R.string.tasks_active_count_format, stats.activeCount)
                        } else {
                            stringResource(
                                R.string.tasks_today_done_format,
                                stats.todayCompletedCount,
                                stats.todayTotalCount
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (stats.overdueCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOverdueClick)
                    ) {
                        Text(
                            text = stringResource(R.string.tasks_overdue_badge_format, stats.overdueCount),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (stats.todayTotalCount > 0) {
                val progress = (stats.todayCompletedCount.toFloat() / stats.todayTotalCount).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasksFilterDropdownBar(
    viewFilter: TaskViewFilter,
    stats: TaskSummaryStats,
    selectedCategory: String?,
    selectedPriority: TaskPriority?,
    availableCategories: List<String>,
    hasExtraFilters: Boolean,
    onSelectViewFilter: (TaskViewFilter) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectPriority: (TaskPriority?) -> Unit,
    onClearExtraFilters: () -> Unit,
    onClearCompleted: () -> Unit
) {
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var catMenuExpanded by remember { mutableStateOf(false) }
    var prioMenuExpanded by remember { mutableStateOf(false) }

    val showClearCompleted = viewFilter == TaskViewFilter.COMPLETED && stats.completedCount > 0

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // View Dropdown (Active / Today / Upcoming / Overdue / Completed)
        Box {
            val viewBadge = when (viewFilter) {
                TaskViewFilter.ALL -> stats.activeCount
                TaskViewFilter.OVERDUE -> stats.overdueCount
                TaskViewFilter.COMPLETED -> stats.completedCount
                else -> 0
            }
            val viewLabel = if (viewBadge > 0) "${viewFilter.label} ($viewBadge)" else viewFilter.label

            FilterChip(
                selected = true,
                onClick = { viewMenuExpanded = true },
                leadingIcon = {
                    Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = viewLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (viewFilter == TaskViewFilter.OVERDUE) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    selectedLabelColor = if (viewFilter == TaskViewFilter.OVERDUE) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                ),
                modifier = Modifier.testTag("task_view_dropdown_chip")
            )
            DropdownMenu(
                expanded = viewMenuExpanded,
                onDismissRequest = { viewMenuExpanded = false }
            ) {
                TaskViewFilter.entries.forEach { tab ->
                    val badge = when (tab) {
                        TaskViewFilter.ALL -> stats.activeCount
                        TaskViewFilter.OVERDUE -> stats.overdueCount
                        TaskViewFilter.COMPLETED -> stats.completedCount
                        else -> 0
                    }
                    val itemText = if (badge > 0) "${tab.label} ($badge)" else tab.label
                    DropdownMenuItem(
                        text = { Text(itemText) },
                        trailingIcon = if (tab == viewFilter) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            viewMenuExpanded = false
                            onSelectViewFilter(tab)
                        },
                        modifier = Modifier.testTag("task_view_filter_${tab.name.lowercase()}")
                    )
                }
            }
        }

        // Category Dropdown
        Box {
            FilterChip(
                selected = selectedCategory != null,
                onClick = { catMenuExpanded = true },
                leadingIcon = {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(
                        text = selectedCategory ?: stringResource(R.string.task_filter_category),
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
                }
            )
            DropdownMenu(
                expanded = catMenuExpanded,
                onDismissRequest = { catMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_filter_all_categories)) },
                    trailingIcon = if (selectedCategory == null) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        catMenuExpanded = false
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
                            catMenuExpanded = false
                            onSelectCategory(cat)
                        }
                    )
                }
            }
        }

        // Priority Dropdown
        Box {
            FilterChip(
                selected = selectedPriority != null,
                onClick = { prioMenuExpanded = true },
                leadingIcon = {
                    Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = {
                    Text(selectedPriority?.label ?: stringResource(R.string.task_priority_label))
                },
                trailingIcon = {
                    if (selectedPriority != null) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSelectPriority(null) }
                        )
                    } else {
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            )
            DropdownMenu(
                expanded = prioMenuExpanded,
                onDismissRequest = { prioMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.task_filter_all_priorities)) },
                    trailingIcon = if (selectedPriority == null) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        prioMenuExpanded = false
                        onSelectPriority(null)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                listOf(TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW).forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        trailingIcon = if (selectedPriority == p) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            prioMenuExpanded = false
                            onSelectPriority(p)
                        }
                    )
                }
            }
        }

        if (showClearCompleted) {
            TextButton(
                onClick = onClearCompleted,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.testTag("clear_completed_tasks_button")
            ) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_clear_completed), style = MaterialTheme.typography.labelMedium)
            }
        } else if (hasExtraFilters) {
            TextButton(
                onClick = onClearExtraFilters,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.action_clear_filters), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}



@Composable
private fun TasksEmptyState(
    viewFilter: TaskViewFilter,
    hasExtraFilters: Boolean,
    onCreateTask: () -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.TaskAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = when {
                hasExtraFilters -> stringResource(R.string.tasks_empty_filtered_title)
                viewFilter == TaskViewFilter.COMPLETED -> stringResource(R.string.tasks_empty_completed_title)
                viewFilter == TaskViewFilter.OVERDUE -> stringResource(R.string.tasks_empty_overdue_title)
                else -> stringResource(R.string.tasks_empty_title)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                hasExtraFilters -> stringResource(R.string.tasks_empty_filtered_subtitle)
                viewFilter == TaskViewFilter.OVERDUE -> stringResource(R.string.tasks_empty_overdue_subtitle)
                else -> stringResource(R.string.tasks_empty_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (hasExtraFilters) {
            OutlinedButton(onClick = onClearFilters, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.action_clear_filters))
            }
        } else if (viewFilter != TaskViewFilter.COMPLETED && viewFilter != TaskViewFilter.OVERDUE) {
            Button(onClick = onCreateTask, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_new_task))
            }
        }
    }
}
