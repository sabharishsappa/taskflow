package com.example.ui

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AppLockPreferencesRepository
import com.example.data.BackupPreferencesRepository
import com.example.data.BiometricAuthHelper
import com.example.data.BiometricAuthHelper.findFragmentActivity
import com.example.data.BiometricAvailability
import com.example.data.JournalBackupManager
import com.example.data.JournalDatabase
import com.example.data.JournalEntry
import com.example.data.JournalImportManager
import com.example.data.JournalRepository
import com.example.data.ReflectionPrompt
import com.example.viewmodel.ImportUiState
import com.example.viewmodel.JournalViewModel
import com.example.viewmodel.UiState

private sealed interface JournalScreenMode {
    data object Feed : JournalScreenMode
    data class Detail(val entryId: String) : JournalScreenMode
    data class Editor(
        val existingEntryId: String? = null,
        val prompt: ReflectionPrompt? = null,
    ) : JournalScreenMode
    data object Settings : JournalScreenMode
}

@Composable
fun JournalApp() {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val promptTitle = stringResource(R.string.lock_screen_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_screen_prompt_subtitle)

    val viewModel: JournalViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dao = JournalDatabase.getInstance(app).journalEntryDao()
                val backupPrefs = BackupPreferencesRepository(app)
                val lockPrefs = AppLockPreferencesRepository(app)
                val backupManager = JournalBackupManager(app, dao, backupPrefs)
                val importManager = JournalImportManager(app, dao, backupManager)
                JournalViewModel(
                    repository = JournalRepository(dao),
                    backupManager = backupManager,
                    importManager = importManager,
                    backupPreferences = backupPrefs,
                    lockPreferences = lockPrefs,
                )
            }
        },
    )

    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val backupSettings by viewModel.backupSettings.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val lockSettings by viewModel.lockSettings.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isUnlocked by remember { mutableStateOf(false) }
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }
    var autoPromptTrigger by remember { mutableIntStateOf(0) }
    var backgroundedAtMillis by remember { mutableLongStateOf(0L) }
    var biometricAvailability by remember {
        mutableStateOf(BiometricAuthHelper.checkAvailability(context))
    }

    val latestLockSettings by rememberUpdatedState(lockSettings)
    val latestIsUnlocked by rememberUpdatedState(isUnlocked)

    // Observe foreground/background transitions for auto-lock & enrollment refresh
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (latestIsUnlocked && latestLockSettings?.isLockEnabled == true) {
                        backgroundedAtMillis = SystemClock.elapsedRealtime()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    biometricAvailability = BiometricAuthHelper.checkAvailability(context)
                    val currentSettings = latestLockSettings
                    if (currentSettings?.isLockEnabled == true && latestIsUnlocked && backgroundedAtMillis > 0L) {
                        val elapsed = SystemClock.elapsedRealtime() - backgroundedAtMillis
                        if (elapsed >= currentSettings.autoLockTimeout.timeoutMillis) {
                            isUnlocked = false
                            lockErrorMessage = null
                            autoPromptTrigger++
                        }
                    }
                    backgroundedAtMillis = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLocked = lockSettings == null || (lockSettings?.isLockEnabled == true && !isUnlocked)
    val shouldHideRecents = isLocked ||
        (lockSettings?.isLockEnabled == true && lockSettings?.hideInAppSwitcher == true)

    // Hide entry previews in Android Recents switcher (API 33+) without blocking live display/streaming
    DisposableEffect(activity, shouldHideRecents) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.setRecentsScreenshotEnabled(!shouldHideRecents)
        }
        onDispose { }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Hold brief loading surface while DataStore loads lock preference to prevent flashing entries
    val activeLockSettings = lockSettings
    if (activeLockSettings == null) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    // Biometric Lock Gate — journal content is not composed while locked
    if (activeLockSettings.isLockEnabled && !isUnlocked) {
        BiometricLockScreen(
            availability = biometricAvailability,
            errorMessage = lockErrorMessage,
            autoPromptTrigger = autoPromptTrigger,
            onRequestUnlock = {
                val hostActivity = activity
                if (hostActivity == null) {
                    lockErrorMessage = "Unable to start biometric authentication."
                    return@BiometricLockScreen
                }
                lockErrorMessage = null
                BiometricAuthHelper.authenticate(
                    activity = hostActivity,
                    title = promptTitle,
                    subtitle = promptSubtitle,
                    onSuccess = {
                        lockErrorMessage = null
                        isUnlocked = true
                    },
                    onError = { err ->
                        lockErrorMessage = err
                    },
                )
            },
            onOpenSecuritySettings = {
                BiometricAuthHelper.openSecurityEnrollmentSettings(context)
            },
            onContinueWithoutLock = {
                viewModel.setBiometricLockEnabled(
                    enabled = false,
                    message = "Biometric lock turned off. You can enable it anytime from Settings.",
                )
                isUnlocked = true
            },
        )
        return
    }

    var screenMode by remember { mutableStateOf<JournalScreenMode>(JournalScreenMode.Feed) }

    val successData = (feedState as? UiState.Success)?.data
    val allEntries: List<JournalEntry> = successData?.allEntries.orEmpty()
    val allTags = successData?.allTags.orEmpty()
    val recentLocations = successData?.recentLocations.orEmpty()
    val totalAttachments = remember(allEntries) { allEntries.sumOf { it.attachments.size } }

    fun handleToggleBiometricLock(enable: Boolean) {
        val availabilityNow = BiometricAuthHelper.checkAvailability(context)
        biometricAvailability = availabilityNow
        val hostActivity = activity
        if (enable && availabilityNow != BiometricAvailability.AVAILABLE) {
            viewModel.showMessage("Set up a fingerprint, face unlock, or screen PIN in Android Settings first.")
        } else if (hostActivity != null && availabilityNow == BiometricAvailability.AVAILABLE) {
            BiometricAuthHelper.authenticate(
                activity = hostActivity,
                title = if (enable) "Enable Biometric Lock" else "Disable Biometric Lock",
                subtitle = if (enable) {
                    "Verify your identity to protect your journal"
                } else {
                    "Verify your identity to turn off biometric lock"
                },
                onSuccess = {
                    viewModel.setBiometricLockEnabled(
                        enabled = enable,
                        message = if (enable) "Biometric lock enabled" else "Biometric lock turned off",
                    )
                    isUnlocked = true
                },
                onError = { err ->
                    if (!err.isNullOrBlank()) viewModel.showMessage(err)
                },
            )
        } else {
            viewModel.setBiometricLockEnabled(
                enabled = enable,
                message = if (enable) "Biometric lock enabled" else "Biometric lock turned off",
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        if (isExpandedLayout) {
            val selectedId = when (val mode = screenMode) {
                is JournalScreenMode.Detail -> mode.entryId
                is JournalScreenMode.Editor -> mode.existingEntryId
                JournalScreenMode.Feed, JournalScreenMode.Settings -> allEntries.firstOrNull()?.id
            }

            Row(modifier = Modifier.fillMaxSize()) {
                JournalFeedScreen(
                    feedState = feedState,
                    selectedEntryId = selectedId,
                    snackbarHostState = snackbarHostState,
                    onNewEntry = { prompt ->
                        screenMode = JournalScreenMode.Editor(existingEntryId = null, prompt = prompt)
                    },
                    onSelectEntry = { entry ->
                        screenMode = JournalScreenMode.Detail(entry.id)
                    },
                    onEditEntry = { entry ->
                        screenMode = JournalScreenMode.Editor(existingEntryId = entry.id)
                    },
                    onToggleBookmark = { viewModel.toggleBookmark(it) },
                    onDeleteEntry = { entry ->
                        viewModel.deleteEntry(entry.id) {
                            if (selectedId == entry.id) {
                                screenMode = JournalScreenMode.Feed
                            }
                        }
                    },
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSearchScopeChange = viewModel::updateSearchScope,
                    onToggleTag = viewModel::toggleTag,
                    onTagMatchModeChange = viewModel::setTagMatchMode,
                    onDatePresetSelect = viewModel::setDatePreset,
                    onCustomDateRangeSelect = viewModel::setCustomDateRange,
                    onClearDateFilter = viewModel::clearDateFilter,
                    onToggleBookmarkedFilter = viewModel::toggleBookmarkedFilter,
                    onClearNonSearchFilters = viewModel::clearNonSearchFilters,
                    onClearFilters = viewModel::clearFilters,
                    onOpenSettings = {
                        biometricAvailability = BiometricAuthHelper.checkAvailability(context)
                        screenMode = JournalScreenMode.Settings
                    },
                    modifier = Modifier
                        .width(410.dp)
                        .fillMaxHeight(),
                )

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when (val mode = screenMode) {
                        JournalScreenMode.Settings -> {
                            SettingsScreen(
                                lockSettings = activeLockSettings,
                                biometricAvailability = biometricAvailability,
                                backupSettings = backupSettings,
                                localBackups = localBackups,
                                totalEntries = allEntries.size,
                                totalAttachments = totalAttachments,
                                isBackupBusy = isBackupBusy,
                                suggestedExportFileName = viewModel.suggestedExportFileName(),
                                onBack = { screenMode = JournalScreenMode.Feed },
                                onRefreshBackups = viewModel::refreshLocalBackups,
                                onToggleBiometricLock = ::handleToggleBiometricLock,
                                onSelectAutoLockTimeout = viewModel::setAutoLockTimeout,
                                onToggleHideInAppSwitcher = viewModel::setHideInAppSwitcher,
                                onLockNow = {
                                    screenMode = JournalScreenMode.Feed
                                    lockErrorMessage = null
                                    isUnlocked = false
                                    autoPromptTrigger++
                                },
                                onOpenSecuritySettings = {
                                    BiometricAuthHelper.openSecurityEnrollmentSettings(context)
                                },
                                onBackupNow = viewModel::createLocalBackupNow,
                                onFrequencyChange = viewModel::setBackupFrequency,
                                onIncludeAttachmentsChange = viewModel::setIncludeAttachmentsInBackup,
                                onExportToUri = viewModel::exportBackupToUri,
                                onOpenImport = viewModel::openImportSheet,
                                onRestoreLocalSnapshot = viewModel::restoreLocalSnapshot,
                                onShareLocalSnapshot = { viewModel.shareLocalSnapshot(context, it) },
                                onDeleteLocalSnapshot = viewModel::deleteLocalSnapshot,
                                snackbarHostState = null,
                            )
                        }
                        is JournalScreenMode.Editor -> {
                            val existing = mode.existingEntryId?.let { id ->
                                allEntries.firstOrNull { it.id == id }
                            }
                            EntryEditorScreen(
                                existingEntry = existing,
                                initialPrompt = mode.prompt,
                                existingTags = allTags,
                                recentLocations = recentLocations,
                                isSaving = isSaving,
                                onCancel = {
                                    screenMode = if (existing != null) {
                                        JournalScreenMode.Detail(existing.id)
                                    } else {
                                        JournalScreenMode.Feed
                                    }
                                },
                                onSave = { heading, body, dateMillis, locName, lat, lng, tags, atts, bookmarked ->
                                    viewModel.saveEntry(
                                        existingEntryId = existing?.id,
                                        heading = heading,
                                        body = body,
                                        entryDateMillis = dateMillis,
                                        locationName = locName,
                                        locationLat = lat,
                                        locationLng = lng,
                                        tags = tags,
                                        attachments = atts,
                                        bookmarked = bookmarked,
                                    ) { savedId ->
                                        if (savedId != null) {
                                            screenMode = JournalScreenMode.Detail(savedId)
                                        }
                                    }
                                },
                                onInfoMessage = viewModel::showMessage,
                            )
                        }
                        else -> {
                            val detailEntry = selectedId?.let { id ->
                                allEntries.firstOrNull { it.id == id }
                            }
                            if (detailEntry != null) {
                                EntryDetailScreen(
                                    entry = detailEntry,
                                    showBackButton = false,
                                    onBack = { screenMode = JournalScreenMode.Feed },
                                    onEdit = {
                                        screenMode = JournalScreenMode.Editor(existingEntryId = detailEntry.id)
                                    },
                                    onToggleBookmark = { viewModel.toggleBookmark(detailEntry) },
                                    onDelete = {
                                        viewModel.deleteEntry(detailEntry.id) {
                                            screenMode = JournalScreenMode.Feed
                                        }
                                    },
                                    onTagClick = { tag -> viewModel.selectTag(tag) },
                                    onAttachmentOpenError = viewModel::showMessage,
                                )
                            } else {
                                ExpandedEmptyPane()
                            }
                        }
                    }
                }
            }
        } else {
            when (val mode = screenMode) {
                JournalScreenMode.Feed -> {
                    JournalFeedScreen(
                        feedState = feedState,
                        selectedEntryId = null,
                        snackbarHostState = snackbarHostState,
                        onNewEntry = { prompt ->
                            screenMode = JournalScreenMode.Editor(existingEntryId = null, prompt = prompt)
                        },
                        onSelectEntry = { entry ->
                            screenMode = JournalScreenMode.Detail(entry.id)
                        },
                        onEditEntry = { entry ->
                            screenMode = JournalScreenMode.Editor(existingEntryId = entry.id)
                        },
                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                        onDeleteEntry = { entry -> viewModel.deleteEntry(entry.id) },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSearchScopeChange = viewModel::updateSearchScope,
                        onToggleTag = viewModel::toggleTag,
                        onTagMatchModeChange = viewModel::setTagMatchMode,
                        onDatePresetSelect = viewModel::setDatePreset,
                        onCustomDateRangeSelect = viewModel::setCustomDateRange,
                        onClearDateFilter = viewModel::clearDateFilter,
                        onToggleBookmarkedFilter = viewModel::toggleBookmarkedFilter,
                        onClearNonSearchFilters = viewModel::clearNonSearchFilters,
                        onClearFilters = viewModel::clearFilters,
                        onOpenSettings = {
                            biometricAvailability = BiometricAuthHelper.checkAvailability(context)
                            screenMode = JournalScreenMode.Settings
                        },
                    )
                }
                JournalScreenMode.Settings -> {
                    SettingsScreen(
                        lockSettings = activeLockSettings,
                        biometricAvailability = biometricAvailability,
                        backupSettings = backupSettings,
                        localBackups = localBackups,
                        totalEntries = allEntries.size,
                        totalAttachments = totalAttachments,
                        isBackupBusy = isBackupBusy,
                        suggestedExportFileName = viewModel.suggestedExportFileName(),
                        onBack = { screenMode = JournalScreenMode.Feed },
                        onRefreshBackups = viewModel::refreshLocalBackups,
                        onToggleBiometricLock = ::handleToggleBiometricLock,
                        onSelectAutoLockTimeout = viewModel::setAutoLockTimeout,
                        onToggleHideInAppSwitcher = viewModel::setHideInAppSwitcher,
                        onLockNow = {
                            screenMode = JournalScreenMode.Feed
                            lockErrorMessage = null
                            isUnlocked = false
                            autoPromptTrigger++
                        },
                        onOpenSecuritySettings = {
                            BiometricAuthHelper.openSecurityEnrollmentSettings(context)
                        },
                        onBackupNow = viewModel::createLocalBackupNow,
                        onFrequencyChange = viewModel::setBackupFrequency,
                        onIncludeAttachmentsChange = viewModel::setIncludeAttachmentsInBackup,
                        onExportToUri = viewModel::exportBackupToUri,
                        onOpenImport = viewModel::openImportSheet,
                        onRestoreLocalSnapshot = viewModel::restoreLocalSnapshot,
                        onShareLocalSnapshot = { viewModel.shareLocalSnapshot(context, it) },
                        onDeleteLocalSnapshot = viewModel::deleteLocalSnapshot,
                        snackbarHostState = snackbarHostState,
                    )
                }
                is JournalScreenMode.Detail -> {
                    val entry = allEntries.firstOrNull { it.id == mode.entryId }
                    if (entry != null) {
                        EntryDetailScreen(
                            entry = entry,
                            showBackButton = true,
                            onBack = { screenMode = JournalScreenMode.Feed },
                            onEdit = { screenMode = JournalScreenMode.Editor(existingEntryId = entry.id) },
                            onToggleBookmark = { viewModel.toggleBookmark(entry) },
                            onDelete = {
                                viewModel.deleteEntry(entry.id) {
                                    screenMode = JournalScreenMode.Feed
                                }
                            },
                            onTagClick = { tag ->
                                viewModel.selectTag(tag)
                                screenMode = JournalScreenMode.Feed
                            },
                            onAttachmentOpenError = viewModel::showMessage,
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            screenMode = JournalScreenMode.Feed
                        }
                    }
                }
                is JournalScreenMode.Editor -> {
                    val existing = mode.existingEntryId?.let { id ->
                        allEntries.firstOrNull { it.id == id }
                    }
                    EntryEditorScreen(
                        existingEntry = existing,
                        initialPrompt = mode.prompt,
                        existingTags = allTags,
                        recentLocations = recentLocations,
                        isSaving = isSaving,
                        onCancel = {
                            screenMode = if (existing != null) {
                                JournalScreenMode.Detail(existing.id)
                            } else {
                                JournalScreenMode.Feed
                            }
                        },
                        onSave = { heading, body, dateMillis, locName, lat, lng, tags, atts, bookmarked ->
                            viewModel.saveEntry(
                                existingEntryId = existing?.id,
                                heading = heading,
                                body = body,
                                entryDateMillis = dateMillis,
                                locationName = locName,
                                locationLat = lat,
                                locationLng = lng,
                                tags = tags,
                                attachments = atts,
                                bookmarked = bookmarked,
                            ) { savedId ->
                                if (savedId != null) {
                                    screenMode = JournalScreenMode.Feed
                                }
                            }
                        },
                        onInfoMessage = viewModel::showMessage,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }
    }

    if (importState !is ImportUiState.Hidden) {
        ImportJournalSheet(
            importState = importState,
            onPickFileUri = viewModel::inspectImportUri,
            onConfirmImport = viewModel::confirmImport,
            onDismiss = viewModel::dismissImportSheet,
        )
    }
}

@Composable
private fun ExpandedEmptyPane() {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Select an entry or start a new moment",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
