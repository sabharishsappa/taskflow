package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val accentKey: String,
    val frequency: String,
    val scheduledDaysCsv: String,
    val weeklyTarget: Int,
    val dailyGoalCount: Int,
    val unitLabel: String,
    val isArchived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): HabitItem {
        val parsedDays = scheduledDaysCsv
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
            .ifEmpty { HabitItem.ALL_WEEKDAYS }

        return HabitItem(
            id = id,
            name = name,
            description = description,
            category = category,
            accent = HabitAccent.fromKey(accentKey),
            frequency = HabitFrequency.fromName(frequency),
            scheduledDays = parsedDays,
            weeklyTarget = weeklyTarget.coerceIn(1, 7),
            dailyGoalCount = dailyGoalCount.coerceIn(1, HabitConstraints.MAX_DAILY_GOAL),
            unitLabel = unitLabel,
            isArchived = isArchived,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromDomain(habit: HabitItem): HabitEntity {
            return HabitEntity(
                id = habit.id.ifBlank { UUID.randomUUID().toString() },
                name = habit.name,
                description = habit.description,
                category = habit.category,
                accentKey = habit.accent.key,
                frequency = habit.frequency.name,
                scheduledDaysCsv = habit.scheduledDays.sorted().joinToString(","),
                weeklyTarget = habit.weeklyTarget.coerceIn(1, 7),
                dailyGoalCount = habit.dailyGoalCount.coerceIn(1, HabitConstraints.MAX_DAILY_GOAL),
                unitLabel = habit.unitLabel,
                isArchived = habit.isArchived,
                createdAtMillis = habit.createdAtMillis,
                updatedAtMillis = habit.updatedAtMillis
            )
        }
    }
}

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "dayKeyMillis"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId"]), Index(value = ["dayKeyMillis"])]
)
data class HabitCompletionEntity(
    val habitId: String,
    val dayKeyMillis: Long,
    val count: Int
) {
    fun toDomain(): HabitCompletion = HabitCompletion(
        habitId = habitId,
        dayKeyMillis = dayKeyMillis,
        count = count
    )

    companion object {
        fun fromDomain(completion: HabitCompletion): HabitCompletionEntity = HabitCompletionEntity(
            habitId = completion.habitId,
            dayKeyMillis = completion.dayKeyMillis,
            count = completion.count
        )
    }
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY isArchived ASC, createdAtMillis ASC")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_completions")
    fun observeAllCompletions(): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAtMillis ASC")
    suspend fun getAllHabitsOnce(): List<HabitEntity>

    @Query("SELECT * FROM habit_completions")
    suspend fun getAllCompletionsOnce(): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND dayKeyMillis = :dayKeyMillis LIMIT 1")
    suspend fun getCompletion(habitId: String, dayKeyMillis: Long): HabitCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabits(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: HabitCompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<HabitCompletionEntity>)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dayKeyMillis = :dayKeyMillis")
    suspend fun deleteCompletion(habitId: String, dayKeyMillis: Long)

    @Query("UPDATE habits SET isArchived = :archived, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAtMillis: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: String)

    @Query("DELETE FROM habit_completions")
    suspend fun deleteAllCompletions()

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()
}
