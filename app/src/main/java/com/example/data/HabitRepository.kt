package com.example.data

import com.example.ui.DateFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class HabitRepository(
    private val dao: HabitDao
) {
    fun observeHabits(): Flow<List<HabitItem>> =
        dao.observeHabits().map { list -> list.map { it.toDomain() } }

    fun observeCompletions(): Flow<List<HabitCompletion>> =
        dao.observeAllCompletions().map { list -> list.map { it.toDomain() } }

    fun observeHabitsWithStats(selectedDayKeyFlow: Flow<Long>): Flow<List<HabitWithStats>> {
        return combine(
            observeHabits(),
            observeCompletions(),
            selectedDayKeyFlow
        ) { habits, completions, selectedDayKey ->
            val todayKey = DateFormatters.dayKey(System.currentTimeMillis())
            val byHabit = completions.groupBy { it.habitId }
            habits.map { habit ->
                val dayCounts = byHabit[habit.id]
                    ?.associate { it.dayKeyMillis to it.count }
                    .orEmpty()
                computeStats(habit, dayCounts, selectedDayKey, todayKey)
            }
        }
    }

    suspend fun getAllHabitsSnapshot(): Pair<List<HabitItem>, List<HabitCompletion>> {
        val habits = dao.getAllHabitsOnce().map { it.toDomain() }
        val completions = dao.getAllCompletionsOnce().map { it.toDomain() }
        return habits to completions
    }

    suspend fun saveHabit(
        habitId: String?,
        name: String,
        description: String,
        category: String,
        accent: HabitAccent,
        frequency: HabitFrequency,
        scheduledDays: Set<Int>,
        weeklyTarget: Int,
        dailyGoalCount: Int,
        unitLabel: String
    ): Result<String> {
        return try {
            val cleanName = name.trim().take(HabitConstraints.MAX_NAME_LENGTH)
            require(cleanName.isNotEmpty()) { "Habit name is required." }
            val cleanDesc = description.trim().take(HabitConstraints.MAX_DESCRIPTION_LENGTH)
            val cleanCat = category.trim().take(HabitConstraints.MAX_CATEGORY_LENGTH).ifBlank { "Health" }
            val cleanUnit = unitLabel.trim().take(HabitConstraints.MAX_UNIT_LENGTH)
            val cleanGoal = dailyGoalCount.coerceIn(1, HabitConstraints.MAX_DAILY_GOAL)
            val cleanWeekly = weeklyTarget.coerceIn(1, 7)
            val cleanDays = if (frequency == HabitFrequency.SPECIFIC_DAYS) {
                scheduledDays.filter { it in 1..7 }.toSet().ifEmpty { HabitItem.ALL_WEEKDAYS }
            } else {
                HabitItem.ALL_WEEKDAYS
            }

            val now = System.currentTimeMillis()
            val existing = habitId?.let { id ->
                dao.getAllHabitsOnce().firstOrNull { it.id == id }?.toDomain()
            }
            val finalId = existing?.id ?: UUID.randomUUID().toString()

            val entity = HabitEntity.fromDomain(
                HabitItem(
                    id = finalId,
                    name = cleanName,
                    description = cleanDesc,
                    category = cleanCat,
                    accent = accent,
                    frequency = frequency,
                    scheduledDays = cleanDays,
                    weeklyTarget = cleanWeekly,
                    dailyGoalCount = cleanGoal,
                    unitLabel = cleanUnit,
                    isArchived = existing?.isArchived ?: false,
                    createdAtMillis = existing?.createdAtMillis ?: now,
                    updatedAtMillis = now
                )
            )
            dao.upsertHabit(entity)
            Result.success(finalId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleOrStepCompletion(
        habit: HabitItem,
        dayKeyMillis: Long,
        delta: Int? = null
    ): Result<Int> {
        return try {
            val normalizedDay = DateFormatters.dayKey(dayKeyMillis)
            val existingCount = dao.getCompletion(habit.id, normalizedDay)?.count ?: 0
            val nextCount = if (delta != null) {
                (existingCount + delta).coerceIn(0, HabitConstraints.MAX_DAILY_GOAL)
            } else {
                // Single-tap toggle behavior:
                // If goal == 1, toggle between 0 and 1.
                // If goal > 1, increment until goal is reached, then wrap back to 0 on next tap.
                if (habit.dailyGoalCount <= 1) {
                    if (existingCount >= 1) 0 else 1
                } else {
                    if (existingCount >= habit.dailyGoalCount) 0 else existingCount + 1
                }
            }

            if (nextCount <= 0) {
                dao.deleteCompletion(habit.id, normalizedDay)
            } else {
                dao.upsertCompletion(
                    HabitCompletionEntity(
                        habitId = habit.id,
                        dayKeyMillis = normalizedDay,
                        count = nextCount
                    )
                )
            }
            Result.success(nextCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setArchived(habitId: String, archived: Boolean): Result<Unit> {
        return try {
            dao.setArchived(habitId, archived, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(habitId: String): Result<Unit> {
        return try {
            dao.deleteHabitById(habitId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importHabits(
        habits: List<HabitItem>,
        completions: List<HabitCompletion>,
        mode: ImportMode
    ): Result<Int> {
        return try {
            if (mode == ImportMode.REPLACE) {
                dao.deleteAllCompletions()
                dao.deleteAllHabits()
            }
            val habitEntities = habits.mapNotNull { h ->
                val cleanName = h.name.trim().take(HabitConstraints.MAX_NAME_LENGTH)
                if (cleanName.isEmpty()) return@mapNotNull null
                HabitEntity.fromDomain(
                    h.copy(
                        id = h.id.ifBlank { UUID.randomUUID().toString() },
                        name = cleanName,
                        description = h.description.trim().take(HabitConstraints.MAX_DESCRIPTION_LENGTH),
                        category = h.category.trim().take(HabitConstraints.MAX_CATEGORY_LENGTH).ifBlank { "Health" }
                    )
                )
            }
            val validIds = habitEntities.map { it.id }.toSet()
            if (habitEntities.isNotEmpty()) {
                dao.upsertHabits(habitEntities)
            }
            val validCompletions = completions
                .filter { it.habitId in validIds && it.count > 0 }
                .map {
                    HabitCompletionEntity(
                        habitId = it.habitId,
                        dayKeyMillis = DateFormatters.dayKey(it.dayKeyMillis),
                        count = it.count.coerceIn(1, HabitConstraints.MAX_DAILY_GOAL)
                    )
                }
            if (validCompletions.isNotEmpty()) {
                dao.upsertCompletions(validCompletions)
            }
            Result.success(habitEntities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun computeStats(
        habit: HabitItem,
        dayCounts: Map<Long, Int>,
        selectedDayKey: Long,
        todayKey: Long
    ): HabitWithStats {
        val goal = habit.dailyGoalCount.coerceAtLeast(1)
        val completedDaysSet = dayCounts.filterValues { it >= goal }.keys

        val selectedCount = dayCounts[selectedDayKey] ?: 0
        val isCompletedSelected = selectedCount >= goal

        // Current streak (looking backward from today, forgiving today if not yet done today)
        var currentStreak = 0
        var cursor = todayKey
        val doneToday = cursor in completedDaysSet
        if (!doneToday) {
            cursor = DateFormatters.addDaysToDayKey(cursor, -1)
        }
        var guard = 0
        while (guard < 3650) {
            val weekday = DateFormatters.dayOfWeek(cursor)
            val scheduled = habit.isScheduledOnWeekday(weekday)
            if (cursor in completedDaysSet) {
                currentStreak++
            } else if (scheduled) {
                break
            }
            cursor = DateFormatters.addDaysToDayKey(cursor, -1)
            guard++
        }

        // Best streak across history
        var bestStreak = currentStreak
        if (completedDaysSet.isNotEmpty()) {
            val minDay = completedDaysSet.min()
            val maxDay = maxOf(completedDaysSet.max(), todayKey)
            var run = 0
            var walk = minDay
            var steps = 0
            while (walk <= maxDay && steps < 3650) {
                if (walk in completedDaysSet) {
                    run++
                    if (run > bestStreak) bestStreak = run
                } else if (habit.isScheduledOnWeekday(DateFormatters.dayOfWeek(walk))) {
                    run = 0
                }
                walk = DateFormatters.addDaysToDayKey(walk, 1)
                steps++
            }
        }

        // 30-day completion rate
        var scheduledIn30 = 0
        var completedIn30 = 0
        for (offset in 0 until 30) {
            val day = DateFormatters.addDaysToDayKey(todayKey, -offset)
            val done = day in completedDaysSet
            if (done) completedIn30++
            if (habit.isScheduledOnWeekday(DateFormatters.dayOfWeek(day))) {
                scheduledIn30++
            }
        }
        val expected30 = when (habit.frequency) {
            HabitFrequency.WEEKLY_TARGET -> ((habit.weeklyTarget * 30f) / 7f).coerceAtLeast(1f)
            else -> scheduledIn30.coerceAtLeast(1).toFloat()
        }
        val rate30 = (completedIn30 / expected30).coerceIn(0f, 1f)

        // Last 7 days completed count
        var weekCompleted = 0
        for (offset in 0 until 7) {
            val day = DateFormatters.addDaysToDayKey(selectedDayKey, -offset)
            if (day in completedDaysSet) weekCompleted++
        }

        return HabitWithStats(
            habit = habit,
            selectedDayCount = selectedCount,
            isCompletedOnSelectedDay = isCompletedSelected,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            completionRate30d = rate30,
            totalCompletions = completedDaysSet.size,
            weekCompletedDays = weekCompleted,
            countsByDayKey = dayCounts
        )
    }
}
