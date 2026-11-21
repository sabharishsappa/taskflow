package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.TaskItem
import com.example.model.TaskList
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String,
    val sortOrder: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toModel(): TaskList = TaskList(
        id = id,
        name = name,
        colorHex = colorHex,
        iconKey = iconKey,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

@Entity(
    tableName = "task_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["listId"]),
        Index(value = ["dueDateEpochDay"]),
    ],
)
data class TaskItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val notes: String?,
    val dueDateEpochDay: Long,
    val reminderEpochMillis: Long?,
    val completed: Boolean,
    val completedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toModel(): TaskItem = TaskItem(
        id = id,
        listId = listId,
        title = title,
        notes = notes,
        dueDateEpochDay = dueDateEpochDay,
        reminderEpochMillis = reminderEpochMillis,
        completed = completed,
        completedAtEpochMillis = completedAtEpochMillis,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

@Dao
interface DayloomDao {
    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC, createdAtEpochMillis ASC")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_items ORDER BY dueDateEpochDay ASC, createdAtEpochMillis ASC")
    fun observeTasks(): Flow<List<TaskItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: TaskListEntity)

    @Query(
        """
        UPDATE task_lists
        SET name = :name, colorHex = :colorHex, iconKey = :iconKey, updatedAtEpochMillis = :updatedAt
        WHERE id = :listId
        """
    )
    suspend fun updateListDetails(
        listId: String,
        name: String,
        colorHex: String,
        iconKey: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM task_lists WHERE id = :listId")
    suspend fun deleteListById(listId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItemEntity)

    @Query(
        """
        UPDATE task_items
        SET listId = :listId,
            title = :title,
            notes = :notes,
            dueDateEpochDay = :dueDateEpochDay,
            reminderEpochMillis = :reminderEpochMillis,
            completed = :completed,
            completedAtEpochMillis = :completedAtEpochMillis,
            updatedAtEpochMillis = :updatedAt
        WHERE id = :taskId
        """
    )
    suspend fun updateTaskDetails(
        taskId: String,
        listId: String,
        title: String,
        notes: String?,
        dueDateEpochDay: Long,
        reminderEpochMillis: Long?,
        completed: Boolean,
        completedAtEpochMillis: Long?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE task_items
        SET completed = :completed,
            completedAtEpochMillis = :completedAtEpochMillis,
            updatedAtEpochMillis = :updatedAt
        WHERE id = :taskId
        """
    )
    suspend fun updateTaskCompleted(
        taskId: String,
        completed: Boolean,
        completedAtEpochMillis: Long?,
        updatedAt: Long,
    )

    @Query("DELETE FROM task_items WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)
}

@Database(
    entities = [TaskListEntity::class, TaskItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DayloomDatabase : RoomDatabase() {
    abstract fun dayloomDao(): DayloomDao

    companion object {
        @Volatile
        private var instance: DayloomDatabase? = null

        fun getInstance(context: Context): DayloomDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DayloomDatabase::class.java,
                    "dayloom_local.db",
                ).build().also { instance = it }
            }
        }
    }
}
