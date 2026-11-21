package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TaskRepository
import com.example.model.DayDate
import com.example.model.ListIconOption
import com.example.model.ProjectColorPalette
import com.example.model.TaskItem
import com.example.model.TaskList
import com.example.notifications.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    data class Error(val message: String) : DataState<Nothing>
}

enum class MainDestination {
    TIMELINE,
    CALENDAR,
    LISTS,
}

data class TaskEditorDraft(
    val existingTask: TaskItem? = null,
    val prefillDate: DayDate = DayDate.today(),
    val prefillListId: String? = null,
)

data class ListEditorDraft(
    val existingList: TaskList? = null,
)

data class DayloomUiControls(
    val currentTab: MainDestination = MainDestination.TIMELINE,
    val timelineListFilterId: String? = null,
    val showCompletedInTimeline: Boolean = false,
    val calendarSelectedDate: DayDate = DayDate.today(),
    val calendarDisplayedMonth: DayDate = DayDate.today().firstOfMonth(),
    val calendarListFilterId: String? = null,
    val selectedListDetailId: String? = null,
    val taskEditorDraft: TaskEditorDraft? = null,
    val listEditorDraft: ListEditorDraft? = null,
    val snackbarMessage: String? = null,
    val isMutating: Boolean = false,
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _controls = MutableStateFlow(DayloomUiControls())
    val controls: StateFlow<DayloomUiControls> = _controls.asStateFlow()

    val listsState: StateFlow<DataState<List<TaskList>>> = repository.observeLists()
        .map<List<TaskList>, DataState<List<TaskList>>> { DataState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing lists", error)
            emit(DataState.Error(error.message ?: "Unable to load your lists."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DataState.Loading,
        )

    val tasksState: StateFlow<DataState<List<TaskItem>>> = repository.observeTasks()
        .map<List<TaskItem>, DataState<List<TaskItem>>> { DataState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing tasks", error)
            emit(DataState.Error(error.message ?: "Unable to load your tasks."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DataState.Loading,
        )

    init {
        viewModelScope.launch {
            combine(listsState, tasksState) { listsRes, tasksRes ->
                val lists = (listsRes as? DataState.Success)?.data
                val tasks = (tasksRes as? DataState.Success)?.data
                if (lists != null && tasks != null) {
                    lists.associateBy { it.id } to tasks
                } else {
                    null
                }
            }.collect { pair ->
                if (pair != null) {
                    reminderScheduler.syncReminders(pair.second, pair.first)
                }
            }
        }
    }

    fun selectTab(tab: MainDestination) {
        _controls.update {
            it.copy(
                currentTab = tab,
                selectedListDetailId = if (tab == MainDestination.LISTS) it.selectedListDetailId else null,
            )
        }
    }

    fun setTimelineListFilter(listId: String?) {
        _controls.update { it.copy(timelineListFilterId = listId) }
    }

    fun toggleShowCompletedInTimeline() {
        _controls.update { it.copy(showCompletedInTimeline = !it.showCompletedInTimeline) }
    }

    fun selectCalendarDate(date: DayDate) {
        _controls.update {
            it.copy(
                calendarSelectedDate = date,
                calendarDisplayedMonth = date.firstOfMonth(),
            )
        }
    }

    fun shiftCalendarMonth(deltaMonths: Int) {
        _controls.update {
            val nextMonth = it.calendarDisplayedMonth.plusMonths(deltaMonths).firstOfMonth()
            it.copy(calendarDisplayedMonth = nextMonth)
        }
    }

    fun jumpCalendarToToday() {
        val today = DayDate.today()
        _controls.update {
            it.copy(
                calendarSelectedDate = today,
                calendarDisplayedMonth = today.firstOfMonth(),
            )
        }
    }

    fun setCalendarListFilter(listId: String?) {
        _controls.update { it.copy(calendarListFilterId = listId) }
    }

    fun openListDetail(listId: String?) {
        _controls.update { it.copy(selectedListDetailId = listId) }
    }

    fun openNewTaskEditor(
        prefillDate: DayDate? = null,
        prefillListId: String? = null,
    ) {
        val state = _controls.value
        val defaultDate = prefillDate ?: when (state.currentTab) {
            MainDestination.CALENDAR -> state.calendarSelectedDate
            else -> DayDate.today()
        }
        val defaultListId = prefillListId
            ?: state.selectedListDetailId
            ?: when (state.currentTab) {
                MainDestination.TIMELINE -> state.timelineListFilterId
                MainDestination.CALENDAR -> state.calendarListFilterId
                MainDestination.LISTS -> null
            }
        _controls.update {
            it.copy(
                taskEditorDraft = TaskEditorDraft(
                    existingTask = null,
                    prefillDate = defaultDate,
                    prefillListId = defaultListId,
                )
            )
        }
    }

    fun openEditTaskEditor(task: TaskItem) {
        _controls.update {
            it.copy(
                taskEditorDraft = TaskEditorDraft(
                    existingTask = task,
                    prefillDate = task.dueDate(),
                    prefillListId = task.listId,
                )
            )
        }
    }

    fun dismissTaskEditor() {
        _controls.update { it.copy(taskEditorDraft = null) }
    }

    fun openNewListEditor() {
        _controls.update { it.copy(listEditorDraft = ListEditorDraft(existingList = null)) }
    }

    fun openEditListEditor(list: TaskList) {
        _controls.update { it.copy(listEditorDraft = ListEditorDraft(existingList = list)) }
    }

    fun dismissListEditor() {
        _controls.update { it.copy(listEditorDraft = null) }
    }

    fun clearSnackbar() {
        _controls.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(message: String) {
        _controls.update { it.copy(snackbarMessage = message) }
    }

    fun saveList(
        existingList: TaskList?,
        name: String,
        colorHex: String,
        iconKey: String,
        onCreated: ((String) -> Unit)? = null,
    ) {
        val currentLists = (listsState.value as? DataState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _controls.update { it.copy(isMutating = true) }
            val result = if (existingList == null) {
                repository.addList(
                    name = name,
                    colorHex = colorHex,
                    iconKey = iconKey,
                    sortOrder = currentLists.size.toLong(),
                )
            } else {
                repository.updateList(
                    listId = existingList.id,
                    name = name,
                    colorHex = colorHex,
                    iconKey = iconKey,
                ).map { existingList.id }
            }

            result.fold(
                onSuccess = { createdOrUpdatedId ->
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            listEditorDraft = null,
                            snackbarMessage = if (existingList == null) "List \"$name\" created" else "List updated",
                        )
                    }
                    onCreated?.invoke(createdOrUpdatedId)
                },
                onFailure = { error ->
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            snackbarMessage = error.message ?: "Could not save list.",
                        )
                    }
                }
            )
        }
    }

    fun deleteList(list: TaskList) {
        val allTasks = (tasksState.value as? DataState.Success)?.data.orEmpty()
        val tasksInList = allTasks.filter { it.listId == list.id }
        viewModelScope.launch {
            _controls.update { it.copy(isMutating = true) }
            val result = repository.deleteList(list.id)
            result.fold(
                onSuccess = {
                    tasksInList.forEach { reminderScheduler.cancelReminder(it.id) }
                    _controls.update { state ->
                        state.copy(
                            isMutating = false,
                            selectedListDetailId = if (state.selectedListDetailId == list.id) null else state.selectedListDetailId,
                            timelineListFilterId = if (state.timelineListFilterId == list.id) null else state.timelineListFilterId,
                            calendarListFilterId = if (state.calendarListFilterId == list.id) null else state.calendarListFilterId,
                            snackbarMessage = "Deleted list \"${list.name}\"",
                        )
                    }
                },
                onFailure = { error ->
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            snackbarMessage = error.message ?: "Could not delete list.",
                        )
                    }
                }
            )
        }
    }

    fun saveTask(
        existingTask: TaskItem?,
        title: String,
        notes: String?,
        dueDate: DayDate,
        reminderEpochMillis: Long?,
        selectedListId: String?,
    ) {
        viewModelScope.launch {
            _controls.update { it.copy(isMutating = true) }

            val resolvedListId = if (!selectedListId.isNullOrBlank()) {
                selectedListId
            } else {
                val existingLists = (listsState.value as? DataState.Success)?.data.orEmpty()
                if (existingLists.isNotEmpty()) {
                    existingLists.first().id
                } else {
                    val createdDefault = repository.addList(
                        name = "Personal",
                        colorHex = ProjectColorPalette.DEFAULT_HEX,
                        iconKey = ListIconOption.PERSONAL.key,
                        sortOrder = 0L,
                    )
                    val newId = createdDefault.getOrNull()
                    if (newId == null) {
                        _controls.update {
                            it.copy(
                                isMutating = false,
                                snackbarMessage = createdDefault.exceptionOrNull()?.message
                                    ?: "Could not create a list for this task.",
                            )
                        }
                        return@launch
                    }
                    newId
                }
            }

            val listsById = (listsState.value as? DataState.Success)?.data
                ?.associateBy { it.id }
                .orEmpty()
            val listName = listsById[resolvedListId]?.name ?: "Personal"

            val result = if (existingTask == null) {
                repository.addTask(
                    listId = resolvedListId,
                    title = title,
                    notes = notes,
                    dueDateEpochDay = dueDate.toEpochDay(),
                    reminderEpochMillis = reminderEpochMillis,
                )
            } else {
                repository.updateTask(
                    taskId = existingTask.id,
                    listId = resolvedListId,
                    title = title,
                    notes = notes,
                    dueDateEpochDay = dueDate.toEpochDay(),
                    reminderEpochMillis = reminderEpochMillis,
                    completed = existingTask.completed,
                    completedAtEpochMillis = existingTask.completedAtEpochMillis,
                ).map { existingTask.id }
            }

            result.fold(
                onSuccess = { taskId ->
                    val isCompleted = existingTask?.completed == true
                    if (!isCompleted && reminderEpochMillis != null && reminderEpochMillis > System.currentTimeMillis()) {
                        reminderScheduler.scheduleReminder(
                            taskId = taskId,
                            title = title.trim(),
                            listName = listName,
                            triggerAtMillis = reminderEpochMillis,
                        )
                    } else {
                        reminderScheduler.cancelReminder(taskId)
                    }
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            taskEditorDraft = null,
                            snackbarMessage = if (existingTask == null) "Task added" else "Task updated",
                        )
                    }
                },
                onFailure = { error ->
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            snackbarMessage = error.message ?: "Could not save task.",
                        )
                    }
                }
            )
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        val nextCompleted = !task.completed
        viewModelScope.launch {
            val result = repository.toggleTaskCompleted(
                taskId = task.id,
                completed = nextCompleted,
            )
            result.fold(
                onSuccess = {
                    if (nextCompleted) {
                        reminderScheduler.cancelReminder(task.id)
                    } else {
                        val rem = task.reminderEpochMillis
                        if (rem != null && rem > System.currentTimeMillis()) {
                            val listName = (listsState.value as? DataState.Success)?.data
                                ?.firstOrNull { it.id == task.listId }?.name ?: "Personal"
                            reminderScheduler.scheduleReminder(task.id, task.title, listName, rem)
                        }
                    }
                },
                onFailure = { error ->
                    _controls.update {
                        it.copy(snackbarMessage = error.message ?: "Could not update task.")
                    }
                }
            )
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            _controls.update { it.copy(isMutating = true) }
            val result = repository.deleteTask(task.id)
            result.fold(
                onSuccess = {
                    reminderScheduler.cancelReminder(task.id)
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            taskEditorDraft = null,
                            snackbarMessage = "Task deleted",
                        )
                    }
                },
                onFailure = { error ->
                    _controls.update {
                        it.copy(
                            isMutating = false,
                            snackbarMessage = error.message ?: "Could not delete task.",
                        )
                    }
                }
            )
        }
    }

    private companion object {
        const val TAG = "TaskViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
