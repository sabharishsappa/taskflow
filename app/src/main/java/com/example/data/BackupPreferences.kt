package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "folio_backup_prefs"
)

enum class BackupFrequency(
    val label: String,
    val description: String,
    val intervalMillis: Long?,
) {
    OFF(
        label = "Off (Manual Only)",
        description = "Create backups only when you tap Back Up Now",
        intervalMillis = null,
    ),
    ON_CHANGE(
        label = "After Every Change",
        description = "Automatically save a fresh backup whenever you add or edit an entry",
        intervalMillis = 0L,
    ),
    DAILY(
        label = "Daily",
        description = "Automatically back up your journal once every 24 hours",
        intervalMillis = 24L * 60L * 60L * 1000L,
    ),
    WEEKLY(
        label = "Weekly",
        description = "Automatically back up your journal every 7 days",
        intervalMillis = 7L * 24L * 60L * 60L * 1000L,
    ),
    MONTHLY(
        label = "Monthly",
        description = "Automatically back up your journal every 30 days",
        intervalMillis = 30L * 24L * 60L * 60L * 1000L,
    );

    companion object {
        fun fromRaw(raw: String?): BackupFrequency {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WEEKLY
        }
    }
}

data class BackupSettings(
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val includeAttachments: Boolean = true,
    val lastBackupMillis: Long = 0L,
    val lastBackupEntryCount: Int = 0,
) {
    fun nextScheduledBackupMillis(): Long? {
        val interval = frequency.intervalMillis ?: return null
        if (interval <= 0L) return null
        val base = if (lastBackupMillis > 0L) lastBackupMillis else System.currentTimeMillis()
        return base + interval
    }

    fun isScheduledBackupDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val interval = frequency.intervalMillis ?: return false
        if (interval <= 0L) return false
        if (lastBackupMillis <= 0L) return true
        return (nowMillis - lastBackupMillis) >= interval
    }
}

class BackupPreferencesRepository(private val context: Context) {
    private val dataStore = context.applicationContext.backupDataStore

    val settingsFlow: Flow<BackupSettings> = dataStore.data.map { prefs ->
        BackupSettings(
            frequency = BackupFrequency.fromRaw(prefs[KEY_FREQUENCY]),
            includeAttachments = prefs[KEY_INCLUDE_ATTACHMENTS] ?: true,
            lastBackupMillis = prefs[KEY_LAST_BACKUP_MILLIS] ?: 0L,
            lastBackupEntryCount = (prefs[KEY_LAST_BACKUP_COUNT] ?: 0L).toInt(),
        )
    }

    suspend fun getSettingsOnce(): BackupSettings = settingsFlow.first()

    suspend fun setFrequency(frequency: BackupFrequency) {
        dataStore.edit { prefs ->
            prefs[KEY_FREQUENCY] = frequency.name
        }
    }

    suspend fun setIncludeAttachments(include: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_INCLUDE_ATTACHMENTS] = include
        }
    }

    suspend fun recordBackupCompleted(timestampMillis: Long, entryCount: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP_MILLIS] = timestampMillis
            prefs[KEY_LAST_BACKUP_COUNT] = entryCount.toLong()
        }
    }

    private companion object {
        val KEY_FREQUENCY = stringPreferencesKey("backup_frequency")
        val KEY_INCLUDE_ATTACHMENTS = booleanPreferencesKey("include_attachments")
        val KEY_LAST_BACKUP_MILLIS = longPreferencesKey("last_backup_millis")
        val KEY_LAST_BACKUP_COUNT = longPreferencesKey("last_backup_count")
    }
}
