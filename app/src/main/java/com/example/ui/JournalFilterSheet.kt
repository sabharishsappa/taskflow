package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.viewmodel.DateFilterPreset
import com.example.viewmodel.JournalFilterState
import com.example.viewmodel.TagMatchMode
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalFilterSheet(
    filter: JournalFilterState,
    allTags: List<String>,
    matchedCount: Int,
    onToggleTag: (String) -> Unit,
    onTagMatchModeChange: (TagMatchMode) -> Unit,
    onDatePresetSelect: (DateFilterPreset) -> Unit,
    onCustomDateRangeSelect: (Long, Long?) -> Unit,
    onToggleBookmarkedOnly: () -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDateRangePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.filter_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (filter.activeFilterCount > 0) {
                    TextButton(
                        onClick = onResetFilters,
                        modifier = Modifier.testTag("filter_sheet_reset_top"),
                    ) {
                        Text(stringResource(R.string.filter_reset))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 0.75.dp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Date Filter Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.filter_date_header).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presets = listOf(
                            DateFilterPreset.ANY to R.string.filter_date_any,
                            DateFilterPreset.TODAY to R.string.filter_date_today,
                            DateFilterPreset.LAST_7_DAYS to R.string.filter_date_7_days,
                            DateFilterPreset.LAST_30_DAYS to R.string.filter_date_30_days,
                            DateFilterPreset.THIS_MONTH to R.string.filter_date_this_month,
                        )
                        presets.forEach { (preset, labelRes) ->
                            val selected = filter.datePreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onDatePresetSelect(preset) },
                                label = { Text(stringResource(labelRes)) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier.testTag("date_preset_${preset.name.lowercase(Locale.US)}"),
                            )
                        }

                        // Custom Date / Date Range Picker Chip
                        val isCustom = filter.datePreset == DateFilterPreset.CUSTOM && filter.customStartMillis != null
                        val customLabel = if (isCustom && filter.customStartMillis != null) {
                            JournalDateFormatter.formatShortDateRange(
                                filter.customStartMillis,
                                filter.customEndMillis,
                            )
                        } else {
                            stringResource(R.string.filter_date_custom)
                        }
                        FilterChip(
                            selected = isCustom,
                            onClick = { showDateRangePicker = true },
                            label = { Text(customLabel) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.testTag("date_preset_custom"),
                        )
                    }
                }

                // Multi-Select Tags Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.filter_tags_header).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (filter.selectedTags.size > 1) {
                            TagMatchModeToggle(
                                mode = filter.tagMatchMode,
                                onModeChange = onTagMatchModeChange,
                            )
                        }
                    }

                    if (allTags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.filter_tags_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            allTags.forEach { tag ->
                                val selected = filter.selectedTags.any { it.equals(tag, ignoreCase = true) }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleTag(tag) },
                                    label = { Text("#$tag") },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.testTag("filter_tag_${tag.lowercase(Locale.US)}"),
                                )
                            }
                        }
                    }
                }

                // Bookmarked Toggle Row
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleBookmarkedOnly() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (filter.bookmarkedOnly) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = if (filter.bookmarkedOnly) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.filter_bookmarked_only),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = filter.bookmarkedOnly,
                            onCheckedChange = { onToggleBookmarkedOnly() },
                            modifier = Modifier.testTag("filter_bookmarked_switch"),
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 0.75.dp,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Footer Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onResetFilters,
                    enabled = filter.activeFilterCount > 0,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("filter_sheet_reset_button"),
                ) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1.6f)
                        .heightIn(min = 48.dp)
                        .testTag("filter_sheet_apply_button"),
                ) {
                    Text("Show $matchedCount entr${if (matchedCount == 1) "y" else "ies"}")
                }
            }
        }
    }

    if (showDateRangePicker) {
        val initialStartUtc = remember(filter.customStartMillis) {
            filter.customStartMillis?.let { localMillisToUtcPickerMillis(it) }
        }
        val initialEndUtc = remember(filter.customEndMillis) {
            filter.customEndMillis?.let { localMillisToUtcPickerMillis(it) }
        }
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialStartUtc,
            initialSelectedEndDateMillis = initialEndUtc,
        )

        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null,
                    onClick = {
                        val startUtc = rangeState.selectedStartDateMillis
                        if (startUtc != null) {
                            val startLocal = utcPickerMillisToLocalNoon(startUtc)
                            val endLocal = rangeState.selectedEndDateMillis?.let { utcPickerMillisToLocalNoon(it) }
                            onCustomDateRangeSelect(startLocal, endLocal)
                        }
                        showDateRangePicker = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DateRangePicker(
                state = rangeState,
                title = {
                    Text(
                        text = stringResource(R.string.filter_date_custom),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp),
            )
        }
    }
}

@Composable
private fun TagMatchModeToggle(
    mode: TagMatchMode,
    onModeChange: (TagMatchMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            listOf(
                TagMatchMode.ANY to R.string.filter_match_any,
                TagMatchMode.ALL to R.string.filter_match_all,
            ).forEach { (option, labelRes) ->
                val active = mode == option
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onModeChange(option) }
                        .testTag("tag_mode_${option.name.lowercase(Locale.US)}"),
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

fun formatActiveDateFilterLabel(filter: JournalFilterState): String? {
    return when (filter.datePreset) {
        DateFilterPreset.ANY -> null
        DateFilterPreset.TODAY -> "Today"
        DateFilterPreset.LAST_7_DAYS -> "Last 7 Days"
        DateFilterPreset.LAST_30_DAYS -> "Last 30 Days"
        DateFilterPreset.THIS_MONTH -> "This Month"
        DateFilterPreset.CUSTOM -> {
            val start = filter.customStartMillis ?: return null
            JournalDateFormatter.formatShortDateRange(start, filter.customEndMillis)
        }
    }
}

private fun localMillisToUtcPickerMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
        )
    }
    return utcCal.timeInMillis
}

private fun utcPickerMillisToLocalNoon(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val localCal = Calendar.getInstance().apply {
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }
    return localCal.timeInMillis
}
