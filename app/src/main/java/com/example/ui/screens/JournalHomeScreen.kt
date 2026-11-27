package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.JournalEntry
import com.example.data.DailyBackupConfig
import com.example.ui.DateFormatters
import com.example.ui.JournalFilterState
import com.example.ui.JournalUiState
import com.example.ui.JournalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalHomeScreen(
    viewModel: JournalViewModel,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit,
    onCreateEntry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onEditEntry: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showDateFilterPicker by remember { mutableStateOf(false) }
    var selectedTabletEntryId by rememberSaveable { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    if (showDateFilterPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateFormatters.localMillisToUtcPickerMillis(
                filterState.selectedDateMillis ?: System.currentTimeMillis()
            )
        )
        DatePickerDialog(
            onDismissRequest = { showDateFilterPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedUtc ->
                            viewModel.selectDateFilter(
                                DateFormatters.utcPickerMillisToLocalMiddayMillis(selectedUtc)
                            )
                        }
                        showDateFilterPicker = false
                    },
                    modifier = Modifier.testTag("confirm_filter_date_button")
                ) {
                    Text(stringResource(R.string.action_confirm_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateFilterPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateEntry,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.action_new_entry),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("new_entry_fab")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedScreen = maxWidth >= 840.dp

            if (isExpandedScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    EventFeedColumn(
                        uiState = uiState,
                        filteredEntries = filteredEntries,
                        filterState = filterState,
                        availableTags = availableTags,
                        searchExpanded = searchExpanded,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.updateSearchQuery("")
                        },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onOpenDateFilter = { showDateFilterPicker = true },
                        onClearDateFilter = { viewModel.selectDateFilter(null) },
                        onSelectTag = viewModel::selectTagFilter,
                        onClearFilters = viewModel::clearFilters,
                        onRetry = viewModel::retryObserve,
                        onCreateEntry = onCreateEntry,
                        onEntryClick = { entryId -> selectedTabletEntryId = entryId },
                        selectedEntryId = selectedTabletEntryId,
                        isBackupBusy = isBackupBusy,
                        canExportBackup = canExportBackup,
                        dailyBackupConfig = dailyBackupConfig,
                        onExportZipClick = onExportZipClick,
                        onExportJsonClick = onExportJsonClick,
                        onImportClick = onImportClick,
                        onOpenBackupCenter = onOpenBackupCenter,
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                    )

                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )

                    Box(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f))
                    ) {
                        val activeEntry = selectedTabletEntryId?.let { id ->
                            filteredEntries.firstOrNull { it.id == id } ?: viewModel.findEntryById(id)
                        }
                        if (activeEntry != null) {
                            EntryDetailPane(
                                entry = activeEntry,
                                showBackButton = false,
                                onBack = {},
                                onEdit = { onEditEntry(activeEntry.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(activeEntry) },
                                onDelete = {
                                    viewModel.deleteEntry(activeEntry.id) {
                                        selectedTabletEntryId = null
                                    }
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.img_empty_pages_1795619040085),
                                        contentDescription = stringResource(R.string.empty_illustration_desc),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(220.dp)
                                            .aspectRatio(4f / 3f)
                                            .clip(RoundedCornerShape(20.dp))
                                    )
                                    Text(
                                        text = stringResource(R.string.empty_journal_title),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    EventFeedColumn(
                        uiState = uiState,
                        filteredEntries = filteredEntries,
                        filterState = filterState,
                        availableTags = availableTags,
                        searchExpanded = searchExpanded,
                        onToggleSearch = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.updateSearchQuery("")
                        },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onOpenDateFilter = { showDateFilterPicker = true },
                        onClearDateFilter = { viewModel.selectDateFilter(null) },
                        onSelectTag = viewModel::selectTagFilter,
                        onClearFilters = viewModel::clearFilters,
                        onRetry = viewModel::retryObserve,
                        onCreateEntry = onCreateEntry,
                        onEntryClick = onOpenEntry,
                        selectedEntryId = null,
                        isBackupBusy = isBackupBusy,
                        canExportBackup = canExportBackup,
                        dailyBackupConfig = dailyBackupConfig,
                        onExportZipClick = onExportZipClick,
                        onExportJsonClick = onExportJsonClick,
                        onImportClick = onImportClick,
                        onOpenBackupCenter = onOpenBackupCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventFeedColumn(
    uiState: JournalUiState,
    filteredEntries: List<JournalEntry>,
    filterState: JournalFilterState,
    availableTags: List<String>,
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenDateFilter: () -> Unit,
    onClearDateFilter: () -> Unit,
    onSelectTag: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onCreateEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    selectedEntryId: String?,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Group entries strictly by calendar date (most recent date first)
    val groupedByDate = remember(filteredEntries) {
        filteredEntries.groupBy { DateFormatters.dayKey(it.eventDateMillis) }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 104.dp)
    ) {
        item(key = "header") {
            EventLogHeader(
                searchExpanded = searchExpanded,
                isBackupBusy = isBackupBusy,
                canExportBackup = canExportBackup,
                dailyBackupConfig = dailyBackupConfig,
                onToggleSearch = onToggleSearch,
                onExportZipClick = onExportZipClick,
                onExportJsonClick = onExportJsonClick,
                onImportClick = onImportClick,
                onOpenBackupCenter = onOpenBackupCenter
            )
        }

        if (isBackupBusy) {
            item(key = "backup_progress") {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }

        item(key = "search_bar") {
            AnimatedVisibility(
                visible = searchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = filterState.query,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (filterState.query.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
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
                        .testTag("search_input")
                )
            }
        }

        if (uiState is JournalUiState.Success && uiState.entries.isNotEmpty()) {
            item(key = "filter_bar") {
                DateAndTagFilterRow(
                    filterState = filterState,
                    availableTags = availableTags,
                    onOpenDateFilter = onOpenDateFilter,
                    onClearDateFilter = onClearDateFilter,
                    onSelectTag = onSelectTag,
                    onClearFilters = onClearFilters
                )
            }
        }

        when (uiState) {
            JournalUiState.Loading -> {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            is JournalUiState.Error -> {
                item(key = "error") {
                    ErrorEventState(
                        message = uiState.message,
                        onRetry = onRetry
                    )
                }
            }

            is JournalUiState.Success -> {
                if (uiState.entries.isEmpty()) {
                    item(key = "empty_first") {
                        EmptyFirstEventState(onCreateEntry = onCreateEntry)
                    }
                } else if (filteredEntries.isEmpty()) {
                    item(key = "empty_filtered") {
                        EmptyFilteredState(onClearFilters = onClearFilters)
                    }
                } else {
                    groupedByDate.forEach { (dayStartMillis, dayEntries) ->
                        item(key = "date_group_$dayStartMillis") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = DateFormatters.formatDateGroupHeader(dayStartMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }

                        items(
                            items = dayEntries,
                            key = { it.id }
                        ) { entry ->
                            CompactEventRow(
                                entry = entry,
                                isSelected = entry.id == selectedEntryId,
                                onClick = { onEntryClick(entry.id) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLogHeader(
    searchExpanded: Boolean,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onToggleSearch: () -> Unit,
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
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.testTag("search_toggle_button")
            ) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            BackupMenuButton(
                isBackupBusy = isBackupBusy,
                canExportBackup = canExportBackup,
                dailyBackupConfig = dailyBackupConfig,
                onExportZipClick = onExportZipClick,
                onExportJsonClick = onExportJsonClick,
                onImportClick = onImportClick,
                onOpenBackupCenter = onOpenBackupCenter,
                buttonTestTag = "backup_menu_button"
            )
        }
    }
}

@Composable
private fun DateAndTagFilterRow(
    filterState: JournalFilterState,
    availableTags: List<String>,
    onOpenDateFilter: () -> Unit,
    onClearDateFilter: () -> Unit,
    onSelectTag: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    var tagMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date Filter Chip
        val selectedDate = filterState.selectedDateMillis
        FilterChip(
            selected = selectedDate != null,
            onClick = onOpenDateFilter,
            label = {
                Text(
                    text = if (selectedDate != null) {
                        DateFormatters.formatShortDate(selectedDate)
                    } else {
                        stringResource(R.string.filter_date)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = if (selectedDate != null) {
                {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.filter_clear_date),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onClearDateFilter)
                    )
                }
            } else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
                selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.testTag("filter_date_chip")
        )

        // Tag Filter Chip + Dropdown Menu
        val selectedTag = filterState.selectedTag
        Box {
            FilterChip(
                selected = selectedTag != null,
                onClick = { tagMenuExpanded = true },
                label = {
                    Text(
                        text = if (selectedTag != null) "#$selectedTag"
                        else stringResource(R.string.filter_tag),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Label,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = if (selectedTag != null) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.filter_clear_tag),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSelectTag(null) }
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("filter_tag_chip")
            )

            DropdownMenu(
                expanded = tagMenuExpanded,
                onDismissRequest = { tagMenuExpanded = false }
            ) {
                if (availableTags.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.filter_no_tags_yet),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { tagMenuExpanded = false },
                        enabled = false
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_all_tags)) },
                        trailingIcon = if (selectedTag == null) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            tagMenuExpanded = false
                            onSelectTag(null)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    availableTags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text("#$tag") },
                            trailingIcon = if (selectedTag == tag) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                tagMenuExpanded = false
                                onSelectTag(tag)
                            },
                            modifier = Modifier.testTag("filter_tag_option_$tag")
                        )
                    }
                }
            }
        }

        if (filterState.hasActiveFilters) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onClearFilters,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.testTag("clear_all_filters_button")
            ) {
                Text(
                    text = stringResource(R.string.action_clear_filters),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CompactEventRow(
    entry: JournalEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("entry_card_${entry.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = DateFormatters.formatCompactDate(entry.eventDateMillis),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            Text(
                text = entry.heading,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EmptyFirstEventState(
    onCreateEntry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_pages_1795619040085),
            contentDescription = stringResource(R.string.empty_illustration_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(24.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.empty_journal_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.empty_journal_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onCreateEntry,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("empty_write_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.empty_journal_cta))
        }
    }
}

@Composable
private fun EmptyFilteredState(
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.FilterListOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_filter_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_filter_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        OutlinedButton(
            onClick = onClearFilters,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("reset_filters_button")
        ) {
            Text(stringResource(R.string.action_clear_filters))
        }
    }
}

@Composable
private fun ErrorEventState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.error_load_entries_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("retry_button")
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
