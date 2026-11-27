package com.example.data

import com.example.ui.DateFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TaskRepository(
    private val dao: TaskDao
) {
    fun observeTasks(): Flow<List<TaskItem>> =
        dao.observeTasks().map { list -> list.map { it.toDomain() } }

    suspend fun getAllTasksSnapshot(): List<TaskItem> =
        dao.getAllTasksOnce().map { it.toDomain() }

    suspend fun saveTask(
        taskId: String?,
        title: String,
        notes: String,
        dueDateMillis: Long?,
        priority: TaskPriority,
        category: String,
        recurrence: TaskRecurrence,
        subTasks: List<SubTask>,
        isCompleted: Boolean? = null
    ): Result<Boolean> {
        return try {
            val cleanTitle = title.trim().take(TaskConstraints.MAX_TITLE_LENGTH)
            require(cleanTitle.isNotEmpty()) { "Task title is required." }
            val cleanNotes = notes.trim().take(TaskConstraints.MAX_NOTES_LENGTH)
            val cleanCategory = category.trim().take(TaskConstraints.MAX_CATEGORY_LENGTH)
            val cleanSubTasks = subTasks
                .mapNotNull { st ->
                    val t = st.title.trim().take(TaskConstraints.MAX_SUBTASK_LENGTH)
                    if (t.isEmpty()) null else st.copy(
                        id = st.id.ifBlank { UUID.randomUUID().toString() },
                        title = t
                    )
                }
                .take(TaskConstraints.MAX_SUBTASKS)

            val now = System.currentTimeMillis()
            val existing = taskId?.let { dao.getTaskById(it)?.toDomain() }
            val finalId = existing?.id ?: UUID.randomUUID().toString()
            val wasCompleted = existing?.isCompleted ?: false
            val finalCompleted = isCompleted ?: wasCompleted
            val finalCompletedAt = when {
                finalCompleted && wasCompleted -> existing?.completedAtMillis ?: now
                finalCompleted -> now
                else -> null
            }

            val savedTask = TaskItem(
                id = finalId,
                title = cleanTitle,
                notes = cleanNotes,
                dueDateMillis = dueDateMillis,
                priority = priority,
                category = cleanCategory,
                recurrence = recurrence,
                subTasks = cleanSubTasks,
                isCompleted = finalCompleted,
                completedAtMillis = finalCompletedAt,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            dao.upsertTask(TaskEntity.fromDomain(savedTask))

            var spawnedRecurring = false
            if (!wasCompleted && finalCompleted && recurrence != TaskRecurrence.NONE) {
                spawnNextRecurringOccurrence(savedTask, now)
                spawnedRecurring = true
            }
            Result.success(spawnedRecurring)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggles task completion. When completing a recurring task, marks the current occurrence
     * completed and automatically creates the next scheduled occurrence with reset subtasks.
     */
    suspend fun toggleTaskCompleted(task: TaskItem): Result<Boolean> {
        return try {
            val now = System.currentTimeMillis()
            val markCompleted = !task.isCompleted
            val updated = task.copy(
                isCompleted = markCompleted,
                completedAtMillis = if (markCompleted) now else null,
                updatedAtMillis = now
            )
            dao.upsertTask(TaskEntity.fromDomain(updated))

            var spawnedRecurring = false
            if (markCompleted && task.recurrence != TaskRecurrence.NONE) {
                spawnNextRecurringOccurrence(task, now)
                spawnedRecurring = true
            }
            Result.success(spawnedRecurring)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun spawnNextRecurringOccurrence(task: TaskItem, now: Long) {
        val baseDate = task.dueDateMillis ?: DateFormatters.todayMiddayMillis()
        val nextDue = when (task.recurrence) {
            TaskRecurrence.DAILY -> DateFormatters.addDays(baseDate, 1)
            TaskRecurrence.WEEKLY -> DateFormatters.addDays(baseDate, 7)
            TaskRecurrence.MONTHLY -> DateFormatters.addMonths(baseDate, 1)
            TaskRecurrence.NONE -> baseDate
        }
        val nextTask = task.copy(
            id = UUID.randomUUID().toString(),
            dueDateMillis = nextDue,
            subTasks = task.subTasks.map { it.copy(isCompleted = false) },
            isCompleted = false,
            completedAtMillis = null,
            createdAtMillis = now + 1,
            updatedAtMillis = now + 1
        )
        dao.upsertTask(TaskEntity.fromDomain(nextTask))
    }

    suspend fun toggleSubTask(task: TaskItem, subTaskId: String): Result<Unit> {
        return try {
            val updatedSubs = task.subTasks.map { st ->
                if (st.id == subTaskId) st.copy(isCompleted = !st.isCompleted) else st
            }
            val updatedTask = task.copy(
                subTasks = updatedSubs,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTask(TaskEntity.fromDomain(updatedTask))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSubTask(
        task: TaskItem,
        title: String,
        dueDateMillis: Long? = null,
        priority: TaskPriority = TaskPriority.NONE
    ): Result<Unit> {
        return try {
            val clean = title.trim().take(TaskConstraints.MAX_SUBTASK_LENGTH)
            require(clean.isNotEmpty()) { "Subtask title is required." }
            if (task.subTasks.size >= TaskConstraints.MAX_SUBTASKS) {
                return Result.failure(IllegalStateException("Maximum ${TaskConstraints.MAX_SUBTASKS} subtasks reached."))
            }
            val newSub = SubTask(
                id = UUID.randomUUID().toString(),
                title = clean,
                isCompleted = false,
                dueDateMillis = dueDateMillis,
                priority = priority
            )
            val updatedTask = task.copy(
                subTasks = task.subTasks + newSub,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTask(TaskEntity.fromDomain(updatedTask))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSubTask(task: TaskItem, updatedSubTask: SubTask): Result<Unit> {
        return try {
            val clean = updatedSubTask.title.trim().take(TaskConstraints.MAX_SUBTASK_LENGTH)
            require(clean.isNotEmpty()) { "Subtask title is required." }
            val updatedSubs = task.subTasks.map { st ->
                if (st.id == updatedSubTask.id) updatedSubTask.copy(title = clean) else st
            }
            val updatedTask = task.copy(
                subTasks = updatedSubs,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTask(TaskEntity.fromDomain(updatedTask))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSubTask(task: TaskItem, subTaskId: String): Result<Unit> {
        return try {
            val updatedSubs = task.subTasks.filterNot { it.id == subTaskId }
            val updatedTask = task.copy(
                subTasks = updatedSubs,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTask(TaskEntity.fromDomain(updatedTask))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setAllSubTasksCompleted(task: TaskItem, completed: Boolean): Result<Unit> {
        return try {
            val updatedSubs = task.subTasks.map { it.copy(isCompleted = completed) }
            val updatedTask = task.copy(
                subTasks = updatedSubs,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTask(TaskEntity.fromDomain(updatedTask))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            dao.deleteTaskById(taskId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCompletedTasks(): Result<Unit> {
        return try {
            dao.deleteCompletedTasks()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importTasks(tasks: List<TaskItem>, mode: ImportMode): Result<Int> {
        return try {
            if (mode == ImportMode.REPLACE) {
                dao.deleteAllTasks()
            }
            val entities = tasks.mapNotNull { task ->
                val cleanTitle = task.title.trim().take(TaskConstraints.MAX_TITLE_LENGTH)
                if (cleanTitle.isEmpty()) return@mapNotNull null
                TaskEntity.fromDomain(
                    task.copy(
                        id = task.id.ifBlank { UUID.randomUUID().toString() },
                        title = cleanTitle,
                        notes = task.notes.trim().take(TaskConstraints.MAX_NOTES_LENGTH),
                        category = task.category.trim().take(TaskConstraints.MAX_CATEGORY_LENGTH),
                        subTasks = task.subTasks.take(TaskConstraints.MAX_SUBTASKS)
                    )
                )
            }
            if (entities.isNotEmpty()) {
                dao.upsertTasks(entities)
            }
            Result.success(entities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
