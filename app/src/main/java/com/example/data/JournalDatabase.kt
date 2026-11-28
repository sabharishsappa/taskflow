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
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val heading: String,
    val body: String,
    val entryDateMillis: Long,
    val locationName: String,
    val locationLat: Double?,
    val locationLng: Double?,
    val tags: List<String>,
    val attachments: List<JournalAttachment>,
    val bookmarked: Boolean,
    val wordCount: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): JournalEntry = JournalEntry(
        id = id,
        heading = heading,
        body = body,
        entryDateMillis = entryDateMillis,
        locationName = locationName,
        locationLat = locationLat,
        locationLng = locationLng,
        tags = tags,
        attachments = attachments,
        bookmarked = bookmarked,
        wordCount = wordCount,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

class JournalTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { index -> arr.optString(index, "") }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromAttachmentList(list: List<JournalAttachment>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("mimeType", item.mimeType)
                    put("localPath", item.localPath)
                    put("kind", item.kind)
                    put("sizeBytes", item.sizeBytes)
                }
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun toAttachmentList(raw: String): List<JournalAttachment> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { index ->
                val obj = arr.optJSONObject(index) ?: return@List null
                JournalAttachment(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    mimeType = obj.optString("mimeType", "application/octet-stream"),
                    localPath = obj.optString("localPath", ""),
                    kind = obj.optString("kind", AttachmentKind.DOCUMENT.firestoreValue),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                )
            }.filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY entryDateMillis DESC, createdAtMillis DESC")
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY entryDateMillis DESC, createdAtMillis DESC")
    suspend fun getAllEntriesOnce(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<JournalEntryEntity>)

    @Query("UPDATE journal_entries SET bookmarked = :bookmarked, updatedAtMillis = :updatedAtMillis WHERE id = :entryId")
    suspend fun updateBookmark(entryId: String, bookmarked: Boolean, updatedAtMillis: Long)

    @Query("DELETE FROM journal_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: String)
}

@Database(
    entities = [JournalEntryEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(JournalTypeConverters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao

    companion object {
        @Volatile
        private var INSTANCE: JournalDatabase? = null

        fun getInstance(context: Context): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JournalDatabase::class.java,
                    "folio_journal.db",
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
