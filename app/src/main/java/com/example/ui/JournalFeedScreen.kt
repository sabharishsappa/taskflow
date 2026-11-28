package com.example.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AttachmentStorageHelper
import com.example.data.DefaultReflectionPrompts
import com.example.data.JournalAttachment
import com.example.data.JournalEntry
import com.example.data.JournalStats
import com.example.data.ReflectionPrompt
import com.example.viewmodel.DateFilterPreset
import com.example.viewmodel.JournalFeedUiModel
import com.example.viewmodel.JournalFilterState
import com.example.viewmodel.SearchScope
import com.example.viewmodel.TagMatchMode
import com.example.viewmodel.UiState
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun JournalFeedScreen(
    feedState: UiState<JournalFeedUiModel>,
    selectedEntryId: String?,
    snackbarHostState: SnackbarHostState,
    onNewEntry: (ReflectionPrompt?) -> Unit,
    onSelectEntry: (JournalEntry) -> Unit,
    onEditEntry: (JournalEntry) -> Unit,
    onToggleBookmark: (JournalEntry) -> Unit,
    onDeleteEntry: (JournalEntry) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchScopeChange: (SearchScope) -> Unit,
    onToggleTag: (String) -> Unit,
    onTagMatchModeChange: (TagMatchMode) -> Unit,
    onDatePresetSelect: (DateFilterPreset) -> Unit,
    onCustomDateRangeSelect: (Long, Long?) -> Unit,
    onClearDateFilter: () -> Unit,
    onToggleBookmarkedFilter: () -> Unit,
    onClearNonSearchFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var entryPendingDelete by remember { mutableStateOf<JournalEntry?>(null) }
    var viewerPhoto by remember { mutableStateOf<JournalAttachment?>(null) }

    val model = (feedState as? UiState.Success)?.data
    val filter = model?.filter ?: JournalFilterState()
    val searchQuery = filter.searchQuery
    val searchScope = filter.searchScope
    val isSearchActive = searchQuery.isNotBlank()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNewEntry(null) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp,
                ),
                modifier = Modifier
                    .navigationBarsPadding()
                    .size(58.dp)
                    .testTag("new_entry_fab"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.cd_new_entry),
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            // Compact Editorial Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.journal_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Compact Search Bar + Filter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactSearchBar(
                    query = searchQuery,
                    isFocused = isSearchFocused,
                    onQueryChange = onSearchQueryChange,
                    onFocusChanged = { isSearchFocused = it },
                    onSearchAction = { focusManager.clearFocus() },
                    onClear = {
                        onSearchQueryChange("")
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.weight(1f),
                )

                CompactFilterButton(
                    activeCount = filter.activeFilterCount,
                    onClick = {
                        focusManager.clearFocus()
                        showFilterSheet = true
                    },
                )
            }

            // Search Scope Selector & Match Count (revealed while searching or focused)
            AnimatedVisibility(
                visible = isSearchFocused || isSearchActive || searchScope != SearchScope.ALL,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SearchScopeBar(
                    selectedScope = searchScope,
                    matchedCount = model?.matchedCount,
                    isQueryActive = isSearchActive,
                    onSelectScope = onSearchScopeChange,
                )
            }

            // Active Filter Pills Summary (Date, Multiple Tags, Bookmarked)
            AnimatedVisibility(
                visible = filter.activeFilterCount > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ActiveFiltersStrip(
                    filter = filter,
                    onClearDate = onClearDateFilter,
                    onRemoveTag = onToggleTag,
                    onClearBookmarked = onToggleBookmarkedFilter,
                    onClearAll = onClearNonSearchFilters,
                )
            }

            when (feedState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Couldn't Load Journal",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = feedState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val data = feedState.data
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 104.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Compact One-Line Stats Strip
                        if (data.allEntries.isNotEmpty() && !isSearchActive && filter.activeFilterCount == 0) {
                            item(key = "compact_stats") {
                                CompactStatsStrip(stats = data.stats)
                            }
                        }

                        // Compact Quick Multi-Select Tag Bar (when tags exist and no active strip clutter)
                        if (data.allTags.isNotEmpty() && !isSearchActive) {
                            item(key = "quick_tag_bar") {
                                QuickTagRibbon(
                                    allTags = data.allTags,
                                    selectedTags = data.filter.selectedTags,
                                    onToggleTag = onToggleTag,
                                )
                            }
                        }

                        if (data.allEntries.isEmpty()) {
                            item(key = "empty_journal") {
                                EmptyJournalState(onStartPrompt = onNewEntry)
                            }
                        } else if (data.filteredGroups.isEmpty()) {
                            item(key = "no_matches") {
                                NoMatchingEntriesCard(onClearFilters = onClearFilters)
                            }
                        } else {
                            data.filteredGroups.forEach { group ->
                                item(key = "date_header_${group.dayKey}") {
                                    DateSectionHeader(
                                        dateMillis = group.dateMillis,
                                        entryCount = group.entries.size,
                                    )
                                }
                                items(
                                    items = group.entries,
                                    key = { it.id },
                                ) { entry ->
                                    CompactJournalEntryRow(
                                        entry = entry,
                                        isSelected = entry.id == selectedEntryId,
                                        searchQuery = searchQuery,
                                        searchScope = searchScope,
                                        onClick = { onSelectEntry(entry) },
                                        onImageClick = { viewerPhoto = it },
                                        onToggleBookmark = { onToggleBookmark(entry) },
                                        onEdit = { onEditEntry(entry) },
                                        onShare = { AttachmentStorageHelper.shareEntry(context, entry) },
                                        onDeleteRequest = { entryPendingDelete = entry },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet && model != null) {
        JournalFilterSheet(
            filter = model.filter,
            allTags = model.allTags,
            matchedCount = model.matchedCount,
            onToggleTag = onToggleTag,
            onTagMatchModeChange = onTagMatchModeChange,
            onDatePresetSelect = onDatePresetSelect,
            onCustomDateRangeSelect = onCustomDateRangeSelect,
            onToggleBookmarkedOnly = onToggleBookmarkedFilter,
            onResetFilters = onClearNonSearchFilters,
            onDismiss = { showFilterSheet = false },
        )
    }

    entryPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntry(target)
                        entryPendingDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    viewerPhoto?.let { photo ->
        FullScreenPhotoViewer(
            attachment = photo,
            onDismiss = { viewerPhoto = null },
        )
    }
}

@Composable
private fun CompactSearchBar(
    query: String,
    isFocused: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearchAction: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isFocused || query.isNotEmpty()) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }
    val containerColor = if (isFocused) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
    }

    Surface(
        shape = RoundedCornerShape(21.dp),
        color = containerColor,
        border = BorderStroke(if (isFocused) 1.25.dp else 1.dp, borderColor),
        modifier = modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = if (isFocused || query.isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChanged(it.isFocused) }
                        .testTag("search_input"),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear)
                        .testTag("clear_search_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun CompactFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val hasActive = activeCount > 0
    Surface(
        shape = RoundedCornerShape(21.dp),
        color = if (hasActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hasActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            },
        ),
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable(onClick = onClick)
            .testTag("open_filter_button"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.cd_open_filters),
                tint = if (hasActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.filter_button),
                style = MaterialTheme.typography.labelMedium,
                color = if (hasActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (hasActive) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = activeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 5.5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFiltersStrip(
    filter: JournalFilterState,
    onClearDate: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onClearBookmarked: () -> Unit,
    onClearAll: () -> Unit,
) {
    val dateLabel = formatActiveDateFilterLabel(filter)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (dateLabel != null) {
            item(key = "active_date") {
                ActiveFilterPill(
                    label = dateLabel,
                    leadingIcon = Icons.Outlined.DateRange,
                    onRemove = onClearDate,
                    testTag = "active_date_pill",
                )
            }
        }
        items(filter.selectedTags.toList(), key = { "active_tag_$it" }) { tag ->
            ActiveFilterPill(
                label = "#$tag",
                onRemove = { onRemoveTag(tag) },
                testTag = "active_tag_pill_${tag.lowercase(Locale.US)}",
            )
        }
        if (filter.bookmarkedOnly) {
            item(key = "active_bookmarked") {
                ActiveFilterPill(
                    label = stringResource(R.string.filter_bookmarked),
                    leadingIcon = Icons.Rounded.Bookmark,
                    onRemove = onClearBookmarked,
                    testTag = "active_bookmarked_pill",
                )
            }
        }
        if (filter.activeFilterCount > 1) {
            item(key = "active_clear_all") {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClearAll)
                        .testTag("active_clear_all_pill"),
                ) {
                    Text(
                        text = stringResource(R.string.filter_reset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterPill(
    label: String,
    onRemove: () -> Unit,
    testTag: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onRemove)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_clear_search),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun CompactStatsStrip(stats: JournalStats) {
    val numberFmt = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "${numberFmt.format(stats.totalEntries)} entr${if (stats.totalEntries == 1) "y" else "ies"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "${numberFmt.format(stats.totalWords)} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stats.currentStreakDays > 0) {
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "${stats.currentStreakDays}d streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickTagRibbon(
    allTags: List<String>,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(allTags, key = { "quick_tag_$it" }) { tag ->
            val selected = selectedTags.any { it.equals(tag, ignoreCase = true) }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
                },
                border = if (selected) {
                    null
                } else {
                    BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onToggleTag(tag) }
                    .testTag("quick_tag_${tag.lowercase(Locale.US)}"),
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DateSectionHeader(
    dateMillis: Long,
    entryCount: Int,
) {
    val label = remember(dateMillis) {
        JournalDateFormatter.formatDateGroupHeader(dateMillis)
    }
    val isToday = remember(dateMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val today = Calendar.getInstance()
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            thickness = 0.75.dp,
        )
        if (entryCount > 1) {
            Text(
                text = "$entryCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun CompactJournalEntryRow(
    entry: JournalEntry,
    isSelected: Boolean,
    searchQuery: String,
    searchScope: SearchScope,
    onClick: () -> Unit,
    onImageClick: (JournalAttachment) -> Unit,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val images = remember(entry.attachments) { entry.imageAttachments() }
    val docs = remember(entry.attachments) { entry.documentAttachments() }
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightFg = MaterialTheme.colorScheme.onPrimaryContainer
    val highlightTitle = searchScope == SearchScope.ALL || searchScope == SearchScope.TITLE
    val showContentSnippet = searchQuery.isNotBlank() &&
        entry.body.isNotBlank() &&
        (searchScope == SearchScope.CONTENT ||
            (searchScope == SearchScope.ALL && !entry.heading.contains(searchQuery.trim(), ignoreCase = true)))

    val borderStroke = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (isSelected) 3.dp else 0.75.dp,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("entry_card_${entry.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Primary Heading (Main visual focus)
                Text(
                    text = if (highlightTitle && searchQuery.isNotBlank()) {
                        buildHighlightedText(entry.heading, searchQuery, highlightBg, highlightFg)
                    } else {
                        AnnotatedString(entry.heading)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Subtle 1-line body match preview ONLY when searching body content
                if (showContentSnippet) {
                    Text(
                        text = buildHighlightedText(entry.body, searchQuery, highlightBg, highlightFg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Quiet Compact Meta Strip (only when tags, location, or attachments exist)
                val hasMeta = entry.tags.isNotEmpty() ||
                    entry.locationName.isNotBlank() ||
                    images.isNotEmpty() ||
                    docs.isNotEmpty()

                if (hasMeta) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (entry.tags.isNotEmpty()) {
                            val tagPreview = buildString {
                                entry.tags.take(2).forEachIndexed { idx, tag ->
                                    if (idx > 0) append(" ")
                                    append("#")
                                    append(tag)
                                }
                                if (entry.tags.size > 2) {
                                    append(" +${entry.tags.size - 2}")
                                }
                            }
                            Text(
                                text = tagPreview,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }

                        if (entry.locationName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = entry.locationName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (images.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Photo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                                if (images.size > 1) {
                                    Text(
                                        text = "${images.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        if (docs.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                                if (docs.size > 1) {
                                    Text(
                                        text = "${docs.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Optional Compact Square Thumbnail
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.width(10.dp))
                AttachmentPhotoTile(
                    attachment = images.first(),
                    onClick = { onImageClick(images.first()) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }

            // Bookmark indicator (shown when bookmarked, tappable to toggle)
            if (entry.bookmarked) {
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("bookmark_button_${entry.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmark,
                        contentDescription = stringResource(R.string.cd_bookmark),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Compact Overflow Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (entry.bookmarked) "Remove Bookmark" else stringResource(R.string.filter_bookmarked)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (entry.bookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleBookmark()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_entry)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyJournalState(
    onStartPrompt: (ReflectionPrompt?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_journal_welcome_hero_1795847653249),
                    contentDescription = stringResource(R.string.cd_welcome_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp),
                )
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.empty_journal_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_journal_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onStartPrompt(null) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("empty_new_entry_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.new_entry))
                    }
                }
            }
        }

        Text(
            text = "SUGGESTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )

        DefaultReflectionPrompts.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onStartPrompt(prompt) },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prompt.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = prompt.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoMatchingEntriesCard(
    onClearFilters: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.no_results_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.no_results_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.clear_filters))
            }
        }
    }
}

@Composable
private fun SearchScopeBar(
    selectedScope: SearchScope,
    matchedCount: Int?,
    isQueryActive: Boolean,
    onSelectScope: (SearchScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(SearchScope.entries, key = { it.name }) { scope ->
                val labelRes = when (scope) {
                    SearchScope.ALL -> R.string.search_scope_all
                    SearchScope.TITLE -> R.string.search_scope_title
                    SearchScope.TAGS -> R.string.search_scope_tags
                    SearchScope.CONTENT -> R.string.search_scope_content
                }
                FilterChip(
                    selected = selectedScope == scope,
                    onClick = { onSelectScope(scope) },
                    label = {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("search_scope_${scope.name.lowercase(Locale.US)}"),
                )
            }
        }

        if (isQueryActive && matchedCount != null) {
            Text(
                text = "$matchedCount match${if (matchedCount == 1) "" else "es"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildHighlightedText(
    source: String,
    query: String,
    highlightBackground: Color,
    highlightForeground: Color,
): AnnotatedString {
    val locale = Locale.getDefault()
    val tokens = query
        .trim()
        .split(Regex("\\s+"))
        .map { it.removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(locale) }

    if (tokens.isEmpty()) return AnnotatedString(source)

    val lowerSource = source.lowercase(locale)
    val style = SpanStyle(
        background = highlightBackground,
        color = highlightForeground,
        fontWeight = FontWeight.SemiBold,
    )

    return buildAnnotatedString {
        append(source)
        tokens.forEach { token ->
            val lowerToken = token.lowercase(locale)
            var searchStart = 0
            while (searchStart < lowerSource.length) {
                val matchIndex = lowerSource.indexOf(lowerToken, startIndex = searchStart)
                if (matchIndex < 0) break
                val endIndex = (matchIndex + lowerToken.length).coerceAtMost(source.length)
                addStyle(style = style, start = matchIndex, end = endIndex)
                searchStart = endIndex
            }
        }
    }
}
