package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prismtask_settings"
)

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromKey(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class TaskRowSize(val label: String) {
    COMPACT("Compact"),
    NORMAL("Medium"),
    LARGE("Large");

    companion object {
        fun fromKey(raw: String?): TaskRowSize =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class AppTextScale(val label: String, val multiplier: Float) {
    SMALL("Small", 0.90f),
    MEDIUM("Medium", 1.00f),
    LARGE("Large", 1.14f);

    companion object {
        fun fromKey(raw: String?): AppTextScale =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val taskRowSize: TaskRowSize = TaskRowSize.NORMAL,
    val textScale: AppTextScale = AppTextScale.MEDIUM,
    val use24HourTime: Boolean = false,
    val showCompletedTasks: Boolean = true
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AppSettings(
                themeMode = AppThemeMode.fromKey(prefs[KEY_THEME_MODE]),
                taskRowSize = TaskRowSize.fromKey(prefs[KEY_TASK_ROW_SIZE]),
                textScale = AppTextScale.fromKey(prefs[KEY_TEXT_SCALE]),
                use24HourTime = prefs[KEY_USE_24_HOUR] ?: false,
                showCompletedTasks = prefs[KEY_SHOW_COMPLETED] ?: true
            )
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setTaskRowSize(size: TaskRowSize) {
        context.settingsDataStore.edit { it[KEY_TASK_ROW_SIZE] = size.name }
    }

    suspend fun setTextScale(scale: AppTextScale) {
        context.settingsDataStore.edit { it[KEY_TEXT_SCALE] = scale.name }
    }

    suspend fun setUse24HourTime(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_USE_24_HOUR] = enabled }
    }

    suspend fun setShowCompletedTasks(show: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_COMPLETED] = show }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_TASK_ROW_SIZE = stringPreferencesKey("task_row_size")
        private val KEY_TEXT_SCALE = stringPreferencesKey("text_scale")
        private val KEY_USE_24_HOUR = booleanPreferencesKey("use_24_hour_time")
        private val KEY_SHOW_COMPLETED = booleanPreferencesKey("show_completed_tasks")
    }
}
