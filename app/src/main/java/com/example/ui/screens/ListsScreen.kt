package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DayDate
import com.example.model.ListIconOption
import com.example.model.ProjectColorPalette
import com.example.model.TaskItem
import com.example.model.TaskList
import com.example.ui.components.TaskCard

@Composable
fun ListsScreen(
    lists: List<TaskList>,
    tasks: List<TaskItem>,
    selectedListDetailId: String?,
    onOpenListDetail: (String?) -> Unit,
    onCreateList: () -> Unit,
    onEditList: (TaskList) -> Unit,
    onAddTaskInList: (String) -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedList = remember(lists, selectedListDetailId) {
        selectedListDetailId?.let { id -> lists.firstOrNull { it.id == id } }
    }

    if (selectedList != null) {
        ListDetailView(
            list = selectedList,
            tasks = tasks.filter { it.listId == selectedList.id },
            onBack = { onOpenListDetail(null) },
            onEditList = { onEditList(selectedList) },
            onAddTask = { onAddTaskInList(selectedList.id) },
            onToggleTaskComplete = onToggleTaskComplete,
            onEditTask = onEditTask,
            modifier = modifier,
        )
    } else {
        ListsOverviewGrid(
            lists = lists,
            tasks = tasks,
            onOpenList = { onOpenListDetail(it.id) },
            onCreateList = onCreateList,
            onEditList = onEditList,
            modifier = modifier,
        )
    }
}

@Composable
private fun ListsOverviewGrid(
    lists: List<TaskList>,
    tasks: List<TaskItem>,
    onOpenList: (TaskList) -> Unit,
    onCreateList: () -> Unit,
    onEditList: (TaskList) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tasksByList = remember(tasks) { tasks.groupBy { it.listId } }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 108.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
                .testTag("lists_grid"),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "lists_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Projects & Categories",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Your Lists",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    OutlinedButton(
                        onClick = onCreateList,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("header_new_list_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New List")
                    }
                }
            }

            if (lists.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty_lists") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(id = R.string.empty_lists_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = R.string.empty_lists_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onCreateList,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("empty_create_list_button"),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Your First List")
                            }
                        }
                    }
                }
            } else {
                items(lists, key = { it.id }) { list ->
                    val listTasks = tasksByList[list.id].orEmpty()
                    val activeCount = listTasks.count { !it.completed }
                    val completedCount = listTasks.count { it.completed }
                    ListSummaryCard(
                        list = list,
                        activeCount = activeCount,
                        completedCount = completedCount,
                        onClick = { onOpenList(list) },
                        onEdit = { onEditList(list) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListSummaryCard(
    list: TaskList,
    activeCount: Int,
    completedCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val accentColor = ProjectColorPalette.parseColor(list.colorHex)
    val iconOption = ListIconOption.fromKey(list.iconKey)
    val totalCount = activeCount + completedCount
    val progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("list_card_${list.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconOption.icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("edit_list_${list.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${list.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = list.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (activeCount) {
                    0 -> if (completedCount > 0) "All $completedCount done" else "No tasks yet"
                    1 -> "1 active task"
                    else -> "$activeCount active tasks"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun ListDetailView(
    list: TaskList,
    tasks: List<TaskItem>,
    onBack: () -> Unit,
    onEditList: () -> Unit,
    onAddTask: () -> Unit,
    onToggleTaskComplete: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val today = remember { DayDate.today() }
    val accentColor = ProjectColorPalette.parseColor(list.colorHex)
    val iconOption = ListIconOption.fromKey(list.iconKey)
    var showCompleted by remember { mutableStateOf(true) }

    val sortedTasks = remember(tasks, showCompleted) {
        tasks
            .filter { showCompleted || !it.completed }
            .sortedWith(
                compareBy<TaskItem>(
                    { it.completed },
                    { it.dueDateEpochDay },
                    { it.reminderEpochMillis ?: Long.MAX_VALUE },
                )
            )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 108.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("list_detail_column"),
        ) {
            item(key = "detail_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("list_detail_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Lists",
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = showCompleted,
                                onClick = { showCompleted = !showCompleted },
                                label = { Text(if (showCompleted) "Showing Done" else "Hide Done") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onEditList,
                                modifier = Modifier.testTag("list_detail_edit_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit list settings",
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = iconOption.icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = list.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            val remaining = tasks.count { !it.completed }
                            Text(
                                text = "$remaining remaining · ${tasks.size} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (sortedTasks.isEmpty()) {
                item(key = "detail_empty") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No tasks in ${list.name} yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tasks added here also appear in your unified Timeline and color-coded Calendar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onAddTask,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("list_detail_empty_add_task"),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Task")
                            }
                        }
                    }
                }
            } else {
                items(sortedTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        taskList = list,
                        onToggleComplete = { onToggleTaskComplete(task) },
                        onClick = { onEditTask(task) },
                        showDueDateBadge = true,
                        showListBadge = false,
                        today = today,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
