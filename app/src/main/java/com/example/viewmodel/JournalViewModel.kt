package com.example.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppLockPreferencesRepository
import com.example.data.AppLockSettings
import com.example.data.AutoLockTimeout
import com.example.data.BackupFrequency
import com.example.data.BackupPreferencesRepository
import com.example.data.BackupSettings
import com.example.data.ImportInspectionResult
import com.example.data.JournalAttachment
import com.example.data.JournalBackupManager
import com.example.data.JournalEntry
import com.example.data.JournalImportManager
import com.example.data.JournalRepository
import com.example.data.JournalStats
import com.example.data.LocalBackupSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ImportUiState {
    data object Hidden : ImportUiState
    data object Ready : ImportUiState
    data object Inspecting : ImportUiState
    data class Preview(val inspection: ImportInspectionResult) : ImportUiState
    data object Importing : ImportUiState
    data class Error(val message: String) : ImportUiState
}

enum class SearchScope {
    ALL,
    TITLE,
    TAGS,
    CONTENT,
}

enum class TagMatchMode {
    ANY,
    ALL,
}

enum class DateFilterPreset {
    ANY,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    CUSTOM,
}

data class JournalFilterState(
    val searchQuery: String = "",
    val searchScope: SearchScope = SearchScope.ALL,
    val selectedTags: Set<String> = emptySet(),
    val tagMatchMode: TagMatchMode = TagMatchMode.ANY,
    val datePreset: DateFilterPreset = DateFilterPreset.ANY,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val bookmarkedOnly: Boolean = false,
) {
    val hasDateFilter: Boolean
        get() = datePreset != DateFilterPreset.ANY &&
            (datePreset != DateFilterPreset.CUSTOM || customStartMillis != null)

    val activeFilterCount: Int
        get() = selectedTags.size +
            (if (hasDateFilter) 1 else 0) +
            (if (bookmarkedOnly) 1 else 0)

    val hasActiveFilter: Boolean
        get() = searchQuery.isNotBlank() || activeFilterCount > 0
}

data class DateGroup(
    val dayKey: Int,
    val dateMillis: Long,
    val entries: List<JournalEntry>,
)

data class JournalFeedUiModel(
    val allEntries: List<JournalEntry>,
    val filteredGroups: List<DateGroup>,
    val matchedCount: Int,
    val stats: JournalStats,
    val allTags: List<String>,
    val recentLocations: List<String>,
    val filter: JournalFilterState,
)

class JournalViewModel(
    private val repository: JournalRepository,
    private val backupManager: JournalBackupManager,
    private val importManager: JournalImportManager,
    private val backupPreferences: BackupPreferencesRepository,
    private val lockPreferences: AppLockPreferencesRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(JournalFilterState())
    val filter: StateFlow<JournalFilterState> = _filter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Hidden)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _localBackups = MutableStateFlow<List<LocalBackupSnapshot>>(emptyList())
    val localBackups: StateFlow<List<LocalBackupSnapshot>> = _localBackups.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val backupSettings: StateFlow<BackupSettings> = backupPreferences.settingsFlow
        .catch { emit(BackupSettings()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BackupSettings(),
        )

    val lockSettings: StateFlow<AppLockSettings?> = lockPreferences.settingsFlow
        .catch { emit(AppLockSettings()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    init {
        viewModelScope.launch {
            backupManager.runScheduledBackupIfDue()
            _localBackups.value = backupManager.listLocalBackups()
        }
    }

    private val rawEntriesState: StateFlow<UiState<List<JournalEntry>>> =
        repository.observeEntries()
            .map<List<JournalEntry>, UiState<List<JournalEntry>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing journal entries", error)
                emit(UiState.Error(error.localizedMessage ?: "Failed to load journal entries."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val feedState: StateFlow<UiState<JournalFeedUiModel>> =
        combine(rawEntriesState, _filter) { entriesState, activeFilter ->
            when (entriesState) {
                is UiState.Loading -> UiState.Loading
                is UiState.Error -> entriesState
                is UiState.Success -> {
                    val allEntries = entriesState.data
                    val filtered = applyFilters(allEntries, activeFilter)
                    UiState.Success(
                        JournalFeedUiModel(
                            allEntries = allEntries,
                            filteredGroups = groupByDate(filtered),
                            matchedCount = filtered.size,
                            stats = computeStats(allEntries),
                            allTags = extractAllTags(allEntries),
                            recentLocations = extractRecentLocations(allEntries),
                            filter = activeFilter,
                        )
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    fun updateSearchQuery(query: String) {
        _filter.value = _filter.value.copy(searchQuery = query)
    }

    fun updateSearchScope(scope: SearchScope) {
        _filter.value = _filter.value.copy(searchScope = scope)
    }

    fun toggleTag(tag: String) {
        val current = _filter.value.selectedTags
        val existing = current.firstOrNull { it.equals(tag, ignoreCase = true) }
        val updated = if (existing != null) current - existing else current + tag
        _filter.value = _filter.value.copy(selectedTags = updated)
    }

    fun selectTag(tag: String?) {
        if (tag == null) {
            _filter.value = _filter.value.copy(selectedTags = emptySet())
        } else {
            toggleTag(tag)
        }
    }

    fun setTagMatchMode(mode: TagMatchMode) {
        _filter.value = _filter.value.copy(tagMatchMode = mode)
    }

    fun setDatePreset(preset: DateFilterPreset) {
        if (preset == DateFilterPreset.CUSTOM) {
            _filter.value = _filter.value.copy(datePreset = DateFilterPreset.CUSTOM)
        } else {
            _filter.value = _filter.value.copy(
                datePreset = preset,
                customStartMillis = null,
                customEndMillis = null,
            )
        }
    }

    fun setCustomDateRange(startMillis: Long, endMillis: Long?) {
        val normalizedStart = if (endMillis != null && endMillis < startMillis) endMillis else startMillis
        val normalizedEnd = if (endMillis != null && endMillis < startMillis) startMillis else endMillis
        _filter.value = _filter.value.copy(
            datePreset = DateFilterPreset.CUSTOM,
            customStartMillis = normalizedStart,
            customEndMillis = normalizedEnd,
        )
    }

    fun clearDateFilter() {
        _filter.value = _filter.value.copy(
            datePreset = DateFilterPreset.ANY,
            customStartMillis = null,
            customEndMillis = null,
        )
    }

    fun toggleBookmarkedFilter() {
        _filter.value = _filter.value.copy(bookmarkedOnly = !_filter.value.bookmarkedOnly)
    }

    fun clearNonSearchFilters() {
        val current = _filter.value
        _filter.value = JournalFilterState(
            searchQuery = current.searchQuery,
            searchScope = current.searchScope,
        )
    }

    fun clearFilters() {
        _filter.value = JournalFilterState()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showMessage(message: String) {
        _statusMessage.value = message
    }

    fun saveEntry(
        existingEntryId: String?,
        heading: String,
        body: String,
        entryDateMillis: Long,
        locationName: String,
        locationLat: Double?,
        locationLng: Double?,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        bookmarked: Boolean,
        onComplete: (savedId: String?) -> Unit,
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (existingEntryId.isNullOrBlank()) {
                    val result = repository.createEntry(
                        heading = heading,
                        body = body,
                        entryDateMillis = entryDateMillis,
                        locationName = locationName,
                        locationLat = locationLat,
                        locationLng = locationLng,
                        tags = tags,
                        attachments = attachments,
                        bookmarked = bookmarked,
                    )
                    result.onSuccess { newId ->
                        _statusMessage.value = "Entry saved to your journal"
                        onComplete(newId)
                        triggerOnChangeBackup()
                    }.onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Couldn't save entry. Please try again."
                        onComplete(null)
                    }
                } else {
                    val result = repository.updateEntry(
                        entryId = existingEntryId,
                        heading = heading,
                        body = body,
                        entryDateMillis = entryDateMillis,
                        locationName = locationName,
                        locationLat = locationLat,
                        locationLng = locationLng,
                        tags = tags,
                        attachments = attachments,
                        bookmarked = bookmarked,
                    )
                    result.onSuccess {
                        _statusMessage.value = "Entry updated"
                        onComplete(existingEntryId)
                        triggerOnChangeBackup()
                    }.onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Couldn't update entry. Please try again."
                        onComplete(null)
                    }
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleBookmark(entry: JournalEntry) {
        viewModelScope.launch {
            repository.toggleBookmark(entry.id, !entry.bookmarked)
                .onSuccess { triggerOnChangeBackup() }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Couldn't update bookmark."
                }
        }
    }

    fun deleteEntry(entryId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
                .onSuccess {
                    _statusMessage.value = "Entry deleted"
                    onDeleted()
                    triggerOnChangeBackup()
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Couldn't delete entry."
                }
        }
    }

    fun setBackupFrequency(frequency: BackupFrequency) {
        viewModelScope.launch {
            backupPreferences.setFrequency(frequency)
            _statusMessage.value = "Backup frequency set to ${frequency.label}"
        }
    }

    fun setIncludeAttachmentsInBackup(include: Boolean) {
        viewModelScope.launch {
            backupPreferences.setIncludeAttachments(include)
        }
    }

    fun refreshLocalBackups() {
        viewModelScope.launch {
            _localBackups.value = backupManager.listLocalBackups()
        }
    }

    fun createLocalBackupNow() {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val includeMedia = backupPreferences.getSettingsOnce().includeAttachments
                backupManager.createLocalBackup(includeMedia)
                    .onSuccess { summary ->
                        _localBackups.value = backupManager.listLocalBackups()
                        val mediaPart = if (summary.attachmentCount > 0) {
                            " and ${summary.attachmentCount} attachment${if (summary.attachmentCount == 1) "" else "s"}"
                        } else {
                            ""
                        }
                        _statusMessage.value = "Backed up ${summary.entryCount} entr${if (summary.entryCount == 1) "y" else "ies"}$mediaPart"
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Backup failed. Please try again."
                    }
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun exportBackupToUri(uri: Uri) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val includeMedia = backupPreferences.getSettingsOnce().includeAttachments
                backupManager.exportBackupToUri(uri, includeMedia)
                    .onSuccess { summary ->
                        _statusMessage.value = "Exported ${summary.entryCount} entr${if (summary.entryCount == 1) "y" else "ies"} to backup file"
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Export failed. Please try again."
                    }
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                backupManager.restoreFromUri(uri)
                    .onSuccess { summary ->
                        _statusMessage.value = "Restored ${summary.restoredEntries} entr${if (summary.restoredEntries == 1) "y" else "ies"}"
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Couldn't restore backup file."
                    }
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun restoreLocalSnapshot(snapshot: LocalBackupSnapshot) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                backupManager.restoreFromLocalBackup(snapshot.filePath)
                    .onSuccess { summary ->
                        _statusMessage.value = "Restored ${summary.restoredEntries} entr${if (summary.restoredEntries == 1) "y" else "ies"}"
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Couldn't restore snapshot."
                    }
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun deleteLocalSnapshot(snapshot: LocalBackupSnapshot) {
        viewModelScope.launch {
            if (backupManager.deleteLocalBackup(snapshot.filePath)) {
                _localBackups.value = backupManager.listLocalBackups()
                _statusMessage.value = "Backup snapshot deleted"
            }
        }
    }

    fun shareLocalSnapshot(activityContext: android.content.Context, snapshot: LocalBackupSnapshot) {
        val shared = backupManager.shareLocalBackup(activityContext, snapshot)
        if (!shared) {
            _statusMessage.value = "Couldn't share backup file."
        }
    }

    fun suggestedExportFileName(): String {
        return backupManager.suggestedExportFileName(backupSettings.value.includeAttachments)
    }

    fun openImportSheet() {
        _importState.value = ImportUiState.Ready
    }

    fun setBiometricLockEnabled(enabled: Boolean, message: String? = null) {
        viewModelScope.launch {
            lockPreferences.setLockEnabled(enabled)
            if (message != null) {
                _statusMessage.value = message
            }
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            lockPreferences.setAutoLockTimeout(timeout)
            _statusMessage.value = "Auto-lock set to ${timeout.label}"
        }
    }

    fun setHideInAppSwitcher(hide: Boolean) {
        viewModelScope.launch {
            lockPreferences.setHideInAppSwitcher(hide)
        }
    }

    fun dismissImportSheet() {
        if (_importState.value !is ImportUiState.Importing) {
            _importState.value = ImportUiState.Hidden
        }
    }

    fun inspectImportUri(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportUiState.Inspecting
            importManager.inspectUri(uri)
                .onSuccess { inspection ->
                    _importState.value = ImportUiState.Preview(inspection)
                }
                .onFailure { err ->
                    _importState.value = ImportUiState.Error(
                        err.localizedMessage ?: "Couldn't read journal entries from that file."
                    )
                }
        }
    }

    fun confirmImport(inspection: ImportInspectionResult) {
        if (_importState.value is ImportUiState.Importing) return
        viewModelScope.launch {
            _importState.value = ImportUiState.Importing
            importManager.executeImport(inspection)
                .onSuccess { summary ->
                    _importState.value = ImportUiState.Hidden
                    val attPart = if (summary.importedAttachments > 0) {
                        " and ${summary.importedAttachments} attachment${if (summary.importedAttachments == 1) "" else "s"}"
                    } else {
                        ""
                    }
                    _statusMessage.value = "Imported ${summary.importedEntries} entr${if (summary.importedEntries == 1) "y" else "ies"}$attPart"
                    triggerOnChangeBackup()
                }
                .onFailure { err ->
                    _importState.value = ImportUiState.Error(
                        err.localizedMessage ?: "Couldn't import entries from this file."
                    )
                }
        }
    }

    private fun triggerOnChangeBackup() {
        viewModelScope.launch {
            backupManager.runOnChangeBackupIfEnabled()
            _localBackups.value = backupManager.listLocalBackups()
        }
    }

    private fun applyFilters(entries: List<JournalEntry>, filter: JournalFilterState): List<JournalEntry> {
        val locale = Locale.getDefault()
        val rawTokens = filter.searchQuery
            .trim()
            .lowercase(locale)
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        val dateRangeKeys = resolveDateRangeKeys(filter)

        return entries.filter { entry ->
            if (filter.bookmarkedOnly && !entry.bookmarked) return@filter false

            if (filter.selectedTags.isNotEmpty()) {
                val tagMatches = when (filter.tagMatchMode) {
                    TagMatchMode.ANY -> filter.selectedTags.any { selected ->
                        entry.tags.any { it.equals(selected, ignoreCase = true) }
                    }
                    TagMatchMode.ALL -> filter.selectedTags.all { selected ->
                        entry.tags.any { it.equals(selected, ignoreCase = true) }
                    }
                }
                if (!tagMatches) return@filter false
            }

            if (dateRangeKeys != null) {
                val entryDayKey = toLocalDateKey(entry.entryDateMillis)
                if (entryDayKey < dateRangeKeys.first || entryDayKey > dateRangeKeys.second) {
                    return@filter false
                }
            }

            if (rawTokens.isNotEmpty()) {
                val headingLower = entry.heading.lowercase(locale)
                val bodyLower = entry.body.lowercase(locale)
                val locationLower = entry.locationName.lowercase(locale)
                val tagsLower = entry.tags.map { it.lowercase(locale) }

                val matchesAllTokens = rawTokens.all { token ->
                    if (token.startsWith("#") && token.length > 1) {
                        val tagNeedle = token.removePrefix("#")
                        tagsLower.any { it.contains(tagNeedle) }
                    } else {
                        when (filter.searchScope) {
                            SearchScope.ALL -> {
                                headingLower.contains(token) ||
                                    bodyLower.contains(token) ||
                                    locationLower.contains(token) ||
                                    tagsLower.any { it.contains(token) }
                            }
                            SearchScope.TITLE -> headingLower.contains(token)
                            SearchScope.TAGS -> tagsLower.any { it.contains(token) }
                            SearchScope.CONTENT -> bodyLower.contains(token)
                        }
                    }
                }
                if (!matchesAllTokens) return@filter false
            }
            true
        }
    }

    private fun resolveDateRangeKeys(filter: JournalFilterState): Pair<Int, Int>? {
        val todayCal = Calendar.getInstance()
        val todayKey = toLocalDateKey(todayCal)
        return when (filter.datePreset) {
            DateFilterPreset.ANY -> null
            DateFilterPreset.TODAY -> todayKey to todayKey
            DateFilterPreset.LAST_7_DAYS -> {
                val start = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
                toLocalDateKey(start) to todayKey
            }
            DateFilterPreset.LAST_30_DAYS -> {
                val start = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29) }
                toLocalDateKey(start) to todayKey
            }
            DateFilterPreset.THIS_MONTH -> {
                val start = (todayCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                val end = (todayCal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                toLocalDateKey(start) to toLocalDateKey(end)
            }
            DateFilterPreset.CUSTOM -> {
                val startMillis = filter.customStartMillis ?: return null
                val endMillis = filter.customEndMillis ?: startMillis
                toLocalDateKey(startMillis) to toLocalDateKey(endMillis)
            }
        }
    }

    private fun groupByDate(entries: List<JournalEntry>): List<DateGroup> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedWith(
            compareByDescending<JournalEntry> { toLocalDateKey(it.entryDateMillis) }
                .thenByDescending { it.entryDateMillis }
                .thenByDescending { it.createdAtMillis }
        )
        val grouped = linkedMapOf<Int, MutableList<JournalEntry>>()
        for (entry in sorted) {
            val dayKey = toLocalDateKey(entry.entryDateMillis)
            grouped.getOrPut(dayKey) { mutableListOf() }.add(entry)
        }
        return grouped.map { (dayKey, list) ->
            DateGroup(
                dayKey = dayKey,
                dateMillis = list.first().entryDateMillis,
                entries = list,
            )
        }
    }

    private fun toLocalDateKey(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return toLocalDateKey(cal)
    }

    private fun toLocalDateKey(cal: Calendar): Int {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return year * 10_000 + month * 100 + day
    }

    private fun computeStats(entries: List<JournalEntry>): JournalStats {
        if (entries.isEmpty()) return JournalStats()
        val totalWords = entries.sumOf { it.wordCount }
        val dayKeys = entries.map { toEpochDay(it.entryDateMillis) }.toSet()
        val todayDay = toEpochDay(System.currentTimeMillis())
        var streak = 0
        var cursor = if (todayDay in dayKeys) todayDay else todayDay - 1
        while (cursor in dayKeys) {
            streak++
            cursor--
        }
        return JournalStats(
            totalEntries = entries.size,
            totalWords = totalWords,
            daysJournaled = dayKeys.size,
            currentStreakDays = streak,
        )
    }

    private fun toEpochDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / MILLIS_PER_DAY
    }

    private fun extractAllTags(entries: List<JournalEntry>): List<String> {
        val counts = linkedMapOf<String, Int>()
        entries.forEach { entry ->
            entry.tags.forEach { tag ->
                val clean = tag.trim()
                if (clean.isNotEmpty()) {
                    counts[clean] = (counts[clean] ?: 0) + 1
                }
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun extractRecentLocations(entries: List<JournalEntry>): List<String> {
        return entries
            .map { it.locationName.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .take(8)
    }

    private companion object {
        const val TAG = "JournalViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
