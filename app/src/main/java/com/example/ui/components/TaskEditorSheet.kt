package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.DayDate
import com.example.model.ProjectColorPalette
import com.example.model.TaskItem
import com.example.model.TaskList
import com.example.ui.TaskEditorDraft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    draft: TaskEditorDraft,
    lists: List<TaskList>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onRequestCreateList: () -> Unit,
    onNotificationDenied: () -> Unit,
    onDelete: (TaskItem) -> Unit,
    onSave: (
        existingTask: TaskItem?,
        title: String,
        notes: String?,
        dueDate: DayDate,
        reminderEpochMillis: Long?,
        selectedListId: String?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val existing = draft.existingTask
    val today = remember { DayDate.today() }

    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var dueDate by remember(existing?.id, draft.prefillDate) {
        mutableStateOf(existing?.dueDate() ?: draft.prefillDate)
    }
    var selectedListId by remember(existing?.id, draft.prefillListId) {
        mutableStateOf(existing?.listId ?: draft.prefillListId ?: lists.firstOrNull()?.id)
    }

    // Auto-select newly created list if none was selected yet
    LaunchedEffect(lists) {
        if (selectedListId == null && lists.isNotEmpty()) {
            selectedListId = lists.first().id
        } else if (selectedListId != null && lists.isNotEmpty() && lists.none { it.id == selectedListId }) {
            selectedListId = lists.first().id
        }
    }

    val initialReminderPair = remember(existing?.id) {
        existing?.reminderEpochMillis?.let { DayDate.extractHourMinute(it) } ?: (9 to 0)
    }
    var reminderEnabled by remember(existing?.id) {
        mutableStateOf(existing?.reminderEpochMillis != null)
    }
    var reminderHour by remember(existing?.id) { mutableIntStateOf(initialReminderPair.first) }
    var reminderMinute by remember(existing?.id) { mutableIntStateOf(initialReminderPair.second) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reminderEnabled = true
        } else {
            reminderEnabled = false
            onNotificationDenied()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (existing == null) "New Task" else "Edit Task",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (existing != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_task_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete task",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Task Title & Notes
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 200) title = it },
                label = { Text("Task") },
                placeholder = { Text("What would you like to get done?") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input"),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 1000) notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Add details, links, or steps…") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_notes_input"),
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Due Date
            SectionMiniLabel(icon = Icons.Rounded.CalendarMonth, label = "Due Date")
            Spacer(modifier = Modifier.height(8.dp))

            val tomorrow = remember(today) { today.plusDays(1) }
            val nextWeek = remember(today) { today.plusDays(7) }
            val inTwoMonthsPlus = remember(today) { today.plusMonths(2).plusDays(7) }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuickDateChip(
                    label = "Today",
                    selected = dueDate == today,
                    onClick = { dueDate = today },
                    tag = "due_today_chip",
                )
                QuickDateChip(
                    label = "Tomorrow",
                    selected = dueDate == tomorrow,
                    onClick = { dueDate = tomorrow },
                    tag = "due_tomorrow_chip",
                )
                QuickDateChip(
                    label = "Next Week",
                    selected = dueDate == nextWeek,
                    onClick = { dueDate = nextWeek },
                    tag = "due_next_week_chip",
                )
                QuickDateChip(
                    label = "After 2 Mo",
                    selected = dueDate > today.plusMonths(2),
                    onClick = { dueDate = inTwoMonthsPlus },
                    tag = "due_after_two_months_chip",
                )
                FilterChip(
                    selected = dueDate != today && dueDate != tomorrow && dueDate != nextWeek && dueDate <= today.plusMonths(2),
                    onClick = { showDatePicker = true },
                    label = { Text(dueDate.formatFullDate(today.year)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("pick_custom_date_chip"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(14.dp))

            // 3. Reminder Notification
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Push Reminder",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (reminderEnabled) {
                                "${dueDate.formatShortDate()} at ${DayDate.formatTime(reminderHour, reminderMinute)}"
                            } else {
                                "Notify me on the due date"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { wantEnabled ->
                        if (!wantEnabled) {
                            reminderEnabled = false
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                reminderEnabled = true
                            } else {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            reminderEnabled = true
                        }
                    },
                    modifier = Modifier.testTag("reminder_toggle_switch"),
                )
            }

            AnimatedVisibility(visible = reminderEnabled) {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        Triple("9:00 AM", 9, 0),
                        Triple("1:00 PM", 13, 0),
                        Triple("6:00 PM", 18, 0),
                    ).forEach { (label, h, m) ->
                        FilterChip(
                            selected = reminderHour == h && reminderMinute == m,
                            onClick = {
                                reminderHour = h
                                reminderMinute = m
                            },
                            label = { Text(label) },
                        )
                    }
                    FilterChip(
                        selected = (reminderHour to reminderMinute) !in listOf(9 to 0, 13 to 0, 18 to 0),
                        onClick = { showTimePicker = true },
                        label = { Text("Custom (${DayDate.formatTime(reminderHour, reminderMinute)})") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.testTag("pick_reminder_time_chip"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(14.dp))

            // 4. List Assignment
            Text(
                text = "List",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lists.forEach { list ->
                    val listColor = ProjectColorPalette.parseColor(list.colorHex)
                    val isSelected = selectedListId == list.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedListId = list.id },
                        label = { Text(list.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(listColor)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = listColor.copy(alpha = 0.16f),
                            selectedLabelColor = listColor,
                        ),
                        modifier = Modifier.testTag("select_list_chip_${list.id}"),
                    )
                }

                if (lists.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            text = "A \"Personal\" list will be created automatically, or create one now:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                FilterChip(
                    selected = false,
                    onClick = onRequestCreateList,
                    label = { Text("New List") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("inline_create_list_chip"),
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Save Button
            Button(
                onClick = {
                    val reminderMillis = if (reminderEnabled) {
                        dueDate.toLocalMillis(reminderHour, reminderMinute)
                    } else {
                        null
                    }
                    onSave(
                        existing,
                        title.trim(),
                        notes.trim().takeIf { it.isNotEmpty() },
                        dueDate,
                        reminderMillis,
                        selectedListId,
                    )
                },
                enabled = title.trim().isNotEmpty() && !isSaving,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_task_button"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (existing == null) "Add Task" else stringResource(id = R.string.action_save),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.toUtcMidnightMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dueDate = DayDate.fromUtcMidnightMillis(millis)
                        }
                        showDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderHour = timePickerState.hour
                        reminderMinute = timePickerState.minute
                        showTimePicker = false
                    }
                ) { Text("Set Time") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this task?") },
            text = { Text("\"${existing.title}\" will be removed from your timeline and calendar.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(existing)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun QuickDateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun SectionMiniLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
