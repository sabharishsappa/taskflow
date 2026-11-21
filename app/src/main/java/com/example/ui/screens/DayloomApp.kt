package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.TaskRepository
import com.example.data.local.DayloomDatabase
import com.example.notifications.ReminderScheduler
import com.example.ui.DataState
import com.example.ui.MainDestination
import com.example.ui.TaskViewModel
import com.example.ui.components.ListEditorSheet
import com.example.ui.components.TaskEditorSheet

private data class NavTabItem(
    val destination: MainDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@Composable
fun DayloomApp() {
    val viewModel: TaskViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val database = DayloomDatabase.getInstance(app)
                TaskViewModel(
                    repository = TaskRepository(database.dayloomDao()),
                    reminderScheduler = ReminderScheduler(app),
                )
            }
        }
    )

    val controls by viewModel.controls.collectAsStateWithLifecycle()
    val listsState by viewModel.listsState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(controls.snackbarMessage) {
        val msg = controls.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val navItems = remember {
        listOf(
            NavTabItem(
                destination = MainDestination.TIMELINE,
                label = "Timeline",
                selectedIcon = Icons.Rounded.ViewAgenda,
                unselectedIcon = Icons.Outlined.ViewAgenda,
                testTag = "nav_timeline",
            ),
            NavTabItem(
                destination = MainDestination.CALENDAR,
                label = "Calendar",
                selectedIcon = Icons.Rounded.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                testTag = "nav_calendar",
            ),
            NavTabItem(
                destination = MainDestination.LISTS,
                label = "Lists",
                selectedIcon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                unselectedIcon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                testTag = "nav_lists",
            ),
        )
    }

    val loadedLists = (listsState as? DataState.Success)?.data.orEmpty()
    val loadedTasks = (tasksState as? DataState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    navItems.forEach { item ->
                        val selected = controls.currentTab == item.destination
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(item.destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 2.dp,
                        ) {
                            navItems.forEach { item ->
                                val selected = controls.currentTab == item.destination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.destination) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag),
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    val isListsRoot = controls.currentTab == MainDestination.LISTS &&
                        controls.selectedListDetailId == null
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (isListsRoot) {
                                viewModel.openNewListEditor()
                            } else {
                                viewModel.openNewTaskEditor()
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                            )
                        },
                        text = {
                            Text(if (isListsRoot) "New List" else "New Task")
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.testTag("primary_fab"),
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val errorMsg = (listsState as? DataState.Error)?.message
                        ?: (tasksState as? DataState.Error)?.message
                    val isLoading = (listsState is DataState.Loading || tasksState is DataState.Loading) &&
                        errorMsg == null

                    when {
                        errorMsg != null -> {
                            StorageErrorState(message = errorMsg)
                        }

                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Loading your tasks…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        else -> {
                            when (controls.currentTab) {
                                MainDestination.TIMELINE -> TimelineScreen(
                                    tasks = loadedTasks,
                                    lists = loadedLists,
                                    selectedListFilterId = controls.timelineListFilterId,
                                    showCompleted = controls.showCompletedInTimeline,
                                    onSelectListFilter = viewModel::setTimelineListFilter,
                                    onToggleShowCompleted = viewModel::toggleShowCompletedInTimeline,
                                    onToggleTaskComplete = viewModel::toggleTaskCompleted,
                                    onEditTask = viewModel::openEditTaskEditor,
                                    onQuickAddForDate = { date -> viewModel.openNewTaskEditor(prefillDate = date) },
                                )

                                MainDestination.CALENDAR -> CalendarScreen(
                                    tasks = loadedTasks,
                                    lists = loadedLists,
                                    selectedDate = controls.calendarSelectedDate,
                                    displayedMonth = controls.calendarDisplayedMonth,
                                    selectedListFilterId = controls.calendarListFilterId,
                                    onSelectDate = viewModel::selectCalendarDate,
                                    onShiftMonth = viewModel::shiftCalendarMonth,
                                    onJumpToToday = viewModel::jumpCalendarToToday,
                                    onSelectListFilter = viewModel::setCalendarListFilter,
                                    onToggleTaskComplete = viewModel::toggleTaskCompleted,
                                    onEditTask = viewModel::openEditTaskEditor,
                                    onAddTaskForDate = { date -> viewModel.openNewTaskEditor(prefillDate = date) },
                                )

                                MainDestination.LISTS -> ListsScreen(
                                    lists = loadedLists,
                                    tasks = loadedTasks,
                                    selectedListDetailId = controls.selectedListDetailId,
                                    onOpenListDetail = viewModel::openListDetail,
                                    onCreateList = viewModel::openNewListEditor,
                                    onEditList = viewModel::openEditListEditor,
                                    onAddTaskInList = { listId -> viewModel.openNewTaskEditor(prefillListId = listId) },
                                    onToggleTaskComplete = viewModel::toggleTaskCompleted,
                                    onEditTask = viewModel::openEditTaskEditor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Task Editor Modal Sheet
    val taskDraft = controls.taskEditorDraft
    if (taskDraft != null) {
        val deniedMsg = stringResource(id = R.string.notification_permission_denied)
        TaskEditorSheet(
            draft = taskDraft,
            lists = loadedLists,
            isSaving = controls.isMutating,
            onDismiss = viewModel::dismissTaskEditor,
            onRequestCreateList = viewModel::openNewListEditor,
            onNotificationDenied = { viewModel.showMessage(deniedMsg) },
            onDelete = viewModel::deleteTask,
            onSave = viewModel::saveTask,
        )
    }

    // List Editor Modal Sheet
    val listDraft = controls.listEditorDraft
    if (listDraft != null) {
        ListEditorSheet(
            draft = listDraft,
            isSaving = controls.isMutating,
            onDismiss = viewModel::dismissListEditor,
            onDelete = viewModel::deleteList,
            onSave = { existing, name, colorHex, iconKey ->
                viewModel.saveList(existing, name, colorHex, iconKey)
            },
        )
    }
}

@Composable
private fun StorageErrorState(
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Unable to Load Tasks",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
