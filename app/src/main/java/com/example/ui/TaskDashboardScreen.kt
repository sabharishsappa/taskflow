package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppSettings
import com.example.data.TaskBackupSerializer
import com.example.data.TaskDateGroup
import com.example.data.TaskDateUtils
import com.example.data.TaskItem
import com.example.data.TaskRowSize
import com.example.data.TaskSectionTab
import com.example.ui.components.DateGroupHeader
import com.example.ui.components.TaskCard
import com.example.ui.components.TaskEditorContent
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrismAmber
import com.example.ui.theme.PrismCoral
import com.example.ui.theme.PrismSky
import com.example.ui.theme.PrismTeal
import com.example.ui.theme.PrismViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDashboardScreen(
    viewModel: TaskViewModel
) {
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationDeniedMsg = stringResource(R.string.notification_permission_denied)
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    var pendingImportReplace by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(context.contentResolver, uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(
                contentResolver = context.contentResolver,
                uri = uri,
                replaceExisting = pendingImportReplace
            )
        }
    }

    LaunchedEffect(controls.snackbarMessage) {
        val msg = controls.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val completedCount = remember(allTasks) { allTasks.count { it.completed } }

    if (controls.isSettingsOpen) {
        SettingsScreen(
            settings = settings,
            totalTaskCount = allTasks.size,
            completedTaskCount = completedCount,
            isBackupBusy = controls.isBackupBusy,
            snackbarHostState = snackbarHostState,
            onSelectTheme = viewModel::setThemeMode,
            onSelectRowSize = viewModel::setTaskRowSize,
            onSelectTextScale = viewModel::setTextScale,
            onToggle24HourTime = viewModel::setUse24HourTime,
            onToggleShowCompleted = viewModel::setShowCompletedTasks,
            onExportBackup = {
                exportBackupLauncher.launch(TaskBackupSerializer.suggestedBackupFileName())
            },
            onImportBackup = { replaceExisting ->
                pendingImportReplace = replaceExisting
                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onClearCompleted = viewModel::clearCompletedTasks,
            onBack = viewModel::closeSettings
        )
        return
    }

    val visibleTasks by remember(allTasks, controls.searchQuery, settings.showCompletedTasks) {
        derivedStateOf {
            val query = controls.searchQuery.trim().lowercase()
            allTasks.filter { task ->
                val matchesCompletedPref = settings.showCompletedTasks || !task.completed
                val matchesQuery = query.isEmpty() ||
                    task.title.lowercase().contains(query) ||
                    task.notes.lowercase().contains(query) ||
                    task.subtasks.any { it.title.lowercase().contains(query) }
                matchesCompletedPref && matchesQuery
            }
        }
    }

    val timelineGroups by remember(visibleTasks) {
        derivedStateOf {
            val scheduled = visibleTasks.filter { TaskDateUtils.hasTimeline(it) }
            TaskDateUtils.groupTasksByDate(scheduled)
        }
    }

    val otherTasks by remember(visibleTasks) {
        derivedStateOf {
            visibleTasks
                .filterNot { TaskDateUtils.hasTimeline(it) }
                .sortedWith(
                    compareBy<TaskItem> { it.completed }
                        .thenBy { it.createdAtMillis }
                )
        }
    }

    val activeTimelineCount by remember(allTasks) {
        derivedStateOf { allTasks.count { !it.completed && TaskDateUtils.hasTimeline(it) } }
    }
    val activeOtherCount by remember(allTasks) {
        derivedStateOf { allTasks.count { !it.completed && !TaskDateUtils.hasTimeline(it) } }
    }

    val todayDateSubtitle = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    val ambientGlowAlpha = if (isDark) 0.14f else 0.08f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrismViolet.copy(alpha = ambientGlowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.04f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.92f, size.height * 0.04f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrismTeal.copy(alpha = ambientGlowAlpha * 0.85f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.06f, size.height * 0.14f),
                        radius = size.width * 0.58f
                    ),
                    radius = size.width * 0.58f,
                    center = Offset(size.width * 0.06f, size.height * 0.14f)
                )
            }
    ) {
        val isExpandedLayout = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                PrismTopAppBar(
                    todaySubtitle = todayDateSubtitle,
                    isSearchActive = controls.isSearchActive,
                    onToggleSearch = { viewModel.setSearchActive(!controls.isSearchActive) },
                    onOpenSettings = viewModel::openSettings
                )
            },
            bottomBar = {
                if (!isExpandedLayout) {
                    SectionBottomBar(
                        selectedSection = controls.selectedSection,
                        timelineBadgeCount = activeTimelineCount,
                        otherBadgeCount = activeOtherCount,
                        onSelectSection = viewModel::selectSection
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openNewTaskEditor() },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = {
                        Text(
                            text = stringResource(R.string.new_task),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("add_task_fab")
                )
            }
        ) { innerPadding ->
            if (isExpandedLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    SectionNavigationRail(
                        selectedSection = controls.selectedSection,
                        timelineBadgeCount = activeTimelineCount,
                        otherBadgeCount = activeOtherCount,
                        onSelectSection = viewModel::selectSection
                    )

                    TaskListPane(
                        tasksState = tasksState,
                        selectedSection = controls.selectedSection,
                        timelineGroups = timelineGroups,
                        otherTasks = otherTasks,
                        controls = controls,
                        settings = settings,
                        onUpdateSearch = viewModel::updateSearchQuery,
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onCardClick = viewModel::openEditTaskEditor,
                        onToggleExpand = viewModel::toggleExpandTask,
                        onToggleSubtask = viewModel::toggleSubtask,
                        modifier = Modifier.weight(1.1f)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(topStart = 28.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .padding(top = 8.dp)
                    ) {
                        val draft = controls.editorDraft
                        if (draft != null) {
                            TaskEditorContent(
                                draft = draft,
                                onUpdateDraft = viewModel::updateDraft,
                                onAddSubtask = viewModel::addSubtaskToDraft,
                                onToggleSubtask = viewModel::toggleDraftSubtask,
                                onRemoveSubtask = viewModel::removeDraftSubtask,
                                onSave = viewModel::saveEditorDraft,
                                onDelete = draft.editingTaskId?.let { id -> { viewModel.deleteTask(id) } },
                                onClose = viewModel::closeEditor,
                                onNotificationPermissionDenied = {
                                    viewModel.showSnackbar(notificationDeniedMsg)
                                }
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.select_task_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    TaskListPane(
                        tasksState = tasksState,
                        selectedSection = controls.selectedSection,
                        timelineGroups = timelineGroups,
                        otherTasks = otherTasks,
                        controls = controls,
                        settings = settings,
                        onUpdateSearch = viewModel::updateSearchQuery,
                        onToggleComplete = viewModel::toggleTaskCompletion,
                        onCardClick = viewModel::openEditTaskEditor,
                        onToggleExpand = viewModel::toggleExpandTask,
                        onToggleSubtask = viewModel::toggleSubtask,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                    )
                }

                val activeDraft = controls.editorDraft
                if (activeDraft != null) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = viewModel::closeEditor,
                        sheetState = sheetState,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TaskEditorContent(
                            draft = activeDraft,
                            onUpdateDraft = viewModel::updateDraft,
                            onAddSubtask = viewModel::addSubtaskToDraft,
                            onToggleSubtask = viewModel::toggleDraftSubtask,
                            onRemoveSubtask = viewModel::removeDraftSubtask,
                            onSave = viewModel::saveEditorDraft,
                            onDelete = activeDraft.editingTaskId?.let { id -> { viewModel.deleteTask(id) } },
                            onClose = viewModel::closeEditor,
                            onNotificationPermissionDenied = {
                                viewModel.showSnackbar(notificationDeniedMsg)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBottomBar(
    selectedSection: TaskSectionTab,
    timelineBadgeCount: Int,
    otherBadgeCount: Int,
    onSelectSection: (TaskSectionTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        NavigationBarItem(
            selected = selectedSection == TaskSectionTab.TIMELINE,
            onClick = { onSelectSection(TaskSectionTab.TIMELINE) },
            icon = {
                BadgedBox(
                    badge = {
                        if (timelineBadgeCount > 0) {
                            Badge(
                                containerColor = PrismTeal,
                                contentColor = Color.White
                            ) {
                                Text(if (timelineBadgeCount > 99) "99+" else timelineBadgeCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedSection == TaskSectionTab.TIMELINE) {
                            Icons.Rounded.CalendarMonth
                        } else {
                            Icons.Outlined.CalendarMonth
                        },
                        contentDescription = stringResource(R.string.section_tab_timeline)
                    )
                }
            },
            label = {
                Text(
                    text = stringResource(R.string.section_tab_timeline),
                    fontWeight = if (selectedSection == TaskSectionTab.TIMELINE) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    }
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("section_tab_timeline")
        )

        NavigationBarItem(
            selected = selectedSection == TaskSectionTab.OTHER_TASKS,
            onClick = { onSelectSection(TaskSectionTab.OTHER_TASKS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (otherBadgeCount > 0) {
                            Badge(
                                containerColor = PrismAmber,
                                contentColor = Color.White
                            ) {
                                Text(if (otherBadgeCount > 99) "99+" else otherBadgeCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedSection == TaskSectionTab.OTHER_TASKS) {
                            Icons.Rounded.Inbox
                        } else {
                            Icons.Outlined.Inbox
                        },
                        contentDescription = stringResource(R.string.section_tab_other)
                    )
                }
            },
            label = {
                Text(
                    text = stringResource(R.string.section_tab_other),
                    fontWeight = if (selectedSection == TaskSectionTab.OTHER_TASKS) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    }
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("section_tab_other")
        )
    }
}

@Composable
private fun SectionNavigationRail(
    selectedSection: TaskSectionTab,
    timelineBadgeCount: Int,
    otherBadgeCount: Int,
    onSelectSection: (TaskSectionTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = selectedSection == TaskSectionTab.TIMELINE,
            onClick = { onSelectSection(TaskSectionTab.TIMELINE) },
            icon = {
                BadgedBox(
                    badge = {
                        if (timelineBadgeCount > 0) {
                            Badge(containerColor = PrismTeal, contentColor = Color.White) {
                                Text(if (timelineBadgeCount > 99) "99+" else timelineBadgeCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedSection == TaskSectionTab.TIMELINE) {
                            Icons.Rounded.CalendarMonth
                        } else {
                            Icons.Outlined.CalendarMonth
                        },
                        contentDescription = stringResource(R.string.section_tab_timeline)
                    )
                }
            },
            label = { Text(stringResource(R.string.section_tab_timeline)) },
            modifier = Modifier.testTag("section_rail_timeline")
        )
        NavigationRailItem(
            selected = selectedSection == TaskSectionTab.OTHER_TASKS,
            onClick = { onSelectSection(TaskSectionTab.OTHER_TASKS) },
            icon = {
                BadgedBox(
                    badge = {
                        if (otherBadgeCount > 0) {
                            Badge(containerColor = PrismAmber, contentColor = Color.White) {
                                Text(if (otherBadgeCount > 99) "99+" else otherBadgeCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (selectedSection == TaskSectionTab.OTHER_TASKS) {
                            Icons.Rounded.Inbox
                        } else {
                            Icons.Outlined.Inbox
                        },
                        contentDescription = stringResource(R.string.section_tab_other)
                    )
                }
            },
            label = { Text(stringResource(R.string.section_tab_other)) },
            modifier = Modifier.testTag("section_rail_other")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrismTopAppBar(
    todaySubtitle: String,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(PrismCoral, PrismAmber, PrismTeal, PrismSky)
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = todaySubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSearchActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                    }
                ) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.testTag("toggle_search_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search_button_desc),
                            tint = if (isSearchActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_button_desc),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun TaskListPane(
    tasksState: UiState<List<TaskItem>>,
    selectedSection: TaskSectionTab,
    timelineGroups: List<TaskDateGroup>,
    otherTasks: List<TaskItem>,
    controls: DashboardControlsState,
    settings: AppSettings,
    onUpdateSearch: (String) -> Unit,
    onToggleComplete: (TaskItem) -> Unit,
    onCardClick: (TaskItem) -> Unit,
    onToggleExpand: (String) -> Unit,
    onToggleSubtask: (TaskItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemSpacing = when (settings.taskRowSize) {
        TaskRowSize.COMPACT -> 5.dp
        TaskRowSize.NORMAL -> 7.dp
        TaskRowSize.LARGE -> 10.dp
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        modifier = modifier
    ) {
        item(key = "search_bar", contentType = "search") {
            AnimatedVisibility(visible = controls.isSearchActive) {
                OutlinedTextField(
                    value = controls.searchQuery,
                    onValueChange = onUpdateSearch,
                    placeholder = { Text(stringResource(R.string.search_tasks_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (controls.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateSearch("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear_search_desc)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("search_tasks_input")
                )
            }
        }

        when (tasksState) {
            is UiState.Loading -> {
                item(key = "loading", contentType = "loading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item(key = "error", contentType = "error") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = tasksState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            is UiState.Success -> {
                when (selectedSection) {
                    TaskSectionTab.TIMELINE -> {
                        if (timelineGroups.isEmpty()) {
                            item(key = "empty_timeline", contentType = "empty") {
                                EmptyTasksState(
                                    isSearching = controls.searchQuery.isNotBlank(),
                                    section = TaskSectionTab.TIMELINE
                                )
                            }
                        } else {
                            timelineGroups.forEachIndexed { groupIndex, group ->
                                item(
                                    key = "header_${group.key}",
                                    contentType = "date_header"
                                ) {
                                    DateGroupHeader(
                                        group = group,
                                        isFirst = groupIndex == 0,
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                items(
                                    items = group.tasks,
                                    key = { it.id },
                                    contentType = { "task_card" }
                                ) { task ->
                                    TaskCard(
                                        task = task,
                                        isExpanded = task.id in controls.expandedTaskIds,
                                        isSelected = controls.editorDraft?.editingTaskId == task.id,
                                        rowSize = settings.taskRowSize,
                                        use24HourTime = settings.use24HourTime,
                                        onToggleComplete = { onToggleComplete(task) },
                                        onCardClick = { onCardClick(task) },
                                        onToggleExpand = { onToggleExpand(task.id) },
                                        onToggleSubtask = { subId -> onToggleSubtask(task, subId) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }

                    TaskSectionTab.OTHER_TASKS -> {
                        if (otherTasks.isEmpty()) {
                            item(key = "empty_other", contentType = "empty") {
                                EmptyTasksState(
                                    isSearching = controls.searchQuery.isNotBlank(),
                                    section = TaskSectionTab.OTHER_TASKS
                                )
                            }
                        } else {
                            items(
                                items = otherTasks,
                                key = { it.id },
                                contentType = { "task_card" }
                            ) { task ->
                                TaskCard(
                                    task = task,
                                    isExpanded = task.id in controls.expandedTaskIds,
                                    isSelected = controls.editorDraft?.editingTaskId == task.id,
                                    rowSize = settings.taskRowSize,
                                    use24HourTime = settings.use24HourTime,
                                    onToggleComplete = { onToggleComplete(task) },
                                    onCardClick = { onCardClick(task) },
                                    onToggleExpand = { onToggleExpand(task.id) },
                                    onToggleSubtask = { subId -> onToggleSubtask(task, subId) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksState(
    isSearching: Boolean,
    section: TaskSectionTab
) {
    val titleRes = when {
        isSearching -> R.string.empty_filter_title
        section == TaskSectionTab.TIMELINE -> R.string.empty_timeline_title
        else -> R.string.empty_other_title
    }
    val subtitleRes = when {
        isSearching -> R.string.empty_filter_subtitle
        section == TaskSectionTab.TIMELINE -> R.string.empty_timeline_subtitle
        else -> R.string.empty_other_subtitle
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(4f / 3f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_tasks_1795857564149),
                    contentDescription = stringResource(R.string.empty_illustration_desc),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
