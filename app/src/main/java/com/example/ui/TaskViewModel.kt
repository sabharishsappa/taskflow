package com.example.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppSettings
import com.example.data.AppTextScale
import com.example.data.AppThemeMode
import com.example.data.RepeatRule
import com.example.data.SettingsRepository
import com.example.data.SubtaskItem
import com.example.data.TaskColor
import com.example.data.TaskCompletionOutcome
import com.example.data.TaskDateUtils
import com.example.data.TaskItem
import com.example.data.TaskRepository
import com.example.data.TaskRowSize
import com.example.data.TaskSectionTab
import com.example.reminder.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class TaskEditorDraft(
    val editingTaskId: String? = null,
    val title: String = "",
    val notes: String = "",
    val completed: Boolean = false,
    val color: TaskColor = TaskColor.CORAL,
    val dueDateMillis: Long? = null,
    val reminderMillis: Long? = null,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val repeatInterval: Int = 1,
    val subtasks: List<SubtaskItem> = emptyList(),
    val isSaving: Boolean = false,
    val validationError: String? = null
) {
    val isNew: Boolean
        get() = editingTaskId == null
}

data class DashboardControlsState(
    val selectedSection: TaskSectionTab = TaskSectionTab.TIMELINE,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val expandedTaskIds: Set<String> = emptySet(),
    val editorDraft: TaskEditorDraft? = null,
    val isSettingsOpen: Boolean = false,
    val isBackupBusy: Boolean = false,
    val snackbarMessage: String? = null
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AppSettings()
        )

    val tasksState: StateFlow<UiState<List<TaskItem>>> = repository.observeTasks()
        .onEach { tasks -> reminderScheduler.syncReminders(tasks) }
        .map<List<TaskItem>, UiState<List<TaskItem>>> { rawTasks ->
            UiState.Success(sortTasks(rawTasks))
        }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing tasks", error)
            emit(UiState.Error(error.localizedMessage ?: "Unable to load tasks right now."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _controls = MutableStateFlow(DashboardControlsState())
    val controls: StateFlow<DashboardControlsState> = _controls.asStateFlow()

    fun selectSection(section: TaskSectionTab) {
        _controls.update { it.copy(selectedSection = section) }
    }

    fun setSearchActive(active: Boolean) {
        _controls.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (active) it.searchQuery else ""
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _controls.update { it.copy(searchQuery = query) }
    }

    fun toggleExpandTask(taskId: String) {
        _controls.update { current ->
            val next = current.expandedTaskIds.toMutableSet()
            if (!next.add(taskId)) next.remove(taskId)
            current.copy(expandedTaskIds = next)
        }
    }

    fun openNewTaskEditor(defaultColor: TaskColor? = null) {
        val chosenColor = defaultColor
            ?: TaskColor.entries[(System.currentTimeMillis() / 1000L % TaskColor.entries.size).toInt()]

        _controls.update {
            it.copy(
                editorDraft = TaskEditorDraft(
                    color = chosenColor
                )
            )
        }
    }

    fun openEditTaskEditor(task: TaskItem) {
        _controls.update {
            it.copy(
                editorDraft = TaskEditorDraft(
                    editingTaskId = task.id,
                    title = task.title,
                    notes = task.notes,
                    completed = task.completed,
                    color = task.taskColor,
                    dueDateMillis = task.dueDateMillis,
                    reminderMillis = task.reminderMillis,
                    repeatRule = task.repeatRuleEnum,
                    repeatInterval = task.repeatIntervalInt,
                    subtasks = task.subtasks
                )
            )
        }
    }

    fun closeEditor() {
        _controls.update { it.copy(editorDraft = null) }
    }

    fun updateDraft(transform: (TaskEditorDraft) -> TaskEditorDraft) {
        _controls.update { current ->
            val draft = current.editorDraft ?: return@update current
            current.copy(editorDraft = transform(draft).copy(validationError = null))
        }
    }

    fun addSubtaskToDraft(rawTitle: String) {
        val clean = rawTitle.trim().take(TaskRepository.MAX_SUBTASK_TITLE_LENGTH)
        if (clean.isEmpty()) return
        _controls.update { current ->
            val draft = current.editorDraft ?: return@update current
            if (draft.subtasks.size >= TaskRepository.MAX_SUBTASKS) {
                return@update current.copy(
                    editorDraft = draft.copy(validationError = "Maximum ${TaskRepository.MAX_SUBTASKS} subtasks reached.")
                )
            }
            val newSubtask = SubtaskItem(
                id = "sub_${UUID.randomUUID().toString().take(8)}",
                title = clean,
                done = false
            )
            current.copy(
                editorDraft = draft.copy(
                    subtasks = draft.subtasks + newSubtask,
                    validationError = null
                )
            )
        }
    }

    fun toggleDraftSubtask(subtaskId: String) {
        updateDraft { draft ->
            draft.copy(
                subtasks = draft.subtasks.map { sub ->
                    if (sub.id == subtaskId) sub.copy(done = !sub.done) else sub
                }
            )
        }
    }

    fun removeDraftSubtask(subtaskId: String) {
        updateDraft { draft ->
            draft.copy(subtasks = draft.subtasks.filterNot { it.id == subtaskId })
        }
    }

    fun saveEditorDraft() {
        val draft = _controls.value.editorDraft ?: return
        val trimmedTitle = draft.title.trim()
        if (trimmedTitle.isEmpty()) {
            _controls.update {
                it.copy(editorDraft = draft.copy(validationError = "Please enter a task title."))
            }
            return
        }
        if (draft.isSaving) return

        _controls.update { it.copy(editorDraft = draft.copy(isSaving = true, validationError = null)) }

        viewModelScope.launch {
            val cleanTitle = trimmedTitle.take(TaskRepository.MAX_TITLE_LENGTH)
            val cleanNotes = draft.notes.trim().take(TaskRepository.MAX_NOTES_LENGTH)
            val cleanInterval = draft.repeatInterval.coerceIn(1, 365)

            if (draft.editingTaskId == null) {
                val result = repository.createTask(
                    title = cleanTitle,
                    notes = cleanNotes,
                    color = draft.color,
                    dueDateMillis = draft.dueDateMillis,
                    reminderMillis = draft.reminderMillis,
                    repeatRule = draft.repeatRule,
                    repeatInterval = cleanInterval,
                    subtasks = draft.subtasks
                )
                result.fold(
                    onSuccess = { newId ->
                        val reminderAt = draft.reminderMillis
                        if (reminderAt != null && reminderAt > System.currentTimeMillis()) {
                            reminderScheduler.scheduleReminder(
                                taskId = newId,
                                title = cleanTitle,
                                subtitle = cleanNotes.ifBlank { "Scheduled PrismTask reminder" },
                                accentColorArgb = draft.color.notificationArgb(),
                                triggerAtMillis = reminderAt
                            )
                        }
                        val hasTimeline = draft.dueDateMillis != null || draft.reminderMillis != null
                        val currentSection = _controls.value.selectedSection
                        val createdMsg = when {
                            hasTimeline && currentSection == TaskSectionTab.OTHER_TASKS ->
                                "Task added to Timeline"
                            !hasTimeline && currentSection == TaskSectionTab.TIMELINE ->
                                "Task added to Other Tasks"
                            else -> "Task created"
                        }
                        _controls.update {
                            it.copy(
                                editorDraft = null,
                                snackbarMessage = createdMsg
                            )
                        }
                    },
                    onFailure = { err ->
                        _controls.update {
                            it.copy(
                                editorDraft = draft.copy(
                                    isSaving = false,
                                    validationError = err.localizedMessage ?: "Failed to create task."
                                )
                            )
                        }
                    }
                )
            } else {
                val taskId = draft.editingTaskId
                val result = repository.updateTask(
                    taskId = taskId,
                    title = cleanTitle,
                    notes = cleanNotes,
                    completed = draft.completed,
                    color = draft.color,
                    dueDateMillis = draft.dueDateMillis,
                    reminderMillis = draft.reminderMillis,
                    repeatRule = draft.repeatRule,
                    repeatInterval = cleanInterval,
                    subtasks = draft.subtasks
                )
                result.fold(
                    onSuccess = {
                        val reminderAt = draft.reminderMillis
                        if (!draft.completed && reminderAt != null && reminderAt > System.currentTimeMillis()) {
                            reminderScheduler.scheduleReminder(
                                taskId = taskId,
                                title = cleanTitle,
                                subtitle = cleanNotes.ifBlank { "Scheduled PrismTask reminder" },
                                accentColorArgb = draft.color.notificationArgb(),
                                triggerAtMillis = reminderAt
                            )
                        } else {
                            reminderScheduler.cancelReminder(taskId)
                        }
                        _controls.update {
                            it.copy(
                                editorDraft = null,
                                snackbarMessage = "Task updated"
                            )
                        }
                    },
                    onFailure = { err ->
                        _controls.update {
                            it.copy(
                                editorDraft = draft.copy(
                                    isSaving = false,
                                    validationError = err.localizedMessage ?: "Failed to update task."
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    fun toggleTaskCompletion(task: TaskItem) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompletion(task)
            result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is TaskCompletionOutcome.Repeated -> {
                            if (outcome.nextReminderMillis != null) {
                                reminderScheduler.scheduleReminder(
                                    taskId = task.id,
                                    title = task.title,
                                    subtitle = task.notes.ifBlank { "Repeating PrismTask reminder" },
                                    accentColorArgb = task.taskColor.notificationArgb(),
                                    triggerAtMillis = outcome.nextReminderMillis
                                )
                            } else {
                                reminderScheduler.cancelReminder(task.id)
                            }
                            val nextLabel = TaskDateUtils.formatDueDate(outcome.nextDueDateMillis)
                            showSnackbar("Nice work! Next occurrence scheduled for $nextLabel")
                        }
                        is TaskCompletionOutcome.Toggled -> {
                            if (outcome.completed) {
                                reminderScheduler.cancelReminder(task.id)
                            } else if (task.reminderMillis != null && task.reminderMillis > System.currentTimeMillis()) {
                                reminderScheduler.scheduleReminder(
                                    taskId = task.id,
                                    title = task.title,
                                    subtitle = task.notes.ifBlank { "Scheduled PrismTask reminder" },
                                    accentColorArgb = task.taskColor.notificationArgb(),
                                    triggerAtMillis = task.reminderMillis
                                )
                            }
                        }
                    }
                },
                onFailure = { err ->
                    showSnackbar(err.localizedMessage ?: "Couldn't update task status.")
                }
            )
        }
    }

    fun toggleSubtask(task: TaskItem, subtaskId: String) {
        viewModelScope.launch {
            val result = repository.toggleSubtask(task, subtaskId)
            result.onFailure { err ->
                showSnackbar(err.localizedMessage ?: "Couldn't update subtask.")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val result = repository.deleteTask(taskId)
            result.fold(
                onSuccess = {
                    reminderScheduler.cancelReminder(taskId)
                    _controls.update { current ->
                        val nextEditor = if (current.editorDraft?.editingTaskId == taskId) null else current.editorDraft
                        current.copy(
                            editorDraft = nextEditor,
                            snackbarMessage = "Task deleted"
                        )
                    }
                },
                onFailure = { err ->
                    showSnackbar(err.localizedMessage ?: "Couldn't delete task.")
                }
            )
        }
    }

    fun openSettings() {
        _controls.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _controls.update { it.copy(isSettingsOpen = false) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setTaskRowSize(size: TaskRowSize) {
        viewModelScope.launch { settingsRepository.setTaskRowSize(size) }
    }

    fun setTextScale(scale: AppTextScale) {
        viewModelScope.launch { settingsRepository.setTextScale(scale) }
    }

    fun setUse24HourTime(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUse24HourTime(enabled) }
    }

    fun setShowCompletedTasks(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowCompletedTasks(show) }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val result = repository.clearCompletedTasks()
            result.fold(
                onSuccess = { removedIds ->
                    removedIds.forEach { reminderScheduler.cancelReminder(it) }
                    val label = if (removedIds.size == 1) "1 completed task" else "${removedIds.size} completed tasks"
                    showSnackbar("Cleared $label")
                },
                onFailure = { err ->
                    showSnackbar(err.localizedMessage ?: "Couldn't clear completed tasks.")
                }
            )
        }
    }

    fun exportBackupToUri(contentResolver: ContentResolver, uri: Uri) {
        if (_controls.value.isBackupBusy) return
        _controls.update { it.copy(isBackupBusy = true) }

        viewModelScope.launch {
            val exportResult = repository.exportTasksJson()
            exportResult.fold(
                onSuccess = { (taskCount, jsonText) ->
                    val writeResult = withContext(Dispatchers.IO) {
                        runCatching {
                            val outputStream = contentResolver.openOutputStream(uri, "wt")
                                ?: contentResolver.openOutputStream(uri)
                                ?: throw IllegalStateException("Unable to open destination file.")
                            outputStream.use { stream ->
                                stream.write(jsonText.toByteArray(StandardCharsets.UTF_8))
                                stream.flush()
                            }
                        }
                    }
                    writeResult.fold(
                        onSuccess = {
                            val label = if (taskCount == 1) "1 task" else "$taskCount tasks"
                            _controls.update {
                                it.copy(
                                    isBackupBusy = false,
                                    snackbarMessage = "Backup saved ($label)"
                                )
                            }
                        },
                        onFailure = { err ->
                            Log.w(TAG, "Backup export write failed", err)
                            _controls.update {
                                it.copy(
                                    isBackupBusy = false,
                                    snackbarMessage = err.localizedMessage ?: "Couldn't write backup file."
                                )
                            }
                        }
                    )
                },
                onFailure = { err ->
                    _controls.update {
                        it.copy(
                            isBackupBusy = false,
                            snackbarMessage = err.localizedMessage ?: "Couldn't prepare backup."
                        )
                    }
                }
            )
        }
    }

    fun importBackupFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        replaceExisting: Boolean
    ) {
        if (_controls.value.isBackupBusy) return
        _controls.update { it.copy(isBackupBusy = true) }

        viewModelScope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open the selected file.")
                    inputStream.use { stream ->
                        val buffer = ByteArray(8192)
                        val out = ByteArrayOutputStream()
                        var totalBytes = 0
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            totalBytes += read
                            if (totalBytes > MAX_BACKUP_FILE_BYTES) {
                                throw IllegalArgumentException("Backup file is too large (max 10 MB).")
                            }
                            out.write(buffer, 0, read)
                        }
                        out.toString(StandardCharsets.UTF_8.name())
                    }
                }
            }

            readResult.fold(
                onSuccess = { rawJson ->
                    val importResult = repository.importTasksJson(
                        rawJson = rawJson,
                        replaceExisting = replaceExisting
                    )
                    importResult.fold(
                        onSuccess = { summary ->
                            summary.removedTaskIds.forEach { removedId ->
                                reminderScheduler.cancelReminder(removedId)
                            }
                            val msg = when {
                                summary.importedCount == 0 && replaceExisting ->
                                    "Backup restored (0 tasks)"
                                summary.importedCount == 0 ->
                                    "No valid tasks found in the backup file"
                                replaceExisting -> {
                                    val countLabel = if (summary.importedCount == 1) "1 task" else "${summary.importedCount} tasks"
                                    "Restored $countLabel from backup"
                                }
                                else -> {
                                    val countLabel = if (summary.importedCount == 1) "1 task" else "${summary.importedCount} tasks"
                                    "Imported $countLabel from backup"
                                }
                            }
                            _controls.update {
                                it.copy(
                                    isBackupBusy = false,
                                    editorDraft = if (replaceExisting) null else it.editorDraft,
                                    snackbarMessage = msg
                                )
                            }
                        },
                        onFailure = { err ->
                            _controls.update {
                                it.copy(
                                    isBackupBusy = false,
                                    snackbarMessage = err.localizedMessage ?: "Couldn't import backup file."
                                )
                            }
                        }
                    )
                },
                onFailure = { err ->
                    Log.w(TAG, "Backup import read failed", err)
                    _controls.update {
                        it.copy(
                            isBackupBusy = false,
                            snackbarMessage = err.localizedMessage ?: "Couldn't read backup file."
                        )
                    }
                }
            )
        }
    }

    fun showSnackbar(message: String) {
        _controls.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        _controls.update { it.copy(snackbarMessage = null) }
    }

    private fun sortTasks(tasks: List<TaskItem>): List<TaskItem> {
        return TaskDateUtils.sortTasksChronologically(tasks)
    }

    companion object {
        private const val TAG = "TaskViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val MAX_BACKUP_FILE_BYTES = 10 * 1024 * 1024
    }
}
