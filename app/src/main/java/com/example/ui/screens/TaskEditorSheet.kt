package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    existingTask: TaskItem?,
    availableCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        notes: String,
        dueDateMillis: Long?,
        priority: TaskPriority,
        category: String,
        recurrence: TaskRecurrence,
        subTasks: List<SubTask>,
        isCompleted: Boolean
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(existingTask?.id) { mutableStateOf(existingTask?.title.orEmpty()) }
    var notes by remember(existingTask?.id) { mutableStateOf(existingTask?.notes.orEmpty()) }
    var dueDateMillis by remember(existingTask?.id) { mutableStateOf(existingTask?.dueDateMillis) }
    var priority by remember(existingTask?.id) { mutableStateOf(existingTask?.priority ?: TaskPriority.NONE) }
    var category by remember(existingTask?.id) { mutableStateOf(existingTask?.category.orEmpty()) }
    var recurrence by remember(existingTask?.id) { mutableStateOf(existingTask?.recurrence ?: TaskRecurrence.NONE) }
    var subTasks by remember(existingTask?.id) { mutableStateOf(existingTask?.subTasks.orEmpty()) }
    var isCompleted by remember(existingTask?.id) { mutableStateOf(existingTask?.isCompleted ?: false) }
    var newSubTaskText by remember { mutableStateOf("") }

    var dueMenuExpanded by remember { mutableStateOf(false) }
    var priorityMenuExpanded by remember { mutableStateOf(false) }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var subTaskBulkMenuExpanded by remember { mutableStateOf(false) }
    var showCustomCategoryInput by remember { mutableStateOf(false) }
    var customCategoryInput by remember { mutableStateOf("") }

    var editingSubTask by remember { mutableStateOf<SubTask?>(null) }
    var showAddSubTaskOptionsDialog by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val todayMidday = remember { DateFormatters.todayMiddayMillis() }
    val tomorrowMidday = remember { DateFormatters.addDays(todayMidday, 1) }
    val nextWeekMidday = remember { DateFormatters.addDays(todayMidday, 7) }

    fun addSubTask() {
        val cleaned = newSubTaskText.trim().take(TaskConstraints.MAX_SUBTASK_LENGTH)
        if (cleaned.isEmpty() || subTasks.size >= TaskConstraints.MAX_SUBTASKS) return
        subTasks = subTasks + SubTask(id = UUID.randomUUID().toString(), title = cleaned)
        newSubTaskText = ""
    }

    if (editingSubTask != null) {
        val current = editingSubTask!!
        SubTaskOptionsDialog(
            existingSubTask = current,
            initialTitle = current.title,
            onDismiss = { editingSubTask = null },
            onSave = { updated ->
                subTasks = subTasks.map { if (it.id == updated.id) updated else it }
                editingSubTask = null
            },
            onDelete = {
                subTasks = subTasks.filterNot { it.id == current.id }
                editingSubTask = null
            }
        )
    }

    if (showAddSubTaskOptionsDialog) {
        SubTaskOptionsDialog(
            existingSubTask = null,
            initialTitle = newSubTaskText,
            onDismiss = { showAddSubTaskOptionsDialog = false },
            onSave = { created ->
                if (subTasks.size < TaskConstraints.MAX_SUBTASKS) {
                    subTasks = subTasks + created
                }
                newSubTaskText = ""
                showAddSubTaskOptionsDialog = false
            }
        )
    }

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

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.task_delete_dialog_title)) },
            text = { Text(stringResource(R.string.task_delete_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        if (existingTask == null) R.string.task_editor_new_title
                        else R.string.task_editor_edit_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (existingTask != null && onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("task_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.action_confirm_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (title.trim().isEmpty()) {
                                validationError = "Please enter a task title."
                                return@Button
                            }
                            // Automatically commit any typed subtask text so users never lose an unsubmitted step
                            val pendingSub = newSubTaskText.trim().take(TaskConstraints.MAX_SUBTASK_LENGTH)
                            val finalSubTasks = if (pendingSub.isNotEmpty() && subTasks.size < TaskConstraints.MAX_SUBTASKS) {
                                subTasks + SubTask(id = UUID.randomUUID().toString(), title = pendingSub)
                            } else {
                                subTasks
                            }
                            onSave(
                                title,
                                notes,
                                dueDateMillis,
                                priority,
                                category,
                                recurrence,
                                finalSubTasks,
                                isCompleted
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("task_save_button")
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }

            // Explicit Click-to-Complete status toggle when editing a task
            if (existingTask != null) {
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
                        .testTag("task_editor_complete_toggle")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCompleted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            if (validationError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { next ->
                    if (next.length <= TaskConstraints.MAX_TITLE_LENGTH) {
                        title = next
                        validationError = null
                    }
                },
                label = { Text(stringResource(R.string.task_title_label)) },
                placeholder = { Text(stringResource(R.string.task_title_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { next ->
                    if (next.length <= TaskConstraints.MAX_NOTES_LENGTH) notes = next
                },
                label = { Text(stringResource(R.string.task_notes_label)) },
                placeholder = { Text(stringResource(R.string.task_notes_placeholder)) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_notes_input")
            )

            // Row 1 of Dropdowns: Due Date & Priority
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                        .weight(1f)
                        .testTag("task_due_dropdown")
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

                val prioTint = when (priority) {
                    TaskPriority.HIGH -> MaterialTheme.colorScheme.error
                    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.secondary
                    TaskPriority.LOW -> MaterialTheme.colorScheme.primary
                    TaskPriority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                OptionDropdownField(
                    label = stringResource(R.string.task_priority_label),
                    value = priority.label,
                    expanded = priorityMenuExpanded,
                    onExpandedChange = { priorityMenuExpanded = it },
                    leadingIcon = Icons.Outlined.Flag,
                    leadingIconTint = prioTint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_priority_dropdown")
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

            // Row 2 of Dropdowns: Repeat & Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OptionDropdownField(
                    label = stringResource(R.string.task_recurrence_label),
                    value = recurrence.label,
                    expanded = recurrenceMenuExpanded,
                    onExpandedChange = { recurrenceMenuExpanded = it },
                    leadingIcon = Icons.Outlined.Repeat,
                    leadingIconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_recurrence_dropdown")
                ) {
                    TaskRecurrence.entries.forEach { rec ->
                        DropdownMenuItem(
                            text = { Text(rec.label) },
                            trailingIcon = if (recurrence == rec) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                recurrence = rec
                                recurrenceMenuExpanded = false
                            }
                        )
                    }
                }

                val mergedCategories = remember(availableCategories, category) {
                    (availableCategories + listOf(category)).filter { it.isNotBlank() }.distinct()
                }
                OptionDropdownField(
                    label = stringResource(R.string.task_category_label),
                    value = category.ifBlank { stringResource(R.string.option_none) },
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    leadingIcon = Icons.Outlined.Folder,
                    leadingIconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_category_dropdown")
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.option_none)) },
                        trailingIcon = if (category.isBlank()) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            category = ""
                            showCustomCategoryInput = false
                            categoryMenuExpanded = false
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    mergedCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            trailingIcon = if (category.equals(cat, ignoreCase = true)) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                category = cat
                                showCustomCategoryInput = false
                                categoryMenuExpanded = false
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.option_add_custom_category)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            categoryMenuExpanded = false
                            showCustomCategoryInput = true
                        }
                    )
                }
            }

            if (showCustomCategoryInput) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customCategoryInput,
                        onValueChange = { next ->
                            if (next.length <= TaskConstraints.MAX_CATEGORY_LENGTH) {
                                customCategoryInput = next
                            }
                        },
                        placeholder = { Text(stringResource(R.string.task_custom_category_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val cleaned = customCategoryInput.trim()
                                if (cleaned.isNotEmpty()) {
                                    category = cleaned
                                    customCategoryInput = ""
                                    showCustomCategoryInput = false
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val cleaned = customCategoryInput.trim()
                            if (cleaned.isNotEmpty()) {
                                category = cleaned
                                customCategoryInput = ""
                                showCustomCategoryInput = false
                            }
                        },
                        enabled = customCategoryInput.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_set))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Subtasks / Checklist Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.task_subtasks_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subTasks.isNotEmpty()) {
                            val doneCount = subTasks.count { it.isCompleted }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.subtasks_progress_format, doneCount, subTasks.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (subTasks.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = { subTaskBulkMenuExpanded = true },
                                modifier = Modifier.testTag("task_editor_subtasks_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_subtask_options),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = subTaskBulkMenuExpanded,
                                onDismissRequest = { subTaskBulkMenuExpanded = false }
                            ) {
                                val hasIncomplete = subTasks.any { !it.isCompleted }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (hasIncomplete) R.string.action_complete_all_subtasks
                                                else R.string.action_uncheck_all_subtasks
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        subTaskBulkMenuExpanded = false
                                        subTasks = subTasks.map { it.copy(isCompleted = hasIncomplete) }
                                    }
                                )
                                if (subTasks.any { it.isCompleted }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_clear_completed_subtasks)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            subTaskBulkMenuExpanded = false
                                            subTasks = subTasks.filterNot { it.isCompleted }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                subTasks.forEach { st ->
                    var stMenuExpanded by remember(st.id) { mutableStateOf(false) }
                    val prioColor = taskPriorityColor(st.priority)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                subTasks = subTasks.map {
                                    if (it.id == st.id) it.copy(isCompleted = !it.isCompleted) else it
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        subTasks = subTasks.map {
                                            if (it.id == st.id) it.copy(isCompleted = !it.isCompleted) else it
                                        }
                                    }
                                    .testTag("editor_subtask_check_${st.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (st.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    border = BorderStroke(
                                        width = 1.75.dp,
                                        color = if (st.isCompleted) MaterialTheme.colorScheme.primary else prioColor
                                    ),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    if (st.isCompleted) {
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
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = st.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (st.isCompleted) TextDecoration.LineThrough else null,
                                    color = if (st.isCompleted) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (st.dueDateMillis != null || st.priority != TaskPriority.NONE) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        st.dueDateMillis?.let { due ->
                                            MetaPill(
                                                icon = Icons.Outlined.CalendarToday,
                                                text = DateFormatters.formatRelativeDueDate(due),
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (st.priority != TaskPriority.NONE) {
                                            MetaPill(
                                                icon = Icons.Outlined.Flag,
                                                text = st.priority.label,
                                                containerColor = prioColor.copy(alpha = 0.14f),
                                                contentColor = prioColor
                                            )
                                        }
                                    }
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { stMenuExpanded = true },
                                    modifier = Modifier.testTag("editor_subtask_options_${st.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(R.string.action_subtask_options),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = stMenuExpanded,
                                    onDismissRequest = { stMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (st.isCompleted) R.string.action_mark_incomplete
                                                    else R.string.action_mark_complete
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (st.isCompleted) {
                                                    Icons.Outlined.RadioButtonUnchecked
                                                } else {
                                                    Icons.Outlined.CheckCircle
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            stMenuExpanded = false
                                            subTasks = subTasks.map {
                                                if (it.id == st.id) it.copy(isCompleted = !it.isCompleted) else it
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_edit_subtask_options)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            stMenuExpanded = false
                                            editingSubTask = st
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
                                                Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            stMenuExpanded = false
                                            subTasks = subTasks.filterNot { it.id == st.id }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (subTasks.size < TaskConstraints.MAX_SUBTASKS) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newSubTaskText,
                            onValueChange = { next ->
                                if (next.length <= TaskConstraints.MAX_SUBTASK_LENGTH) {
                                    newSubTaskText = next
                                }
                            },
                            placeholder = { Text(stringResource(R.string.task_add_subtask_placeholder)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { addSubTask() }),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showAddSubTaskOptionsDialog = true },
                                    modifier = Modifier.testTag("task_subtask_options_button")
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
                                .testTag("task_subtask_input")
                        )
                        FilledTonalIconButton(
                            onClick = ::addSubTask,
                            enabled = newSubTaskText.isNotBlank(),
                            modifier = Modifier.testTag("task_add_subtask_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.action_add_subtask)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
