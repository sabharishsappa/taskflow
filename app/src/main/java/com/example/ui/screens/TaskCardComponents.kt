package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SubTask
import com.example.data.TaskConstraints
import com.example.data.TaskItem
import com.example.data.TaskPriority
import com.example.data.TaskRecurrence
import com.example.ui.DateFormatters
import com.example.ui.components.OptionDropdownField
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: TaskItem,
    onToggleComplete: () -> Unit,
    onToggleSubTask: (String) -> Unit,
    onAddSubTask: (title: String, dueDateMillis: Long?, priority: TaskPriority) -> Unit,
    onUpdateSubTask: (SubTask) -> Unit,
    onDeleteSubTask: (String) -> Unit,
    onSetAllSubTasksCompleted: (Boolean) -> Unit,
    onDeleteTask: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Expand subtasks by default when a task has subtasks so users can see and click them complete immediately
    var expandedSubTasks by rememberSaveable(task.id) { mutableStateOf(task.subTasks.isNotEmpty()) }
    var previousSubTaskCount by remember(task.id) { mutableStateOf(task.subTasks.size) }
    LaunchedEffect(task.subTasks.size) {
        if (previousSubTaskCount == 0 && task.subTasks.isNotEmpty()) {
            expandedSubTasks = true
        }
        previousSubTaskCount = task.subTasks.size
    }
    var showInlineAddSubTask by rememberSaveable(task.id) { mutableStateOf(false) }
    var inlineSubTaskTitle by remember(task.id) { mutableStateOf("") }
    var taskMenuExpanded by remember { mutableStateOf(false) }
    var editingSubTask by remember { mutableStateOf<SubTask?>(null) }
    var addingDetailedSubTask by remember { mutableStateOf(false) }

    val todayKey = DateFormatters.dayKey(System.currentTimeMillis())
    val dueKey = task.dueDateMillis?.let { DateFormatters.dayKey(it) }
    val isOverdue = !task.isCompleted && dueKey != null && dueKey < todayKey
    val isDueToday = !task.isCompleted && dueKey == todayKey

    val priorityColor: Color = taskPriorityColor(task.priority)

    if (editingSubTask != null) {
        val current = editingSubTask!!
        SubTaskOptionsDialog(
            existingSubTask = current,
            initialTitle = current.title,
            onDismiss = { editingSubTask = null },
            onSave = { updated ->
                onUpdateSubTask(updated)
                editingSubTask = null
            },
            onDelete = {
                onDeleteSubTask(current.id)
                editingSubTask = null
            }
        )
    }

    if (addingDetailedSubTask) {
        SubTaskOptionsDialog(
            existingSubTask = null,
            initialTitle = inlineSubTaskTitle,
            onDismiss = { addingDetailedSubTask = false },
            onSave = { created ->
                onAddSubTask(created.title, created.dueDateMillis, created.priority)
                inlineSubTaskTitle = ""
                showInlineAddSubTask = false
                expandedSubTasks = true
                addingDetailedSubTask = false
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (task.isCompleted) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isOverdue) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("task_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 48dp touch target priority-ring check button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleComplete)
                        .testTag("task_check_${task.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = BorderStroke(
                            width = 2.dp,
                            color = if (task.isCompleted) MaterialTheme.colorScheme.primary else priorityColor
                        ),
                        modifier = Modifier.size(24.dp)
                    ) {
                        if (task.isCompleted) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.action_toggle_task),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (task.notes.isNotBlank()) {
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val hasMeta = task.dueDateMillis != null ||
                        task.priority != TaskPriority.NONE ||
                        task.category.isNotBlank() ||
                        task.recurrence != TaskRecurrence.NONE ||
                        task.subTasks.isNotEmpty()

                    if (hasMeta) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            task.dueDateMillis?.let { due ->
                                val chipBg = when {
                                    isOverdue -> MaterialTheme.colorScheme.errorContainer
                                    isDueToday -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                }
                                val chipFg = when {
                                    isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                    isDueToday -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                MetaPill(
                                    icon = Icons.Outlined.CalendarToday,
                                    text = DateFormatters.formatRelativeDueDate(due),
                                    containerColor = chipBg,
                                    contentColor = chipFg
                                )
                            }

                            if (task.priority != TaskPriority.NONE) {
                                MetaPill(
                                    icon = Icons.Outlined.Flag,
                                    text = task.priority.label,
                                    containerColor = priorityColor.copy(alpha = 0.14f),
                                    contentColor = priorityColor
                                )
                            }

                            if (task.category.isNotBlank()) {
                                MetaPill(
                                    icon = null,
                                    text = task.category,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            if (task.recurrence != TaskRecurrence.NONE) {
                                MetaPill(
                                    icon = Icons.Outlined.Repeat,
                                    text = task.recurrence.label,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (task.subTasks.isNotEmpty()) {
                                val allDone = task.completedSubTaskCount == task.subTasks.size
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (allDone) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { expandedSubTasks = !expandedSubTasks }
                                        .testTag("task_subtasks_toggle_${task.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Checklist,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "${task.completedSubTaskCount}/${task.subTasks.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (allDone) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Icon(
                                            imageVector = if (expandedSubTasks) {
                                                Icons.Rounded.ExpandLess
                                            } else {
                                                Icons.Rounded.ExpandMore
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Task Options Dropdown (Complete, Add Subtask, Complete All Subtasks, Edit, Delete)
                Box {
                    IconButton(
                        onClick = { taskMenuExpanded = true },
                        modifier = Modifier.testTag("task_options_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_task_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = taskMenuExpanded,
                        onDismissRequest = { taskMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (task.isCompleted) R.string.action_mark_active
                                        else R.string.action_mark_complete
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (task.isCompleted) {
                                        Icons.Outlined.RadioButtonUnchecked
                                    } else {
                                        Icons.Outlined.CheckCircle
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                taskMenuExpanded = false
                                onToggleComplete()
                            }
                        )
                        if (task.subTasks.size < TaskConstraints.MAX_SUBTASKS) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_subtask)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    taskMenuExpanded = false
                                    expandedSubTasks = true
                                    showInlineAddSubTask = true
                                }
                            )
                        }
                        if (task.subTasks.isNotEmpty()) {
                            val hasIncompleteSubs = task.completedSubTaskCount < task.subTasks.size
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (hasIncompleteSubs) R.string.action_complete_all_subtasks
                                            else R.string.action_uncheck_all_subtasks
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DoneAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    taskMenuExpanded = false
                                    onSetAllSubTasksCompleted(hasIncompleteSubs)
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit_task)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                taskMenuExpanded = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.action_delete_task),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                taskMenuExpanded = false
                                onDeleteTask()
                            }
                        )
                    }
                }
            }

            // Subtasks section (expanded by default when subtasks exist, or when inline add is triggered)
            val showSubTaskArea = (expandedSubTasks && task.subTasks.isNotEmpty()) || showInlineAddSubTask
            AnimatedVisibility(visible = showSubTaskArea) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

                    if (task.subTasks.isNotEmpty()) {
                        val subProgress = (task.completedSubTaskCount.toFloat() / task.subTasks.size)
                            .coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { subProgress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                            Text(
                                text = stringResource(
                                    R.string.subtasks_progress_format,
                                    task.completedSubTaskCount,
                                    task.subTasks.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    task.subTasks.forEach { st ->
                        SubTaskCardRow(
                            subTask = st,
                            onToggleComplete = { onToggleSubTask(st.id) },
                            onEditOptions = { editingSubTask = st },
                            onDelete = { onDeleteSubTask(st.id) }
                        )
                    }

                    // Gentle 1-tap complete prompt when all subtasks are finished on an active task
                    if (!task.isCompleted &&
                        task.subTasks.isNotEmpty() &&
                        task.completedSubTaskCount == task.subTasks.size
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onToggleComplete)
                                .testTag("task_complete_all_done_${task.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.subtask_all_done_prompt),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Inline quick-add subtask input OR subtle "+ Subtask" trigger
                    if (showInlineAddSubTask && task.subTasks.size < TaskConstraints.MAX_SUBTASKS) {
                        fun submitInline() {
                            val clean = inlineSubTaskTitle.trim()
                            if (clean.isNotEmpty()) {
                                onAddSubTask(clean, null, TaskPriority.NONE)
                                inlineSubTaskTitle = ""
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = inlineSubTaskTitle,
                                onValueChange = { next ->
                                    if (next.length <= TaskConstraints.MAX_SUBTASK_LENGTH) {
                                        inlineSubTaskTitle = next
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.task_add_subtask_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { submitInline() }),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { addingDetailedSubTask = true },
                                        modifier = Modifier.testTag("task_inline_subtask_options_${task.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = stringResource(R.string.action_subtask_options),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("task_inline_subtask_input_${task.id}")
                            )
                            FilledTonalIconButton(
                                onClick = ::submitInline,
                                enabled = inlineSubTaskTitle.isNotBlank(),
                                modifier = Modifier.testTag("task_inline_subtask_add_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.action_add_subtask)
                                )
                            }
                            IconButton(
                                onClick = {
                                    inlineSubTaskTitle = ""
                                    showInlineAddSubTask = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (task.subTasks.size < TaskConstraints.MAX_SUBTASKS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            TextButton(
                                onClick = { showInlineAddSubTask = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("task_quick_add_subtask_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.action_add_subtask),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubTaskCardRow(
    subTask: SubTask,
    onToggleComplete: () -> Unit,
    onEditOptions: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val prioColor = taskPriorityColor(subTask.priority)
    val todayKey = DateFormatters.dayKey(System.currentTimeMillis())
    val dueKey = subTask.dueDateMillis?.let { DateFormatters.dayKey(it) }
    val isOverdue = !subTask.isCompleted && dueKey != null && dueKey < todayKey

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggleComplete)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 48dp accessible click-to-complete target for each subtask
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleComplete)
                .testTag("subtask_check_${subTask.id}"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (subTask.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = BorderStroke(
                    width = 1.75.dp,
                    color = if (subTask.isCompleted) MaterialTheme.colorScheme.primary else prioColor
                ),
                modifier = Modifier.size(20.dp)
            ) {
                if (subTask.isCompleted) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.action_mark_incomplete),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = subTask.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null,
                color = if (subTask.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (subTask.dueDateMillis != null || subTask.priority != TaskPriority.NONE) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subTask.dueDateMillis?.let { due ->
                        MetaPill(
                            icon = Icons.Outlined.CalendarToday,
                            text = DateFormatters.formatRelativeDueDate(due),
                            containerColor = if (isOverdue) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            contentColor = if (isOverdue) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (subTask.priority != TaskPriority.NONE) {
                        MetaPill(
                            icon = Icons.Outlined.Flag,
                            text = subTask.priority.label,
                            containerColor = prioColor.copy(alpha = 0.14f),
                            contentColor = prioColor
                        )
                    }
                }
            }
        }

        // Subtask Options Dropdown
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("subtask_options_${subTask.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_subtask_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (subTask.isCompleted) R.string.action_mark_incomplete
                                else R.string.action_mark_complete
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (subTask.isCompleted) {
                                Icons.Outlined.RadioButtonUnchecked
                            } else {
                                Icons.Outlined.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onToggleComplete()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit_subtask_options)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onEditOptions()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_remove_subtask),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubTaskOptionsDialog(
    existingSubTask: SubTask?,
    initialTitle: String = existingSubTask?.title.orEmpty(),
    onDismiss: () -> Unit,
    onSave: (SubTask) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember(existingSubTask?.id, initialTitle) { mutableStateOf(initialTitle) }
    var isCompleted by remember(existingSubTask?.id) {
        mutableStateOf(existingSubTask?.isCompleted ?: false)
    }
    var dueDateMillis by remember(existingSubTask?.id) {
        mutableStateOf(existingSubTask?.dueDateMillis)
    }
    var priority by remember(existingSubTask?.id) {
        mutableStateOf(existingSubTask?.priority ?: TaskPriority.NONE)
    }

    var dueMenuExpanded by remember { mutableStateOf(false) }
    var priorityMenuExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val todayMidday = remember { DateFormatters.todayMiddayMillis() }
    val tomorrowMidday = remember { DateFormatters.addDays(todayMidday, 1) }
    val nextWeekMidday = remember { DateFormatters.addDays(todayMidday, 7) }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateFormatters.localMillisToUtcPickerMillis(
                dueDateMillis ?: todayMidday
            )
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { utc ->
                            dueDateMillis = DateFormatters.utcPickerMillisToLocalMiddayMillis(utc)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (existingSubTask == null) R.string.subtask_dialog_add_title
                    else R.string.subtask_dialog_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { next ->
                        if (next.length <= TaskConstraints.MAX_SUBTASK_LENGTH) title = next
                    },
                    label = { Text(stringResource(R.string.subtask_title_label)) },
                    placeholder = { Text(stringResource(R.string.subtask_title_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtask_dialog_title_input")
                )

                // Clickable Complete toggle row
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isCompleted = !isCompleted }
                        .testTag("subtask_dialog_complete_toggle")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = if (isCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(
                                if (isCompleted) R.string.task_status_completed
                                else R.string.action_mark_complete
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCompleted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                // Due Date Dropdown
                val dueDisplay = dueDateMillis?.let { DateFormatters.formatRelativeDueDate(it) }
                    ?: stringResource(R.string.task_due_none)
                OptionDropdownField(
                    label = stringResource(R.string.task_due_date_label),
                    value = dueDisplay,
                    expanded = dueMenuExpanded,
                    onExpandedChange = { dueMenuExpanded = it },
                    leadingIcon = Icons.Outlined.CalendarToday,
                    leadingIconTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtask_due_dropdown")
                ) {
                    val selectedKey = dueDateMillis?.let { DateFormatters.dayKey(it) }
                    val todayKey = DateFormatters.dayKey(todayMidday)
                    val tomorrowKey = DateFormatters.dayKey(tomorrowMidday)
                    val nextWeekKey = DateFormatters.dayKey(nextWeekMidday)

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_due_none)) },
                        trailingIcon = if (dueDateMillis == null) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            dueDateMillis = null
                            dueMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_due_today)) },
                        trailingIcon = if (selectedKey == todayKey) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            dueDateMillis = todayMidday
                            dueMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_due_tomorrow)) },
                        trailingIcon = if (selectedKey == tomorrowKey) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            dueDateMillis = tomorrowMidday
                            dueMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_due_next_week)) },
                        trailingIcon = if (selectedKey == nextWeekKey) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            dueDateMillis = nextWeekMidday
                            dueMenuExpanded = false
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_due_pick_date)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            dueMenuExpanded = false
                            showDatePicker = true
                        }
                    )
                }

                // Priority Dropdown
                OptionDropdownField(
                    label = stringResource(R.string.task_priority_label),
                    value = priority.label,
                    expanded = priorityMenuExpanded,
                    onExpandedChange = { priorityMenuExpanded = it },
                    leadingIcon = Icons.Outlined.Flag,
                    leadingIconTint = taskPriorityColor(priority),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtask_priority_dropdown")
                ) {
                    listOf(
                        TaskPriority.NONE,
                        TaskPriority.LOW,
                        TaskPriority.MEDIUM,
                        TaskPriority.HIGH
                    ).forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            trailingIcon = if (priority == p) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                priority = p
                                priorityMenuExpanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = {
                    val cleaned = title.trim().take(TaskConstraints.MAX_SUBTASK_LENGTH)
                    if (cleaned.isNotEmpty()) {
                        onSave(
                            SubTask(
                                id = existingSubTask?.id ?: UUID.randomUUID().toString(),
                                title = cleaned,
                                isCompleted = isCompleted,
                                dueDateMillis = dueDateMillis,
                                priority = priority
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("subtask_dialog_save_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existingSubTask != null && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_confirm_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
fun MetaPill(
    icon: ImageVector?,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun taskPriorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondary
    TaskPriority.LOW -> MaterialTheme.colorScheme.primary
    TaskPriority.NONE -> MaterialTheme.colorScheme.outline
}
