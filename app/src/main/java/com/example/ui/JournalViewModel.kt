package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttachmentAndLocationHelper
import com.example.data.BackupManager
import com.example.data.BackupPreview
import com.example.data.DailyBackupConfig
import com.example.data.DailyBackupManager
import com.example.data.EventLocation
import com.example.data.HabitRepository
import com.example.data.ImportMode
import com.example.data.JournalAttachment
import com.example.data.JournalEntry
import com.example.data.JournalRepository
import com.example.data.SavedDailyBackup
import com.example.data.TaskRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface JournalUiState {
    data object Loading : JournalUiState
    data class Success(val entries: List<JournalEntry>) : JournalUiState
    data class Error(val message: String) : JournalUiState
}

data class JournalFilterState(
    val query: String = "",
    val selectedDateMillis: Long? = null,
    val selectedTag: String? = null
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || selectedDateMillis != null || selectedTag != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val repository: JournalRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    private val _filterState = MutableStateFlow(JournalFilterState())
    val filterState: StateFlow<JournalFilterState> = _filterState.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _pendingImportPreview = MutableStateFlow<BackupPreview?>(null)
    val pendingImportPreview: StateFlow<BackupPreview?> = _pendingImportPreview.asStateFlow()

    val dailyBackupConfig: StateFlow<DailyBackupConfig> = DailyBackupManager.configFlow
    val savedDailyBackups: StateFlow<List<SavedDailyBackup>> = DailyBackupManager.localBackupsFlow

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<JournalUiState> = retryTrigger
        .flatMapLatest {
            repository.observeEntries()
                .map<List<JournalEntry>, JournalUiState> { entries ->
                    val sorted = entries.sortedWith(
                        compareByDescending<JournalEntry> { it.eventDateMillis }
                            .thenByDescending { it.createdAtMillis }
                    )
                    JournalUiState.Success(sorted)
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.w(TAG, "Error observing events", error)
                    emit(
                        JournalUiState.Error(
                            error.localizedMessage ?: "Unable to load your events right now."
                        )
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = JournalUiState.Loading
        )

    val availableTags: StateFlow<List<String>> = uiState.map { state ->
        val entries = (state as? JournalUiState.Success)?.entries.orEmpty()
        entries.flatMap { it.tags }.distinct().sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val filteredEntries: StateFlow<List<JournalEntry>> = combine(uiState, filterState) { state, filter ->
        val entries = (state as? JournalUiState.Success)?.entries.orEmpty()
        val targetDayKey = filter.selectedDateMillis?.let { DateFormatters.dayKey(it) }
        entries.filter { entry ->
            val matchesDate = targetDayKey == null ||
                DateFormatters.dayKey(entry.eventDateMillis) == targetDayKey
            val matchesTag = filter.selectedTag == null || entry.tags.contains(filter.selectedTag)
            val q = filter.query.trim()
            val matchesQuery = q.isEmpty() ||
                entry.heading.contains(q, ignoreCase = true) ||
                entry.content.contains(q, ignoreCase = true) ||
                entry.tags.any { it.contains(q, ignoreCase = true) } ||
                (entry.location?.name?.contains(q, ignoreCase = true) == true)
            matchesDate && matchesTag && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    fun retryObserve() {
        retryTrigger.update { it + 1 }
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun selectDateFilter(dateMillis: Long?) {
        _filterState.update { it.copy(selectedDateMillis = dateMillis) }
    }

    fun selectTagFilter(tag: String?) {
        _filterState.update { state ->
            state.copy(selectedTag = if (state.selectedTag == tag) null else tag)
        }
    }

    fun clearFilters() {
        _filterState.value = JournalFilterState()
    }

    fun findEntryById(entryId: String): JournalEntry? {
        val entries = (uiState.value as? JournalUiState.Success)?.entries.orEmpty()
        return entries.firstOrNull { it.id == entryId }
    }

    fun saveEntry(
        entryId: String?,
        heading: String,
        content: String,
        eventDateMillis: Long,
        favorite: Boolean,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        location: EventLocation?,
        onSuccess: (savedId: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (entryId == null) {
                    val result = repository.createEntry(
                        heading = heading,
                        content = content,
                        eventDateMillis = eventDateMillis,
                        favorite = favorite,
                        tags = tags,
                        attachments = attachments,
                        location = location
                    )
                    result.fold(
                        onSuccess = { newId ->
                            _events.tryEmit("Event saved")
                            onSuccess(newId)
                        },
                        onFailure = { error ->
                            onError(error.localizedMessage ?: "Couldn't save event. Please try again.")
                        }
                    )
                } else {
                    val result = repository.updateEntry(
                        entryId = entryId,
                        heading = heading,
                        content = content,
                        eventDateMillis = eventDateMillis,
                        favorite = favorite,
                        tags = tags,
                        attachments = attachments,
                        location = location
                    )
                    result.fold(
                        onSuccess = {
                            _events.tryEmit("Changes saved")
                            onSuccess(entryId)
                        },
                        onFailure = { error ->
                            onError(error.localizedMessage ?: "Couldn't update event. Please try again.")
                        }
                    )
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleFavorite(entry: JournalEntry) {
        viewModelScope.launch {
            val result = repository.toggleFavorite(
                entryId = entry.id,
                favorite = !entry.favorite
            )
            result.onFailure {
                _events.tryEmit("Couldn't update bookmark")
            }
        }
    }

    fun deleteEntry(entryId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteEntry(entryId)
            result.fold(
                onSuccess = {
                    _events.tryEmit("Event deleted")
                    onDeleted()
                },
                onFailure = {
                    _events.tryEmit("Couldn't delete event. Please try again.")
                }
            )
        }
    }

    fun exportBackup(context: Context, uri: Uri) {
        if (_isBackupBusy.value) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val journalSnapshot = repository.getAllEntriesSnapshot()
                val tasksSnapshot = taskRepository.getAllTasksSnapshot()
                val (habitsSnapshot, completionsSnapshot) = habitRepository.getAllHabitsSnapshot()

                if (journalSnapshot.isEmpty() && tasksSnapshot.isEmpty() && habitsSnapshot.isEmpty()) {
                    _events.tryEmit("Nothing to back up yet.")
                    return@launch
                }

                val result = BackupManager.exportBackupToUri(
                    context = appContext,
                    uri = uri,
                    entries = journalSnapshot,
                    tasks = tasksSnapshot,
                    habits = habitsSnapshot,
                    habitCompletions = completionsSnapshot
                )
                result.fold(
                    onSuccess = { info ->
                        val parts = buildList {
                            if (info.entryCount > 0) add("${info.entryCount} journal")
                            if (info.taskCount > 0) add("${info.taskCount} tasks")
                            if (info.habitCount > 0) add("${info.habitCount} habits")
                        }
                        val summary = if (parts.isEmpty()) "0 items" else parts.joinToString(", ")
                        _events.tryEmit("Backup exported ($summary)")
                    },
                    onFailure = { error ->
                        _events.tryEmit(error.localizedMessage ?: "Couldn't export backup. Please try again.")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun exportJsonBackup(context: Context, uri: Uri) {
        if (_isBackupBusy.value) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val journalSnapshot = repository.getAllEntriesSnapshot()
                val tasksSnapshot = taskRepository.getAllTasksSnapshot()
                val (habitsSnapshot, completionsSnapshot) = habitRepository.getAllHabitsSnapshot()

                if (journalSnapshot.isEmpty() && tasksSnapshot.isEmpty() && habitsSnapshot.isEmpty()) {
                    _events.tryEmit("Nothing to export yet.")
                    return@launch
                }

                val result = BackupManager.exportJsonToUri(
                    context = appContext,
                    uri = uri,
                    entries = journalSnapshot,
                    tasks = tasksSnapshot,
                    habits = habitsSnapshot,
                    habitCompletions = completionsSnapshot
                )
                result.fold(
                    onSuccess = { info ->
                        val parts = buildList {
                            if (info.entryCount > 0) add("${info.entryCount} journal")
                            if (info.taskCount > 0) add("${info.taskCount} tasks")
                            if (info.habitCount > 0) add("${info.habitCount} habits")
                        }
                        val summary = if (parts.isEmpty()) "0 items" else parts.joinToString(", ")
                        _events.tryEmit("JSON exported ($summary)")
                    },
                    onFailure = { error ->
                        _events.tryEmit(error.localizedMessage ?: "Couldn't export JSON. Please try again.")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun initDailyBackup(context: Context) {
        val appContext = context.applicationContext
        DailyBackupManager.refreshState(appContext)
        viewModelScope.launch {
            DailyBackupManager.runCatchUpBackupIfDue(appContext)
        }
    }

    fun setDailyBackupEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        DailyBackupManager.setDailyBackupEnabled(appContext, enabled)
        val config = DailyBackupManager.loadConfig(appContext)
        if (enabled) {
            _events.tryEmit("Daily backup scheduled at ${config.formattedScheduleTime()}")
        } else {
            _events.tryEmit("Daily auto-backup turned off")
        }
    }

    fun setDailyBackupHour(context: Context, hour: Int) {
        DailyBackupManager.setScheduleHour(context.applicationContext, hour)
    }

    fun setDailyBackupRetention(context: Context, days: Int) {
        DailyBackupManager.setRetentionDays(context.applicationContext, days)
    }

    fun setDailyBackupFolder(context: Context, treeUri: Uri?) {
        val appContext = context.applicationContext
        DailyBackupManager.setBackupFolder(appContext, treeUri)
        if (treeUri != null) {
            val name = DailyBackupManager.loadConfig(appContext).folderDisplayName ?: "selected folder"
            _events.tryEmit("Daily backups will also save to $name")
        } else {
            _events.tryEmit("Daily backups set to internal app vault")
        }
    }

    fun runDailyBackupNow(context: Context) {
        if (_isBackupBusy.value) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val result = DailyBackupManager.performDailyBackup(appContext)
                result.fold(
                    onSuccess = { info ->
                        if (info.entryCount == 0 && info.taskCount == 0 && info.habitCount == 0) {
                            _events.tryEmit("Nothing to back up yet.")
                        } else {
                            _events.tryEmit("Daily backup saved")
                        }
                    },
                    onFailure = { error ->
                        _events.tryEmit(error.localizedMessage ?: "Daily backup failed.")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun inspectSavedDailyBackup(context: Context, backup: SavedDailyBackup) {
        val fileUri = Uri.fromFile(File(backup.filePath))
        inspectImportBackup(context, fileUri)
    }

    fun deleteSavedDailyBackup(context: Context, backup: SavedDailyBackup) {
        DailyBackupManager.deleteLocalDailyBackup(context.applicationContext, backup.filePath)
        _events.tryEmit("Snapshot removed")
    }

    fun inspectImportBackup(context: Context, uri: Uri) {
        if (_isBackupBusy.value) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val result = BackupManager.inspectBackupUri(appContext, uri)
                result.fold(
                    onSuccess = { preview ->
                        if (preview.totalItemCount == 0) {
                            _events.tryEmit("This backup file doesn't contain any data.")
                        } else {
                            _pendingImportPreview.value = preview
                        }
                    },
                    onFailure = { error ->
                        _events.tryEmit(
                            error.localizedMessage ?: "Selected file is not a valid Daybook backup."
                        )
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun dismissImportPreview() {
        _pendingImportPreview.value = null
    }

    fun confirmImportBackup(context: Context, mode: ImportMode) {
        val preview = _pendingImportPreview.value ?: return
        if (_isBackupBusy.value) return
        val appContext = context.applicationContext
        _pendingImportPreview.value = null

        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val parsedResult = BackupManager.importBackupFromUri(appContext, preview.uri)
                parsedResult.fold(
                    onSuccess = { parsed ->
                        val journalResult = repository.importEntries(parsed.entries, mode)
                        if (journalResult.isFailure) {
                            parsed.extractedFilePaths.forEach { path ->
                                AttachmentAndLocationHelper.deletePrivateFile(path)
                            }
                            _events.tryEmit(
                                journalResult.exceptionOrNull()?.localizedMessage ?: "Couldn't import backup."
                            )
                            return@fold
                        }
                        val tasksImported = taskRepository.importTasks(parsed.tasks, mode).getOrDefault(0)
                        val habitsImported = habitRepository
                            .importHabits(parsed.habits, parsed.habitCompletions, mode)
                            .getOrDefault(0)
                        val journalImported = journalResult.getOrNull()?.totalImported ?: 0

                        val parts = buildList {
                            if (journalImported > 0) add("$journalImported journal")
                            if (tasksImported > 0) add("$tasksImported tasks")
                            if (habitsImported > 0) add("$habitsImported habits")
                        }
                        val summary = if (parts.isEmpty()) "0 items" else parts.joinToString(", ")
                        _events.tryEmit("Imported $summary")
                    },
                    onFailure = { error ->
                        _events.tryEmit(error.localizedMessage ?: "Couldn't read backup file.")
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "JournalVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
