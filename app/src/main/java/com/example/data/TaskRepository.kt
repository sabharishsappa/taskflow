package com.example.data

import com.example.data.local.DayloomDao
import com.example.data.local.TaskItemEntity
import com.example.data.local.TaskListEntity
import com.example.model.ListIconOption
import com.example.model.TaskItem
import com.example.model.TaskList
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val dao: DayloomDao,
) {
    fun observeLists(): Flow<List<TaskList>> =
        dao.observeLists().map { entities -> entities.map { it.toModel() } }

    fun observeTasks(): Flow<List<TaskItem>> =
        dao.observeTasks().map { entities -> entities.map { it.toModel() } }

    suspend fun addList(
        name: String,
        colorHex: String,
        iconKey: String,
        sortOrder: Long,
    ): Result<String> {
        val cleanName = name.trim()
        return try {
            require(cleanName.length in 1..60) { "List name must be 1 to 60 characters." }
            require(colorHex.matches(COLOR_HEX_REGEX)) { "Color must be a valid #RRGGBB hex." }
            val validIcon = ListIconOption.fromKey(iconKey).key
            val safeOrder = sortOrder.coerceIn(0L, 10000L)
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()

            dao.insertList(
                TaskListEntity(
                    id = id,
                    name = cleanName,
                    colorHex = colorHex,
                    iconKey = validIcon,
                    sortOrder = safeOrder,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateList(
        listId: String,
        name: String,
        colorHex: String,
        iconKey: String,
    ): Result<Unit> {
        val cleanName = name.trim()
        return try {
            require(listId.isNotBlank()) { "List ID must not be blank." }
            require(cleanName.length in 1..60) { "List name must be 1 to 60 characters." }
            require(colorHex.matches(COLOR_HEX_REGEX)) { "Color must be a valid #RRGGBB hex." }
            val validIcon = ListIconOption.fromKey(iconKey).key

            dao.updateListDetails(
                listId = listId,
                name = cleanName,
                colorHex = colorHex,
                iconKey = validIcon,
                updatedAt = System.currentTimeMillis(),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteList(listId: String): Result<Unit> {
        return try {
            require(listId.isNotBlank()) { "List ID must not be blank." }
            dao.deleteListById(listId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addTask(
        listId: String,
        title: String,
        notes: String?,
        dueDateEpochDay: Long,
        reminderEpochMillis: Long?,
    ): Result<String> {
        val cleanTitle = title.trim()
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        return try {
            require(listId.isNotBlank()) { "Please select a list for this task." }
            require(cleanTitle.length in 1..200) { "Task title must be 1 to 200 characters." }
            require(cleanNotes == null || cleanNotes.length <= 1000) { "Notes cannot exceed 1000 characters." }
            require(dueDateEpochDay in -365000L..365000L) { "Invalid due date." }
            require(reminderEpochMillis == null || reminderEpochMillis >= 0L) { "Invalid reminder time." }

            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            dao.insertTask(
                TaskItemEntity(
                    id = id,
                    listId = listId,
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueDateEpochDay = dueDateEpochDay,
                    reminderEpochMillis = reminderEpochMillis,
                    completed = false,
                    completedAtEpochMillis = null,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                )
            )
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        taskId: String,
        listId: String,
        title: String,
        notes: String?,
        dueDateEpochDay: Long,
        reminderEpochMillis: Long?,
        completed: Boolean,
        completedAtEpochMillis: Long?,
    ): Result<Unit> {
        val cleanTitle = title.trim()
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        return try {
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            require(listId.isNotBlank()) { "Please select a list for this task." }
            require(cleanTitle.length in 1..200) { "Task title must be 1 to 200 characters." }
            require(cleanNotes == null || cleanNotes.length <= 1000) { "Notes cannot exceed 1000 characters." }
            require(dueDateEpochDay in -365000L..365000L) { "Invalid due date." }
            require(reminderEpochMillis == null || reminderEpochMillis >= 0L) { "Invalid reminder time." }

            dao.updateTaskDetails(
                taskId = taskId,
                listId = listId,
                title = cleanTitle,
                notes = cleanNotes,
                dueDateEpochDay = dueDateEpochDay,
                reminderEpochMillis = reminderEpochMillis,
                completed = completed,
                completedAtEpochMillis = completedAtEpochMillis,
                updatedAt = System.currentTimeMillis(),
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(
        taskId: String,
        completed: Boolean,
    ): Result<Unit> {
        return try {
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            val now = System.currentTimeMillis()
            dao.updateTaskCompleted(
                taskId = taskId,
                completed = completed,
                completedAtEpochMillis = if (completed) now else null,
                updatedAt = now,
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
            dao.deleteTaskById(taskId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val COLOR_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
