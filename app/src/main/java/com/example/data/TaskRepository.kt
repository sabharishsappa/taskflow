package com.example.data

import com.example.data.local.TaskDao
import com.example.data.local.TaskEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao
) {
    fun observeTasks(): Flow<List<TaskItem>> =
        taskDao.observeTasks().map { entities -> entities.map { it.toDomain() } }

    suspend fun createTask(
        title: String,
        notes: String,
        color: TaskColor,
        dueDateMillis: Long?,
        reminderMillis: Long?,
        repeatRule: RepeatRule,
        repeatInterval: Int,
        subtasks: List<SubtaskItem>
    ): Result<String> {
        return try {
            val cleanTitle = title.trim()
            val cleanNotes = notes.trim()
            val cleanInterval = repeatInterval.coerceIn(1, 365)
            val cleanSubtasks = sanitizeSubtasks(subtasks)

            require(cleanTitle.isNotEmpty() && cleanTitle.length <= MAX_TITLE_LENGTH) {
                "Task title must be 1..$MAX_TITLE_LENGTH characters."
            }
            require(cleanNotes.length <= MAX_NOTES_LENGTH) {
                "Notes cannot exceed $MAX_NOTES_LENGTH characters."
            }
            require(cleanSubtasks.size <= MAX_SUBTASKS) {
                "Tasks support up to $MAX_SUBTASKS subtasks."
            }

            val id = "task_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val now = System.currentTimeMillis()
            val entity = TaskEntity(
                id = id,
                title = cleanTitle,
                notes = cleanNotes,
                completed = false,
                colorKey = color.name,
                dueDateMillis = dueDateMillis?.takeIf { it > 0L },
                reminderMillis = reminderMillis?.takeIf { it > 0L },
                repeatRule = repeatRule.name,
                repeatInterval = cleanInterval.toLong(),
                subtasksJson = TaskEntity.encodeSubtasks(cleanSubtasks),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            taskDao.upsertTask(entity)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        taskId: String,
        title: String,
        notes: String,
        completed: Boolean,
        color: TaskColor,
        dueDateMillis: Long?,
        reminderMillis: Long?,
        repeatRule: RepeatRule,
        repeatInterval: Int,
        subtasks: List<SubtaskItem>
    ): Result<Unit> {
        return try {
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            val existing = taskDao.getTaskById(taskId)
            val cleanTitle = title.trim()
            val cleanNotes = notes.trim()
            val cleanInterval = repeatInterval.coerceIn(1, 365)
            val cleanSubtasks = sanitizeSubtasks(subtasks)

            require(cleanTitle.isNotEmpty() && cleanTitle.length <= MAX_TITLE_LENGTH) {
                "Task title must be 1..$MAX_TITLE_LENGTH characters."
            }
            require(cleanNotes.length <= MAX_NOTES_LENGTH) {
                "Notes cannot exceed $MAX_NOTES_LENGTH characters."
            }
            require(cleanSubtasks.size <= MAX_SUBTASKS) {
                "Tasks support up to $MAX_SUBTASKS subtasks."
            }

            val now = System.currentTimeMillis()
            val entity = TaskEntity(
                id = taskId,
                title = cleanTitle,
                notes = cleanNotes,
                completed = completed,
                colorKey = color.name,
                dueDateMillis = dueDateMillis?.takeIf { it > 0L },
                reminderMillis = reminderMillis?.takeIf { it > 0L },
                repeatRule = repeatRule.name,
                repeatInterval = cleanInterval.toLong(),
                subtasksJson = TaskEntity.encodeSubtasks(cleanSubtasks),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            taskDao.upsertTask(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompletion(task: TaskItem): Result<TaskCompletionOutcome> {
        return try {
            require(task.id.isNotBlank()) { "Task ID must not be blank." }
            val existing = taskDao.getTaskById(task.id) ?: return Result.failure(
                IllegalStateException("Task not found.")
            )
            val now = System.currentTimeMillis()
            val rule = task.repeatRuleEnum

            if (!task.completed && rule != RepeatRule.NONE) {
                val baseDue = task.dueDateMillis ?: TaskDateUtils.daysFromTodayMillis(0)
                val nextDue = TaskDateUtils.computeNextOccurrence(
                    baseMillis = baseDue,
                    rule = rule,
                    interval = task.repeatIntervalInt,
                    minMillis = TaskDateUtils.endOfDayMillis(now)
                )
                val nextReminder = task.reminderMillis?.let { oldReminder ->
                    TaskDateUtils.computeNextOccurrence(
                        baseMillis = oldReminder,
                        rule = rule,
                        interval = task.repeatIntervalInt,
                        minMillis = now
                    )
                }
                val resetSubtasks = task.subtasks.map { it.copy(done = false) }
                taskDao.upsertTask(
                    existing.copy(
                        completed = false,
                        dueDateMillis = nextDue,
                        reminderMillis = nextReminder,
                        subtasksJson = TaskEntity.encodeSubtasks(resetSubtasks),
                        updatedAtMillis = now
                    )
                )
                Result.success(TaskCompletionOutcome.Repeated(nextDue, nextReminder))
            } else {
                val nextCompleted = !task.completed
                taskDao.upsertTask(
                    existing.copy(
                        completed = nextCompleted,
                        updatedAtMillis = now
                    )
                )
                Result.success(TaskCompletionOutcome.Toggled(nextCompleted))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleSubtask(task: TaskItem, subtaskId: String): Result<Unit> {
        return try {
            require(task.id.isNotBlank()) { "Task ID must not be blank." }
            val existing = taskDao.getTaskById(task.id) ?: return Result.failure(
                IllegalStateException("Task not found.")
            )
            val updatedSubtasks = task.subtasks.map { sub ->
                if (sub.id == subtaskId) sub.copy(done = !sub.done) else sub
            }
            taskDao.upsertTask(
                existing.copy(
                    subtasksJson = TaskEntity.encodeSubtasks(updatedSubtasks),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            taskDao.deleteTaskById(taskId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCompletedTasks(): Result<List<String>> {
        return try {
            val completedIds = taskDao.getCompletedTaskIds()
            if (completedIds.isNotEmpty()) {
                taskDao.deleteCompletedTasks()
            }
            Result.success(completedIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportTasksJson(): Result<Pair<Int, String>> {
        return try {
            val tasks = taskDao.getAllTasks().map { it.toDomain() }
            val json = TaskBackupSerializer.encode(tasks)
            Result.success(tasks.size to json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importTasksJson(
        rawJson: String,
        replaceExisting: Boolean
    ): Result<ImportBackupSummary> {
        return try {
            val parsedTasks = TaskBackupSerializer.decode(rawJson).getOrElse { error ->
                return Result.failure(error)
            }
            val existingTasks = taskDao.getAllTasks()
            val entities = parsedTasks.map { task ->
                TaskEntity(
                    id = task.id,
                    title = task.title,
                    notes = task.notes,
                    completed = task.completed,
                    colorKey = task.colorKey,
                    dueDateMillis = task.dueDateMillis,
                    reminderMillis = task.reminderMillis,
                    repeatRule = task.repeatRule,
                    repeatInterval = task.repeatInterval,
                    subtasksJson = TaskEntity.encodeSubtasks(sanitizeSubtasks(task.subtasks)),
                    createdAtMillis = task.createdAtMillis,
                    updatedAtMillis = task.updatedAtMillis
                )
            }

            if (replaceExisting) {
                taskDao.replaceAllTasks(entities)
                val importedIds = entities.mapTo(hashSetOf()) { it.id }
                val removedIds = existingTasks.map { it.id }.filterNot { it in importedIds }
                Result.success(
                    ImportBackupSummary(
                        importedCount = entities.size,
                        replacedPreviousCount = existingTasks.size,
                        removedTaskIds = removedIds
                    )
                )
            } else {
                if (entities.isNotEmpty()) {
                    taskDao.upsertTasks(entities)
                }
                Result.success(
                    ImportBackupSummary(
                        importedCount = entities.size,
                        replacedPreviousCount = 0,
                        removedTaskIds = emptyList()
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeSubtasks(subtasks: List<SubtaskItem>): List<SubtaskItem> {
        return subtasks
            .mapNotNull { sub ->
                val cleanTitle = sub.title.trim().take(MAX_SUBTASK_TITLE_LENGTH)
                val cleanId = sub.id.trim().take(64).ifEmpty { "sub_${System.nanoTime()}" }
                if (cleanTitle.isEmpty()) null else SubtaskItem(id = cleanId, title = cleanTitle, done = sub.done)
            }
            .take(MAX_SUBTASKS)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_NOTES_LENGTH = 500
        const val MAX_SUBTASK_TITLE_LENGTH = 120
        const val MAX_SUBTASKS = 30
    }
}
