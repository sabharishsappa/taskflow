package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HabitAccent
import com.example.data.HabitConstraints
import com.example.data.HabitFrequency
import com.example.data.HabitItem
import com.example.ui.components.OptionDropdownField
import java.util.Calendar

@Composable
fun habitAccentColor(accent: HabitAccent): Color {
    return when (accent) {
        HabitAccent.SAGE -> MaterialTheme.colorScheme.primary
        HabitAccent.TERRACOTTA -> MaterialTheme.colorScheme.secondary
        HabitAccent.BRASS -> MaterialTheme.colorScheme.tertiary
        HabitAccent.CRIMSON -> MaterialTheme.colorScheme.error
        HabitAccent.SLATE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    existingHabit: HabitItem?,
    availableCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        category: String,
        accent: HabitAccent,
        frequency: HabitFrequency,
        scheduledDays: Set<Int>,
        weeklyTarget: Int,
        dailyGoalCount: Int,
        unitLabel: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existingHabit?.id) { mutableStateOf(existingHabit?.name.orEmpty()) }
    var description by remember(existingHabit?.id) { mutableStateOf(existingHabit?.description.orEmpty()) }
    var category by remember(existingHabit?.id) { mutableStateOf(existingHabit?.category ?: "Health") }
    var accent by remember(existingHabit?.id) { mutableStateOf(existingHabit?.accent ?: HabitAccent.SAGE) }
    var frequency by remember(existingHabit?.id) { mutableStateOf(existingHabit?.frequency ?: HabitFrequency.DAILY) }
    var scheduledDays by remember(existingHabit?.id) {
        mutableStateOf(existingHabit?.scheduledDays ?: HabitItem.ALL_WEEKDAYS)
    }
    var weeklyTarget by remember(existingHabit?.id) { mutableIntStateOf(existingHabit?.weeklyTarget ?: 5) }
    var dailyGoalCount by remember(existingHabit?.id) { mutableIntStateOf(existingHabit?.dailyGoalCount ?: 1) }
    var unitLabel by remember(existingHabit?.id) { mutableStateOf(existingHabit?.unitLabel.orEmpty()) }

    var colorMenuExpanded by remember { mutableStateOf(false) }
    var frequencyMenuExpanded by remember { mutableStateOf(false) }
    var weeklyMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showCustomCategoryInput by remember { mutableStateOf(false) }
    var customCategory by remember { mutableStateOf("") }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.habit_delete_dialog_title)) },
            text = { Text(stringResource(R.string.habit_delete_dialog_body)) },
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
                        if (existingHabit == null) R.string.habit_editor_new_title
                        else R.string.habit_editor_edit_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (existingHabit != null && onDelete != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.action_confirm_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                validationError = "Please enter a habit name."
                                return@Button
                            }
                            onSave(
                                name,
                                description,
                                category,
                                accent,
                                frequency,
                                scheduledDays,
                                weeklyTarget,
                                dailyGoalCount,
                                unitLabel
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("habit_save_button")
                    ) {
                        Text(stringResource(R.string.action_save))
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
                value = name,
                onValueChange = { next ->
                    if (next.length <= HabitConstraints.MAX_NAME_LENGTH) {
                        name = next
                        validationError = null
                    }
                },
                label = { Text(stringResource(R.string.habit_name_label)) },
                placeholder = { Text(stringResource(R.string.habit_name_placeholder)) },
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
                    .testTag("habit_name_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { next ->
                    if (next.length <= HabitConstraints.MAX_DESCRIPTION_LENGTH) description = next
                },
                label = { Text(stringResource(R.string.habit_description_label)) },
                placeholder = { Text(stringResource(R.string.habit_description_placeholder)) },
                minLines = 2,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_description_input")
            )

            // Row 1 of Dropdowns: Category & Accent Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val mergedCategories = remember(availableCategories, category) {
                    (availableCategories + listOf(category)).filter { it.isNotBlank() }.distinct()
                }
                OptionDropdownField(
                    label = stringResource(R.string.habit_category_label),
                    value = category,
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                    leadingIcon = Icons.Outlined.Folder,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("habit_category_dropdown")
                ) {
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

                OptionDropdownField(
                    label = stringResource(R.string.habit_color_label),
                    value = accent.label,
                    expanded = colorMenuExpanded,
                    onExpandedChange = { colorMenuExpanded = it },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = habitAccentColor(accent),
                            modifier = Modifier.size(18.dp)
                        ) {}
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("habit_color_dropdown")
                ) {
                    HabitAccent.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                Surface(
                                    shape = CircleShape,
                                    color = habitAccentColor(option),
                                    modifier = Modifier.size(16.dp)
                                ) {}
                            },
                            trailingIcon = if (accent == option) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                accent = option
                                colorMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (showCustomCategoryInput) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { next ->
                            if (next.length <= HabitConstraints.MAX_CATEGORY_LENGTH) customCategory = next
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
                                val cleaned = customCategory.trim()
                                if (cleaned.isNotEmpty()) {
                                    category = cleaned
                                    customCategory = ""
                                    showCustomCategoryInput = false
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val cleaned = customCategory.trim()
                            if (cleaned.isNotEmpty()) {
                                category = cleaned
                                customCategory = ""
                                showCustomCategoryInput = false
                            }
                        },
                        enabled = customCategory.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_set))
                    }
                }
            }

            // Schedule Dropdown
            OptionDropdownField(
                label = stringResource(R.string.habit_frequency_label),
                value = frequency.label,
                expanded = frequencyMenuExpanded,
                onExpandedChange = { frequencyMenuExpanded = it },
                leadingIcon = Icons.Outlined.Repeat,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_frequency_dropdown")
            ) {
                HabitFrequency.entries.forEach { freq ->
                    DropdownMenuItem(
                        text = { Text(freq.label) },
                        trailingIcon = if (frequency == freq) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            frequency = freq
                            frequencyMenuExpanded = false
                        }
                    )
                }
            }

            if (frequency == HabitFrequency.SPECIFIC_DAYS) {
                val weekdays = listOf(
                    Calendar.MONDAY to "M",
                    Calendar.TUESDAY to "T",
                    Calendar.WEDNESDAY to "W",
                    Calendar.THURSDAY to "T",
                    Calendar.FRIDAY to "F",
                    Calendar.SATURDAY to "S",
                    Calendar.SUNDAY to "S"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekdays.forEach { (dayConst, letter) ->
                        val active = dayConst in scheduledDays
                        Surface(
                            shape = CircleShape,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    scheduledDays = if (active && scheduledDays.size > 1) {
                                        scheduledDays - dayConst
                                    } else {
                                        scheduledDays + dayConst
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (active) {
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

            if (frequency == HabitFrequency.WEEKLY_TARGET) {
                OptionDropdownField(
                    label = stringResource(R.string.habit_weekly_target_label),
                    value = stringResource(R.string.habit_weekly_target_format, weeklyTarget),
                    expanded = weeklyMenuExpanded,
                    onExpandedChange = { weeklyMenuExpanded = it },
                    leadingIcon = Icons.Outlined.CalendarToday,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..7).forEach { target ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.habit_weekly_target_format, target)) },
                            trailingIcon = if (weeklyTarget == target) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                weeklyTarget = target
                                weeklyMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Daily Goal / Countable Target Section
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.habit_daily_goal_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (dailyGoalCount == 1) {
                                stringResource(R.string.habit_goal_simple)
                            } else {
                                stringResource(
                                    R.string.habit_goal_countable_format,
                                    dailyGoalCount,
                                    unitLabel.ifBlank { "times" }
                                )
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = { if (dailyGoalCount > 1) dailyGoalCount-- },
                            enabled = dailyGoalCount > 1
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = null)
                        }
                        FilledTonalIconButton(
                            onClick = {
                                if (dailyGoalCount < HabitConstraints.MAX_DAILY_GOAL) dailyGoalCount++
                            },
                            enabled = dailyGoalCount < HabitConstraints.MAX_DAILY_GOAL
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                    }
                }
            }

            if (dailyGoalCount > 1) {
                OutlinedTextField(
                    value = unitLabel,
                    onValueChange = { next ->
                        if (next.length <= HabitConstraints.MAX_UNIT_LENGTH) unitLabel = next
                    },
                    label = { Text(stringResource(R.string.habit_unit_label)) },
                    placeholder = { Text(stringResource(R.string.habit_unit_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
