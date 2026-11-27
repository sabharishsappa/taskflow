package com.example.data

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
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val heading: String,
    val content: String,
    val eventDateMillis: Long,
    val favorite: Boolean,
    val tagsSerialized: String,
    val attachmentsSerialized: String,
    val locationName: String,
    val locationLat: Double?,
    val locationLng: Double?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): JournalEntry {
        val parsedTags = if (tagsSerialized.isBlank()) {
            emptyList()
        } else {
            tagsSerialized.split("\n").filter { it.isNotBlank() }
        }
        val parsedAttachments = decodeAttachments(attachmentsSerialized)
        val loc = EventLocation(
            name = locationName,
            latitude = locationLat,
            longitude = locationLng
        ).takeIf { it.isPresent }

        return JournalEntry(
            id = id,
            heading = heading,
            content = content,
            eventDateMillis = eventDateMillis,
            favorite = favorite,
            tags = parsedTags,
            attachments = parsedAttachments,
            location = loc,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun encodeAttachments(attachments: List<JournalAttachment>): String {
            if (attachments.isEmpty()) return ""
            val array = JSONArray()
            attachments.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("localPath", item.localPath)
                        put("displayName", item.displayName)
                        put("mimeType", item.mimeType)
                        put("isImage", item.isImage)
                    }
                )
            }
            return array.toString()
        }

        fun decodeAttachments(raw: String): List<JournalAttachment> {
            if (raw.isBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val path = obj.optString("localPath", "")
                        if (path.isNotBlank()) {
                            add(
                                JournalAttachment(
                                    localPath = path,
                                    displayName = obj.optString("displayName", "Attachment"),
                                    mimeType = obj.optString("mimeType", "*/*"),
                                    isImage = obj.optBoolean("isImage", false)
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
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY eventDateMillis DESC, createdAtMillis DESC")
    fun observeEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity)

    @Query(
        """
        UPDATE journal_entries
        SET heading = :heading,
            content = :content,
            eventDateMillis = :eventDateMillis,
            favorite = :favorite,
            tagsSerialized = :tagsSerialized,
            attachmentsSerialized = :attachmentsSerialized,
            locationName = :locationName,
            locationLat = :locationLat,
            locationLng = :locationLng,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun updateEntry(
        id: String,
        heading: String,
        content: String,
        eventDateMillis: Long,
        favorite: Boolean,
        tagsSerialized: String,
        attachmentsSerialized: String,
        locationName: String,
        locationLat: Double?,
        locationLng: Double?,
        updatedAtMillis: Long
    )

    @Query("UPDATE journal_entries SET favorite = :favorite, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean, updatedAtMillis: Long)

    @Query("SELECT * FROM journal_entries ORDER BY eventDateMillis DESC, createdAtMillis DESC")
    suspend fun getAllEntriesOnce(): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<JournalEntryEntity>)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAllEntries()
}

@Database(
    entities = [
        JournalEntryEntity::class,
        TaskEntity::class,
        HabitEntity::class,
        HabitCompletionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DaybookDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var INSTANCE: DaybookDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tasks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `dueDateMillis` INTEGER,
                        `priority` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `recurrence` TEXT NOT NULL,
                        `subTasksSerialized` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `completedAtMillis` INTEGER,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habits` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `accentKey` TEXT NOT NULL,
                        `frequency` TEXT NOT NULL,
                        `scheduledDaysCsv` TEXT NOT NULL,
                        `weeklyTarget` INTEGER NOT NULL,
                        `dailyGoalCount` INTEGER NOT NULL,
                        `unitLabel` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habit_completions` (
                        `habitId` TEXT NOT NULL,
                        `dayKeyMillis` INTEGER NOT NULL,
                        `count` INTEGER NOT NULL,
                        PRIMARY KEY(`habitId`, `dayKeyMillis`),
                        FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_habit_completions_habitId` ON `habit_completions` (`habitId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_habit_completions_dayKeyMillis` ON `habit_completions` (`dayKeyMillis`)"
                )
            }
        }

        fun getInstance(context: Context): DaybookDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DaybookDatabase::class.java,
                    "daybook_journal.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
