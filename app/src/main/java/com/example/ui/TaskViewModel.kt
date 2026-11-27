package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SubTask
import com.example.data.TaskConstraints
import com.example.data.TaskItem
import com.example.data.TaskPriority
import com.example.data.TaskRecurrence
import com.example.data.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskViewFilter(val label: String) {
    ALL("Active"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue"),
    COMPLETED("Completed")
}

enum class TaskSortOption(val label: String) {
    SMART("Smart"),
    PRIORITY("Priority"),
    DUE_DATE("Due Date"),
    NEWEST("Newest")
}

data class TaskFilterState(
    val viewFilter: TaskViewFilter = TaskViewFilter.ALL,
    val query: String = "",
    val selectedCategory: String? = null,
    val selectedPriority: TaskPriority? = null,
    val sortOption: TaskSortOption = TaskSortOption.SMART
) {
    val hasExtraFilters: Boolean
        get() = query.isNotBlank() || selectedCategory != null || selectedPriority != null
}

data class TaskSummaryStats(
    val activeCount: Int = 0,
    val overdueCount: Int = 0,
    val todayTotalCount: Int = 0,
    val todayCompletedCount: Int = 0,
    val completedCount: Int = 0
)

class TaskViewModel(
    private val repository: TaskRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(TaskFilterState())
    val filterState: StateFlow<TaskFilterState> = _filterState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val allTasks: StateFlow<List<TaskItem>> = repository.observeTasks()
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing tasks", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val availableCategories: StateFlow<List<String>> = allTasks.map { tasks ->
        (TaskConstraints.DEFAULT_CATEGORIES + tasks.map { it.category })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TaskConstraints.DEFAULT_CATEGORIES
    )

    val summaryStats: StateFlow<TaskSummaryStats> = allTasks.map { tasks ->
        val todayKey = DateFormatters.dayKey(System.currentTimeMillis())
        val active = tasks.filter { !it.isCompleted }
        val completed = tasks.filter { it.isCompleted }
        val overdue = active.count { t ->
            t.dueDateMillis != null && DateFormatters.dayKey(t.dueDateMillis) < todayKey
        }
        val dueTodayOrCompletedToday = tasks.filter { t ->
            val dueToday = t.dueDateMillis != null && DateFormatters.dayKey(t.dueDateMillis) == todayKey
            val completedToday = t.isCompleted && t.completedAtMillis != null &&
                DateFormatters.dayKey(t.completedAtMillis) == todayKey
            dueToday || completedToday
        }
        val completedToday = dueTodayOrCompletedToday.count { it.isCompleted }
        TaskSummaryStats(
            activeCount = active.size,
            overdueCount = overdue,
            todayTotalCount = dueTodayOrCompletedToday.size,
            todayCompletedCount = completedToday,
            completedCount = completed.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TaskSummaryStats()
    )

    val filteredTasks: StateFlow<List<TaskItem>> = combine(allTasks, filterState) { tasks, filter ->
        val todayKey = DateFormatters.dayKey(System.currentTimeMillis())
        val q = filter.query.trim()

        val matched = tasks.filter { task ->
            val dueKey = task.dueDateMillis?.let { DateFormatters.dayKey(it) }
            val matchesView = when (filter.viewFilter) {
                TaskViewFilter.ALL -> !task.isCompleted
                TaskViewFilter.TODAY -> !task.isCompleted && dueKey != null && dueKey <= todayKey
                TaskViewFilter.UPCOMING -> !task.isCompleted && dueKey != null && dueKey > todayKey
                TaskViewFilter.OVERDUE -> !task.isCompleted && dueKey != null && dueKey < todayKey
                TaskViewFilter.COMPLETED -> task.isCompleted
            }
            val matchesCategory = filter.selectedCategory == null ||
                task.category.equals(filter.selectedCategory, ignoreCase = true)
            val matchesPriority = filter.selectedPriority == null ||
                task.priority == filter.selectedPriority
            val matchesQuery = q.isEmpty() ||
                task.title.contains(q, ignoreCase = true) ||
                task.notes.contains(q, ignoreCase = true) ||
                task.category.contains(q, ignoreCase = true) ||
                task.subTasks.any { it.title.contains(q, ignoreCase = true) }

            matchesView && matchesCategory && matchesPriority && matchesQuery
        }

        if (filter.viewFilter == TaskViewFilter.COMPLETED) {
            matched.sortedByDescending { it.completedAtMillis ?: it.updatedAtMillis }
        } else {
            when (filter.sortOption) {
                TaskSortOption.SMART -> matched.sortedWith(
                    compareBy<TaskItem> { it.dueDateMillis?.let { d -> DateFormatters.dayKey(d) } ?: Long.MAX_VALUE }
                        .thenByDescending { it.priority.sortRank }
                        .thenByDescending { it.createdAtMillis }
                )

                TaskSortOption.PRIORITY -> matched.sortedWith(
                    compareByDescending<TaskItem> { it.priority.sortRank }
                        .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
                        .thenByDescending { it.createdAtMillis }
                )

                TaskSortOption.DUE_DATE -> matched.sortedWith(
                    compareBy<TaskItem> { it.dueDateMillis ?: Long.MAX_VALUE }
                        .thenByDescending { it.priority.sortRank }
                )

                TaskSortOption.NEWEST -> matched.sortedByDescending { it.createdAtMillis }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun selectViewFilter(viewFilter: TaskViewFilter) {
        _filterState.update { it.copy(viewFilter = viewFilter) }
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun selectCategory(category: String?) {
        _filterState.update { it.copy(selectedCategory = category) }
    }

    fun selectPriority(priority: TaskPriority?) {
        _filterState.update { it.copy(selectedPriority = priority) }
    }

    fun selectSortOption(option: TaskSortOption) {
        _filterState.update { it.copy(sortOption = option) }
    }

    fun clearExtraFilters() {
        _filterState.update {
            it.copy(query = "", selectedCategory = null, selectedPriority = null)
        }
    }

    fun saveTask(
        taskId: String?,
        title: String,
        notes: String,
        dueDateMillis: Long?,
        priority: TaskPriority,
        category: String,
        recurrence: TaskRecurrence,
        subTasks: List<SubTask>,
        isCompleted: Boolean? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.saveTask(
                taskId = taskId,
                title = title,
                notes = notes,
                dueDateMillis = dueDateMillis,
                priority = priority,
                category = category,
                recurrence = recurrence,
                subTasks = subTasks,
                isCompleted = isCompleted
            )
            result.fold(
                onSuccess = { spawnedRecurring ->
                    val msg = when {
                        spawnedRecurring -> "Task completed · Next ${recurrence.label.lowercase()} task scheduled"
                        taskId == null -> "Task added"
                        else -> "Task updated"
                    }
                    _events.tryEmit(msg)
                    onSuccess()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Couldn't save task.")
                }
            )
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompleted(task)
            result.fold(
                onSuccess = { spawnedRecurring ->
                    if (spawnedRecurring) {
                        _events.tryEmit("Task completed · Next ${task.recurrence.label.lowercase()} task scheduled")
                    }
                },
                onFailure = {
                    _events.tryEmit("Couldn't update task.")
                }
            )
        }
    }

    fun toggleSubTask(task: TaskItem, subTaskId: String) {
        viewModelScope.launch {
            repository.toggleSubTask(task, subTaskId)
        }
    }

    fun addSubTask(
        task: TaskItem,
        title: String,
        dueDateMillis: Long? = null,
        priority: TaskPriority = TaskPriority.NONE
    ) {
        viewModelScope.launch {
            val result = repository.addSubTask(task, title, dueDateMillis, priority)
            result.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Couldn't add subtask.")
            }
        }
    }

    fun updateSubTask(task: TaskItem, subTask: SubTask) {
        viewModelScope.launch {
            val result = repository.updateSubTask(task, subTask)
            result.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Couldn't update subtask.")
            }
        }
    }

    fun deleteSubTask(task: TaskItem, subTaskId: String) {
        viewModelScope.launch {
            repository.deleteSubTask(task, subTaskId)
        }
    }

    fun setAllSubTasksCompleted(task: TaskItem, completed: Boolean) {
        viewModelScope.launch {
            repository.setAllSubTasksCompleted(task, completed)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val result = repository.deleteTask(taskId)
            result.onSuccess { _events.tryEmit("Task deleted") }
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            val result = repository.clearCompletedTasks()
            result.onSuccess { _events.tryEmit("Completed tasks cleared") }
        }
    }

    private companion object {
        const val TAG = "TaskVM"
    }
}
