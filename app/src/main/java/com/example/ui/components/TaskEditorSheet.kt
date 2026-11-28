package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.RepeatRule
import com.example.data.TaskColor
import com.example.data.TaskDateUtils
import com.example.data.TaskRepository
import com.example.ui.TaskEditorDraft
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrismAmber
import com.example.ui.theme.PrismCoral
import com.example.ui.theme.PrismSky
import com.example.ui.theme.PrismTeal
import java.util.Calendar

private enum class ScheduleSection {
    DUE_DATE,
    REMINDER,
    REPEAT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorContent(
    draft: TaskEditorDraft,
    onUpdateDraft: ((TaskEditorDraft) -> TaskEditorDraft) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (String) -> Unit,
    onRemoveSubtask: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
    onNotificationPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val activePalette = draft.color.palette(isDark)

    var subtaskInput by remember { mutableStateOf("") }
    var expandedSection by remember { mutableStateOf<ScheduleSection?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingReminderAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onNotificationPermissionDenied()
        }
        pendingReminderAction?.invoke()
        pendingReminderAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                action()
            } else {
                pendingReminderAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            action()
        }
    }

    fun toggleSection(section: ScheduleSection) {
        expandedSection = if (expandedSection == section) null else section
    }

    val cleanFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // 1. Clean Top Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(activePalette.accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (draft.isNew) {
                    stringResource(R.string.new_task)
                } else {
                    stringResource(R.string.edit_task)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_task_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_task),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_editor_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel)
                )
            }
        }

        // 2. Scrollable Clean Grouped Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(visible = draft.validationError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = draft.validationError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // Card A: Unified Title + Notes + Color Strip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = activePalette.accent.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = draft.title,
                        onValueChange = { value ->
                            onUpdateDraft { it.copy(title = value.take(TaskRepository.MAX_TITLE_LENGTH)) }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.task_title_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        colors = cleanFieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .testTag("task_title_input")
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    TextField(
                        value = draft.notes,
                        onValueChange = { value ->
                            onUpdateDraft { it.copy(notes = value.take(TaskRepository.MAX_NOTES_LENGTH)) }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.task_notes_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        minLines = 1,
                        maxLines = 3,
                        colors = cleanFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .testTag("task_notes_input")
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Evenly aligned accent color strip
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        TaskColor.entries.forEach { taskColor ->
                            val accent = taskColor.palette(isDark).accent
                            val isSelected = draft.color == taskColor
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onUpdateDraft { it.copy(color = taskColor) } }
                                    .testTag("editor_color_${taskColor.name.lowercase()}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(if (isSelected) 32.dp else 24.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = taskColor.label,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card B: Cleanly Aligned Schedule Group (Due Date, Reminder, Repetition)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Due Date
                    ScheduleOptionRow(
                        icon = Icons.Rounded.CalendarMonth,
                        accent = PrismSky,
                        label = stringResource(R.string.section_due_date),
                        valueText = draft.dueDateMillis?.let { TaskDateUtils.formatDueDate(it) },
                        isExpanded = expandedSection == ScheduleSection.DUE_DATE,
                        onRowClick = { toggleSection(ScheduleSection.DUE_DATE) },
                        onClear = if (draft.dueDateMillis != null) {
                            { onUpdateDraft { it.copy(dueDateMillis = null) } }
                        } else null,
                        testTag = "schedule_row_due_date"
                    )

                    AnimatedVisibility(
                        visible = expandedSection == ScheduleSection.DUE_DATE,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val todayMillis = remember { TaskDateUtils.daysFromTodayMillis(0) }
                        val tomorrowMillis = remember { TaskDateUtils.daysFromTodayMillis(1) }
                        val nextWeekMillis = remember { TaskDateUtils.daysFromTodayMillis(7) }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        ) {
                            UniformOptionPill(
                                label = stringResource(R.string.preset_today),
                                selected = TaskDateUtils.isDueToday(draft.dueDateMillis),
                                accent = PrismTeal,
                                onClick = {
                                    onUpdateDraft { it.copy(dueDateMillis = todayMillis) }
                                    expandedSection = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("due_preset_today")
                            )
                            UniformOptionPill(
                                label = stringResource(R.string.preset_tomorrow),
                                selected = draft.dueDateMillis?.let { TaskDateUtils.formatDueDate(it) == "Tomorrow" } == true,
                                accent = PrismSky,
                                onClick = {
                                    onUpdateDraft { it.copy(dueDateMillis = tomorrowMillis) }
                                    expandedSection = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("due_preset_tomorrow")
                            )
                            UniformOptionPill(
                                label = "+7d",
                                selected = false,
                                accent = activePalette.accent,
                                onClick = {
                                    onUpdateDraft { it.copy(dueDateMillis = nextWeekMillis) }
                                    expandedSection = null
                                },
                                modifier = Modifier
                                    .weight(0.75f)
                                    .testTag("due_preset_next_week")
                            )
                            UniformOptionPill(
                                label = "Pick",
                                selected = false,
                                accent = PrismSky,
                                outlined = true,
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .weight(0.85f)
                                    .testTag("due_pick_date_button")
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Row 2: Reminder
                    ScheduleOptionRow(
                        icon = Icons.Rounded.Alarm,
                        accent = PrismCoral,
                        label = stringResource(R.string.section_reminder),
                        valueText = draft.reminderMillis?.let { TaskDateUtils.formatReminder(it) },
                        isExpanded = expandedSection == ScheduleSection.REMINDER,
                        onRowClick = { toggleSection(ScheduleSection.REMINDER) },
                        onClear = if (draft.reminderMillis != null) {
                            { onUpdateDraft { it.copy(reminderMillis = null) } }
                        } else null,
                        testTag = "schedule_row_reminder"
                    )

                    AnimatedVisibility(
                        visible = expandedSection == ScheduleSection.REMINDER,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        ) {
                            // Dedicated side-by-side Date & Time picker buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ReminderPickerTile(
                                    icon = Icons.Rounded.CalendarMonth,
                                    label = draft.reminderMillis?.let { TaskDateUtils.formatDueDate(it) }
                                        ?: stringResource(R.string.preset_pick_date),
                                    hasValue = draft.reminderMillis != null,
                                    accent = PrismCoral,
                                    onClick = {
                                        runWithNotificationPermission { showReminderDatePicker = true }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reminder_pick_date_button")
                                )
                                ReminderPickerTile(
                                    icon = Icons.Rounded.Alarm,
                                    label = draft.reminderMillis?.let { TaskDateUtils.formatTimeOnly(it) }
                                        ?: stringResource(R.string.preset_pick_time),
                                    hasValue = draft.reminderMillis != null,
                                    accent = PrismCoral,
                                    onClick = {
                                        runWithNotificationPermission { showTimePicker = true }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reminder_pick_time_button")
                                )
                            }

                            // Quick 1-tap presets
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                UniformOptionPill(
                                    label = stringResource(R.string.preset_in_one_hour),
                                    selected = false,
                                    accent = PrismCoral,
                                    onClick = {
                                        runWithNotificationPermission {
                                            val inOneHour = System.currentTimeMillis() + 3_600_000L
                                            onUpdateDraft { it.copy(reminderMillis = inOneHour) }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reminder_in_1hr")
                                )
                                UniformOptionPill(
                                    label = stringResource(R.string.preset_tonight),
                                    selected = false,
                                    accent = PrismCoral,
                                    onClick = {
                                        runWithNotificationPermission {
                                            var tonight = TaskDateUtils.combineDateAndTime(
                                                dateMillis = TaskDateUtils.daysFromTodayMillis(0),
                                                hourOfDay = 20,
                                                minute = 0
                                            )
                                            if (tonight <= System.currentTimeMillis()) {
                                                tonight = TaskDateUtils.combineDateAndTime(
                                                    dateMillis = TaskDateUtils.daysFromTodayMillis(1),
                                                    hourOfDay = 20,
                                                    minute = 0
                                                )
                                            }
                                            onUpdateDraft { it.copy(reminderMillis = tonight) }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reminder_tonight")
                                )
                                UniformOptionPill(
                                    label = stringResource(R.string.preset_tomorrow_morning),
                                    selected = false,
                                    accent = PrismCoral,
                                    onClick = {
                                        runWithNotificationPermission {
                                            val tomorrow9am = TaskDateUtils.combineDateAndTime(
                                                dateMillis = TaskDateUtils.daysFromTodayMillis(1),
                                                hourOfDay = 9,
                                                minute = 0
                                            )
                                            onUpdateDraft { it.copy(reminderMillis = tomorrow9am) }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reminder_tomorrow_9am")
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Row 3: Repetition
                    ScheduleOptionRow(
                        icon = Icons.Rounded.Repeat,
                        accent = PrismAmber,
                        label = stringResource(R.string.section_repetition),
                        valueText = if (draft.repeatRule != RepeatRule.NONE) {
                            draft.repeatRule.formatBadge(draft.repeatInterval)
                        } else null,
                        isExpanded = expandedSection == ScheduleSection.REPEAT,
                        onRowClick = { toggleSection(ScheduleSection.REPEAT) },
                        onClear = if (draft.repeatRule != RepeatRule.NONE) {
                            { onUpdateDraft { it.copy(repeatRule = RepeatRule.NONE, repeatInterval = 1) } }
                        } else null,
                        testTag = "schedule_row_repeat"
                    )

                    AnimatedVisibility(
                        visible = expandedSection == ScheduleSection.REPEAT,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                        ) {
                            // 2 neat aligned rows for the 5 repeat options
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(RepeatRule.NONE, RepeatRule.DAILY, RepeatRule.WEEKDAYS).forEach { rule ->
                                    UniformOptionPill(
                                        label = rule.label,
                                        selected = draft.repeatRule == rule,
                                        accent = PrismAmber,
                                        onClick = { selectRepeatRule(rule, onUpdateDraft) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("repeat_option_${rule.name.lowercase()}")
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(RepeatRule.WEEKLY, RepeatRule.MONTHLY).forEach { rule ->
                                    UniformOptionPill(
                                        label = rule.label,
                                        selected = draft.repeatRule == rule,
                                        accent = PrismAmber,
                                        onClick = { selectRepeatRule(rule, onUpdateDraft) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("repeat_option_${rule.name.lowercase()}")
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = draft.repeatRule == RepeatRule.DAILY ||
                                    draft.repeatRule == RepeatRule.WEEKLY ||
                                    draft.repeatRule == RepeatRule.MONTHLY
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        val unit = if (draft.repeatInterval == 1) {
                                            draft.repeatRule.unitSingular
                                        } else {
                                            draft.repeatRule.unitPlural
                                        }
                                        Text(
                                            text = "${stringResource(R.string.every_label)} ${draft.repeatInterval} $unit",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    onUpdateDraft {
                                                        it.copy(repeatInterval = (it.repeatInterval - 1).coerceAtLeast(1))
                                                    }
                                                },
                                                enabled = draft.repeatInterval > 1,
                                                modifier = Modifier.testTag("repeat_interval_minus")
                                            ) {
                                                Icon(Icons.Rounded.Remove, contentDescription = "Decrease interval")
                                            }
                                            Text(
                                                text = draft.repeatInterval.toString(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    onUpdateDraft {
                                                        it.copy(repeatInterval = (it.repeatInterval + 1).coerceAtMost(365))
                                                    }
                                                },
                                                enabled = draft.repeatInterval < 365,
                                                modifier = Modifier.testTag("repeat_interval_plus")
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = "Increase interval")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card C: Unified Clean Subtasks Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(activePalette.accent.copy(alpha = 0.14f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = activePalette.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.section_subtasks),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (draft.subtasks.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = activePalette.accent.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${draft.subtasks.size}/${TaskRepository.MAX_SUBTASKS}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activePalette.accent,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    draft.subtasks.forEach { sub ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSubtask(sub.id) }
                                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (sub.done) activePalette.accent else Color.Transparent)
                                    .border(1.8.dp, activePalette.accent, RoundedCornerShape(7.dp))
                            ) {
                                if (sub.done) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = sub.title,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (sub.done) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveSubtask(sub.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.remove_subtask_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (draft.subtasks.isNotEmpty()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Clean inline borderless add-subtask row aligned with items above
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 10.dp, bottom = 4.dp)
                    ) {
                        TextField(
                            value = subtaskInput,
                            onValueChange = { subtaskInput = it.take(TaskRepository.MAX_SUBTASK_TITLE_LENGTH) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.add_subtask_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            colors = cleanFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (subtaskInput.isNotBlank()) {
                                        onAddSubtask(subtaskInput)
                                        subtaskInput = ""
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("subtask_input")
                        )

                        Surface(
                            onClick = {
                                if (subtaskInput.isNotBlank()) {
                                    onAddSubtask(subtaskInput)
                                    subtaskInput = ""
                                }
                            },
                            shape = CircleShape,
                            color = if (subtaskInput.isNotBlank()) {
                                activePalette.accent
                            } else {
                                activePalette.accent.copy(alpha = 0.18f)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("add_subtask_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.add_subtask_desc),
                                    tint = if (subtaskInput.isNotBlank()) Color.White else activePalette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // 3. Sticky Bottom Save Bar (Always aligned and 1 tap away)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = !draft.isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activePalette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_task_button")
            ) {
                if (draft.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (draft.isNew) {
                            stringResource(R.string.create_task)
                        } else {
                            stringResource(R.string.save_task)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Material 3 Date Picker Dialog for Due Date
    if (showDatePicker) {
        val initialUtc = remember(draft.dueDateMillis) {
            TaskDateUtils.localMillisToUtcMidnight(
                draft.dueDateMillis ?: System.currentTimeMillis()
            )
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val localMidday = TaskDateUtils.utcDatePickerMillisToLocalMidday(utcMillis)
                            onUpdateDraft { it.copy(dueDateMillis = localMidday) }
                            expandedSection = null
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 Date Picker Dialog for Reminder Date
    if (showReminderDatePicker) {
        val initialUtc = remember(draft.reminderMillis, draft.dueDateMillis) {
            TaskDateUtils.localMillisToUtcMidnight(
                draft.reminderMillis ?: draft.dueDateMillis ?: System.currentTimeMillis()
            )
        }
        val reminderDateState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showReminderDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderDateState.selectedDateMillis?.let { utcMillis ->
                            val pickedDateMidday = TaskDateUtils.utcDatePickerMillisToLocalMidday(utcMillis)
                            val (hour, minute) = if (draft.reminderMillis != null) {
                                val existingCal = Calendar.getInstance().apply {
                                    timeInMillis = draft.reminderMillis
                                }
                                existingCal.get(Calendar.HOUR_OF_DAY) to existingCal.get(Calendar.MINUTE)
                            } else {
                                val nineAmCandidate = TaskDateUtils.combineDateAndTime(
                                    dateMillis = pickedDateMidday,
                                    hourOfDay = 9,
                                    minute = 0
                                )
                                if (nineAmCandidate > System.currentTimeMillis()) {
                                    9 to 0
                                } else {
                                    val nextHour = Calendar.getInstance().apply {
                                        add(Calendar.HOUR_OF_DAY, 1)
                                        set(Calendar.MINUTE, 0)
                                    }
                                    nextHour.get(Calendar.HOUR_OF_DAY) to 0
                                }
                            }
                            val combined = TaskDateUtils.combineDateAndTime(
                                dateMillis = pickedDateMidday,
                                hourOfDay = hour,
                                minute = minute
                            )
                            onUpdateDraft { it.copy(reminderMillis = combined) }
                        }
                        showReminderDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(
                state = reminderDateState,
                title = {
                    Text(
                        text = stringResource(R.string.reminder_date_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }

    // Material 3 Time Picker Dialog for Reminder Time
    if (showTimePicker) {
        val defaultCal = remember(draft.reminderMillis) {
            Calendar.getInstance().apply {
                if (draft.reminderMillis != null) {
                    timeInMillis = draft.reminderMillis
                } else {
                    add(Calendar.HOUR_OF_DAY, 1)
                    set(Calendar.MINUTE, 0)
                }
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = defaultCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = defaultCal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.reminder_time_dialog_title)) },
            text = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val explicitDate = draft.reminderMillis ?: draft.dueDateMillis
                        val baseDate = explicitDate ?: System.currentTimeMillis()
                        var candidate = TaskDateUtils.combineDateAndTime(
                            dateMillis = baseDate,
                            hourOfDay = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        if (explicitDate == null && candidate <= System.currentTimeMillis()) {
                            candidate = TaskDateUtils.combineDateAndTime(
                                dateMillis = TaskDateUtils.daysFromTodayMillis(1),
                                hourOfDay = timePickerState.hour,
                                minute = timePickerState.minute
                            )
                        }
                        onUpdateDraft { it.copy(reminderMillis = candidate) }
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun selectRepeatRule(
    rule: RepeatRule,
    onUpdateDraft: ((TaskEditorDraft) -> TaskEditorDraft) -> Unit
) {
    onUpdateDraft { current ->
        val ensureDue = if (rule != RepeatRule.NONE && current.dueDateMillis == null) {
            TaskDateUtils.daysFromTodayMillis(0)
        } else {
            current.dueDateMillis
        }
        current.copy(
            repeatRule = rule,
            repeatInterval = if (rule == RepeatRule.WEEKDAYS) 1 else current.repeatInterval,
            dueDateMillis = ensureDue
        )
    }
}

@Composable
private fun ScheduleOptionRow(
    icon: ImageVector,
    accent: Color,
    label: String,
    valueText: String?,
    isExpanded: Boolean,
    onRowClick: () -> Unit,
    onClear: (() -> Unit)?,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (!valueText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = accent.copy(alpha = 0.15f)
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            if (onClear != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun UniformOptionPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    val bgColor = when {
        selected -> accent
        outlined -> Color.Transparent
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        selected -> Color.White
        outlined -> accent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        selected -> Color.Transparent
        outlined -> accent.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
            .height(40.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReminderPickerTile(
    icon: ImageVector,
    label: String,
    hasValue: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (hasValue) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
    val borderColor = if (hasValue) accent.copy(alpha = 0.55f) else accent.copy(alpha = 0.35f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
            .height(44.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (hasValue) MaterialTheme.colorScheme.onSurface else accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
