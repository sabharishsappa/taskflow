package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.data.SubtaskItem
import com.example.data.TaskItem
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val completed: Boolean,
    val colorKey: String,
    val dueDateMillis: Long?,
    val reminderMillis: Long?,
    val repeatRule: String,
    val repeatInterval: Long,
    val subtasksJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): TaskItem = TaskItem(
        id = id,
        title = title,
        notes = notes,
        completed = completed,
        colorKey = colorKey,
        dueDateMillis = dueDateMillis,
        reminderMillis = reminderMillis,
        repeatRule = repeatRule,
        repeatInterval = repeatInterval,
        subtasks = decodeSubtasks(subtasksJson),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun encodeSubtasks(subtasks: List<SubtaskItem>): String {
            val array = JSONArray()
            subtasks.forEach { sub ->
                array.put(
                    JSONObject().apply {
                        put("id", sub.id)
                        put("title", sub.title)
                        put("done", sub.done)
                    }
                )
            }
            return array.toString()
        }

        fun decodeSubtasks(raw: String): List<SubtaskItem> {
            if (raw.isBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        val title = obj.optString("title")
                        if (title.isNotBlank()) {
                            add(
                                SubtaskItem(
                                    id = id.ifBlank { "sub_$i" },
                                    title = title,
                                    done = obj.optBoolean("done", false)
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
    @Query("SELECT * FROM tasks ORDER BY completed ASC, CASE WHEN dueDateMillis IS NULL THEN 1 ELSE 0 END ASC, dueDateMillis ASC, createdAtMillis DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(entity: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(entities: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("SELECT id FROM tasks WHERE completed = 1")
    suspend fun getCompletedTaskIds(): List<String>

    @Query("DELETE FROM tasks WHERE completed = 1")
    suspend fun deleteCompletedTasks()

    @Transaction
    suspend fun replaceAllTasks(entities: List<TaskEntity>) {
        deleteAllTasks()
        if (entities.isNotEmpty()) {
            upsertTasks(entities)
        }
    }
}

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PrismTaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: PrismTaskDatabase? = null

        fun getInstance(context: Context): PrismTaskDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrismTaskDatabase::class.java,
                    "prismtask.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
