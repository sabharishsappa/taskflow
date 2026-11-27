package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HabitAccent
import com.example.data.HabitConstraints
import com.example.data.HabitFrequency
import com.example.data.HabitItem
import com.example.data.HabitRepository
import com.example.data.HabitWithStats
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

data class HabitFilterState(
    val query: String = "",
    val selectedCategory: String? = null,
    val showArchived: Boolean = false
)

data class HabitDayOverview(
    val scheduledCount: Int = 0,
    val completedCount: Int = 0,
    val bestCurrentStreak: Int = 0
) {
    val progress: Float
        get() = if (scheduledCount <= 0) 0f else (completedCount.toFloat() / scheduledCount).coerceIn(0f, 1f)
}

class HabitViewModel(
    private val repository: HabitRepository
) : ViewModel() {

    private val _selectedDayKey = MutableStateFlow(DateFormatters.dayKey(System.currentTimeMillis()))
    val selectedDayKey: StateFlow<Long> = _selectedDayKey.asStateFlow()

    private val _filterState = MutableStateFlow(HabitFilterState())
    val filterState: StateFlow<HabitFilterState> = _filterState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val allHabitsWithStats: StateFlow<List<HabitWithStats>> =
        repository.observeHabitsWithStats(_selectedDayKey)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing habits", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val availableCategories: StateFlow<List<String>> = allHabitsWithStats.map { list ->
        (HabitConstraints.DEFAULT_CATEGORIES + list.map { it.habit.category })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HabitConstraints.DEFAULT_CATEGORIES
    )

    val dayOverview: StateFlow<HabitDayOverview> = combine(
        allHabitsWithStats,
        selectedDayKey
    ) { habits, dayKey ->
        val active = habits.filter { !it.habit.isArchived }
        val weekday = DateFormatters.dayOfWeek(dayKey)
        val scheduled = active.filter { it.habit.isScheduledOnWeekday(weekday) }
        val completed = scheduled.count { it.isCompletedOnSelectedDay }
        val bestActiveStreak = active.maxOfOrNull { it.currentStreak } ?: 0
        HabitDayOverview(
            scheduledCount = scheduled.size,
            completedCount = completed,
            bestCurrentStreak = bestActiveStreak
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HabitDayOverview()
    )

    val filteredHabits: StateFlow<List<HabitWithStats>> = combine(
        allHabitsWithStats,
        filterState,
        selectedDayKey
    ) { habits, filter, dayKey ->
        val q = filter.query.trim()
        val weekday = DateFormatters.dayOfWeek(dayKey)
        habits.filter { item ->
            val matchesArchived = if (filter.showArchived) item.habit.isArchived else !item.habit.isArchived
            val matchesCategory = filter.selectedCategory == null ||
                item.habit.category.equals(filter.selectedCategory, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                item.habit.name.contains(q, ignoreCase = true) ||
                item.habit.description.contains(q, ignoreCase = true) ||
                item.habit.category.contains(q, ignoreCase = true)
            matchesArchived && matchesCategory && matchesQuery
        }.sortedWith(
            compareByDescending<HabitWithStats> { it.habit.isScheduledOnWeekday(weekday) }
                .thenBy { it.isCompletedOnSelectedDay }
                .thenBy { it.habit.createdAtMillis }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val archivedCount: StateFlow<Int> = allHabitsWithStats.map { list ->
        list.count { it.habit.isArchived }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = 0
    )

    fun selectDay(dayKeyMillis: Long) {
        _selectedDayKey.value = DateFormatters.dayKey(dayKeyMillis)
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(query = query) }
    }

    fun selectCategory(category: String?) {
        _filterState.update { it.copy(selectedCategory = category) }
    }

    fun toggleShowArchived() {
        _filterState.update { it.copy(showArchived = !it.showArchived) }
    }

    fun saveHabit(
        habitId: String?,
        name: String,
        description: String,
        category: String,
        accent: HabitAccent,
        frequency: HabitFrequency,
        scheduledDays: Set<Int>,
        weeklyTarget: Int,
        dailyGoalCount: Int,
        unitLabel: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.saveHabit(
                habitId = habitId,
                name = name,
                description = description,
                category = category,
                accent = accent,
                frequency = frequency,
                scheduledDays = scheduledDays,
                weeklyTarget = weeklyTarget,
                dailyGoalCount = dailyGoalCount,
                unitLabel = unitLabel
            )
            result.fold(
                onSuccess = {
                    _events.tryEmit(if (habitId == null) "Habit created" else "Habit updated")
                    onSuccess()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Couldn't save habit.")
                }
            )
        }
    }

    fun toggleHabitForDay(habit: HabitItem, dayKeyMillis: Long = _selectedDayKey.value) {
        viewModelScope.launch {
            repository.toggleOrStepCompletion(habit, dayKeyMillis, delta = null)
        }
    }

    fun stepHabitForDay(habit: HabitItem, delta: Int, dayKeyMillis: Long = _selectedDayKey.value) {
        viewModelScope.launch {
            repository.toggleOrStepCompletion(habit, dayKeyMillis, delta = delta)
        }
    }

    fun setArchived(habitId: String, archived: Boolean) {
        viewModelScope.launch {
            val result = repository.setArchived(habitId, archived)
            result.onSuccess {
                _events.tryEmit(if (archived) "Habit archived" else "Habit restored")
            }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            val result = repository.deleteHabit(habitId)
            result.onSuccess { _events.tryEmit("Habit deleted") }
        }
    }

    private companion object {
        const val TAG = "HabitVM"
    }
}
