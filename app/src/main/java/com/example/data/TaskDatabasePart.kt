package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val dueDateMillis: Long?,
    val priority: String,
    val category: String,
    val recurrence: String,
    val subTasksSerialized: String,
    val isCompleted: Boolean,
    val completedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            notes = notes,
            dueDateMillis = dueDateMillis,
            priority = TaskPriority.fromName(priority),
            category = category,
            recurrence = TaskRecurrence.fromName(recurrence),
            subTasks = decodeSubTasks(subTasksSerialized),
            isCompleted = isCompleted,
            completedAtMillis = completedAtMillis,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromDomain(task: TaskItem): TaskEntity {
            return TaskEntity(
                id = task.id.ifBlank { UUID.randomUUID().toString() },
                title = task.title,
                notes = task.notes,
                dueDateMillis = task.dueDateMillis,
                priority = task.priority.name,
                category = task.category,
                recurrence = task.recurrence.name,
                subTasksSerialized = encodeSubTasks(task.subTasks),
                isCompleted = task.isCompleted,
                completedAtMillis = task.completedAtMillis,
                createdAtMillis = task.createdAtMillis,
                updatedAtMillis = task.updatedAtMillis
            )
        }

        fun encodeSubTasks(subTasks: List<SubTask>): String {
            if (subTasks.isEmpty()) return ""
            val arr = JSONArray()
            subTasks.forEach { st ->
                arr.put(
                    JSONObject().apply {
                        put("id", st.id)
                        put("title", st.title)
                        put("isCompleted", st.isCompleted)
                        if (st.dueDateMillis != null) put("dueDateMillis", st.dueDateMillis)
                        if (st.priority != TaskPriority.NONE) put("priority", st.priority.name)
                    }
                )
            }
            return arr.toString()
        }

        fun decodeSubTasks(raw: String): List<SubTask> {
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val title = obj.optString("title", "").trim()
                        if (title.isNotEmpty()) {
                            val due = if (obj.has("dueDateMillis") && !obj.isNull("dueDateMillis")) {
                                obj.optLong("dueDateMillis").takeIf { it > 0L }
                            } else null
                            add(
                                SubTask(
                                    id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                                    title = title,
                                    isCompleted = obj.optBoolean("isCompleted", false),
                                    dueDateMillis = due,
                                    priority = TaskPriority.fromName(obj.optString("priority", "NONE"))
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAtMillis DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAtMillis DESC")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}
