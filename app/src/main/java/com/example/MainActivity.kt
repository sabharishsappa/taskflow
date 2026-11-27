package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.BackupManager
import com.example.data.DaybookDatabase
import com.example.data.HabitRepository
import com.example.data.ImportMode
import com.example.data.JournalRepository
import com.example.data.TaskRepository
import com.example.ui.DateFormatters
import com.example.ui.HabitViewModel
import com.example.ui.JournalUiState
import com.example.ui.JournalViewModel
import com.example.ui.TaskViewModel
import com.example.ui.screens.BackupCenterSheet
import com.example.ui.screens.EntryDetailScreen
import com.example.ui.screens.EntryEditorScreen
import com.example.ui.screens.HabitsScreen
import com.example.ui.screens.JournalHomeScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.DaybookTheme

private const val ROUTE_MAIN = "main"
private const val ROUTE_DETAIL = "detail/{entryId}"
private const val ROUTE_EDITOR_NEW = "editor/new"
private const val ROUTE_EDITOR_EDIT = "editor/edit/{entryId}"

private enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TASKS(R.string.tab_tasks, Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt, "nav_tab_tasks"),
    JOURNAL(R.string.tab_journal, Icons.Rounded.Book, Icons.Outlined.Book, "nav_tab_journal"),
    HABITS(R.string.tab_habits, Icons.Rounded.Loop, Icons.Outlined.Loop, "nav_tab_habits")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaybookTheme {
                DaybookApp()
            }
        }
    }
}

@Composable
fun DaybookApp() {
    val navController = rememberNavController()

    val journalViewModel: JournalViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = DaybookDatabase.getInstance(app)
                JournalViewModel(
                    repository = JournalRepository(db.journalEntryDao()),
                    taskRepository = TaskRepository(db.taskDao()),
                    habitRepository = HabitRepository(db.habitDao())
                )
            }
        }
    )

    val taskViewModel: TaskViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = DaybookDatabase.getInstance(app)
                TaskViewModel(repository = TaskRepository(db.taskDao()))
            }
        }
    )

    val habitViewModel: HabitViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = DaybookDatabase.getInstance(app)
                HabitViewModel(repository = HabitRepository(db.habitDao()))
            }
        }
    )

    NavHost(
        navController = navController,
        startDestination = ROUTE_MAIN
    ) {
        composable(route = ROUTE_MAIN) {
            MainWorkspaceShell(
                journalViewModel = journalViewModel,
                taskViewModel = taskViewModel,
                habitViewModel = habitViewModel,
                onCreateJournalEntry = { navController.navigate(ROUTE_EDITOR_NEW) },
                onOpenJournalEntry = { entryId -> navController.navigate("detail/$entryId") },
                onEditJournalEntry = { entryId -> navController.navigate("editor/edit/$entryId") }
            )
        }

        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId").orEmpty()
            EntryDetailScreen(
                entryId = entryId,
                viewModel = journalViewModel,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("editor/edit/$id") }
            )
        }

        composable(route = ROUTE_EDITOR_NEW) {
            EntryEditorScreen(
                entryId = null,
                viewModel = journalViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_EDITOR_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")
            EntryEditorScreen(
                entryId = entryId,
                viewModel = journalViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainWorkspaceShell(
    journalViewModel: JournalViewModel,
    taskViewModel: TaskViewModel,
    habitViewModel: HabitViewModel,
    onCreateJournalEntry: () -> Unit,
    onOpenJournalEntry: (String) -> Unit,
    onEditJournalEntry: (String) -> Unit
) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.TASKS.name) }
    val selectedTab = remember(selectedTabName) {
        MainTab.entries.firstOrNull { it.name == selectedTabName } ?: MainTab.TASKS
    }

    val context = LocalContext.current
    val journalState by journalViewModel.uiState.collectAsStateWithLifecycle()
    val allTasks by taskViewModel.allTasks.collectAsStateWithLifecycle()
    val allHabits by habitViewModel.allHabitsWithStats.collectAsStateWithLifecycle()
    val isBackupBusy by journalViewModel.isBackupBusy.collectAsStateWithLifecycle()
    val pendingImportPreview by journalViewModel.pendingImportPreview.collectAsStateWithLifecycle()
    val dailyBackupConfig by journalViewModel.dailyBackupConfig.collectAsStateWithLifecycle()
    val savedDailyBackups by journalViewModel.savedDailyBackups.collectAsStateWithLifecycle()
    val shellSnackbarHostState = remember { SnackbarHostState() }
    var showBackupCenterSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        journalViewModel.initDailyBackup(context)
    }

    val canExportBackup = ((journalState as? JournalUiState.Success)?.entries?.isNotEmpty() == true) ||
        allTasks.isNotEmpty() ||
        allHabits.isNotEmpty()

    val createZipBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            journalViewModel.exportBackup(context, uri)
        }
    }

    val createJsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            journalViewModel.exportJsonBackup(context, uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            journalViewModel.inspectImportBackup(context, uri)
        }
    }

    val pickDailyBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            journalViewModel.setDailyBackupFolder(context, treeUri)
        }
    }

    val onExportZipClick: () -> Unit = {
        createZipBackupLauncher.launch(BackupManager.suggestedBackupFileName())
    }
    val onExportJsonClick: () -> Unit = {
        createJsonExportLauncher.launch(BackupManager.suggestedJsonExportFileName())
    }
    val onImportClick: () -> Unit = {
        importBackupLauncher.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/json",
                "application/octet-stream",
                "*/*"
            )
        )
    }
    val onOpenBackupCenter: () -> Unit = {
        showBackupCenterSheet = true
    }

    // Show backup/import feedback snackbar when on Tasks or Habits tab (Journal tab shows its own)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(journalViewModel, lifecycleOwner, selectedTab) {
        if (selectedTab != MainTab.JOURNAL) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                journalViewModel.events.collect { message ->
                    shellSnackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    if (showBackupCenterSheet) {
        BackupCenterSheet(
            config = dailyBackupConfig,
            savedBackups = savedDailyBackups,
            isBackupBusy = isBackupBusy,
            canExportBackup = canExportBackup,
            onDismiss = { showBackupCenterSheet = false },
            onExportZipClick = onExportZipClick,
            onExportJsonClick = onExportJsonClick,
            onImportClick = onImportClick,
            onToggleDailyEnabled = { enabled -> journalViewModel.setDailyBackupEnabled(context, enabled) },
            onSelectScheduleHour = { hour -> journalViewModel.setDailyBackupHour(context, hour) },
            onSelectRetentionDays = { days -> journalViewModel.setDailyBackupRetention(context, days) },
            onPickFolderClick = { pickDailyBackupFolderLauncher.launch(null) },
            onClearFolderClick = { journalViewModel.setDailyBackupFolder(context, null) },
            onRunDailyBackupNow = { journalViewModel.runDailyBackupNow(context) },
            onRestoreSavedBackup = { snapshot ->
                showBackupCenterSheet = false
                journalViewModel.inspectSavedDailyBackup(context, snapshot)
            },
            onDeleteSavedBackup = { snapshot ->
                journalViewModel.deleteSavedDailyBackup(context, snapshot)
            }
        )
    }

    val importPreview = pendingImportPreview
    if (importPreview != null) {
        AlertDialog(
            onDismissRequest = journalViewModel::dismissImportPreview,
            title = {
                Text(
                    text = stringResource(R.string.import_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.import_dialog_suite_summary_format,
                                    importPreview.taskCount,
                                    importPreview.entryCount,
                                    importPreview.habitCount
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (importPreview.attachmentCount > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.import_dialog_attachments_format,
                                        importPreview.attachmentCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            importPreview.exportedAtMillis?.let { exportedAt ->
                                Text(
                                    text = stringResource(
                                        R.string.import_dialog_date_format,
                                        DateFormatters.formatDateTime(exportedAt)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.import_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { journalViewModel.confirmImportBackup(context, ImportMode.REPLACE) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_import_replace_button")
                    ) {
                        Text(stringResource(R.string.action_import_replace))
                    }
                    Button(
                        onClick = { journalViewModel.confirmImportBackup(context, ImportMode.MERGE) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("confirm_import_merge_button")
                    ) {
                        Text(stringResource(R.string.action_import_merge))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = journalViewModel::dismissImportPreview,
                    modifier = Modifier.testTag("cancel_import_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 840.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = shellSnackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp
                    ) {
                        MainTab.entries.forEach { tab ->
                            val isSelected = tab == selectedTab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTabName = tab.name },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        MainTab.entries.forEach { tab ->
                            val isSelected = tab == selectedTab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { selectedTabName = tab.name },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        MainTab.TASKS -> TasksScreen(
                            viewModel = taskViewModel,
                            isBackupBusy = isBackupBusy,
                            canExportBackup = canExportBackup,
                            dailyBackupConfig = dailyBackupConfig,
                            onExportZipClick = onExportZipClick,
                            onExportJsonClick = onExportJsonClick,
                            onImportClick = onImportClick,
                            onOpenBackupCenter = onOpenBackupCenter
                        )

                        MainTab.JOURNAL -> JournalHomeScreen(
                            viewModel = journalViewModel,
                            isBackupBusy = isBackupBusy,
                            canExportBackup = canExportBackup,
                            dailyBackupConfig = dailyBackupConfig,
                            onExportZipClick = onExportZipClick,
                            onExportJsonClick = onExportJsonClick,
                            onImportClick = onImportClick,
                            onOpenBackupCenter = onOpenBackupCenter,
                            onCreateEntry = onCreateJournalEntry,
                            onOpenEntry = onOpenJournalEntry,
                            onEditEntry = onEditJournalEntry
                        )

                        MainTab.HABITS -> HabitsScreen(
                            viewModel = habitViewModel,
                            isBackupBusy = isBackupBusy,
                            canExportBackup = canExportBackup,
                            dailyBackupConfig = dailyBackupConfig,
                            onExportZipClick = onExportZipClick,
                            onExportJsonClick = onExportJsonClick,
                            onImportClick = onImportClick,
                            onOpenBackupCenter = onOpenBackupCenter
                        )
                    }
                }
            }
        }
    }
}
