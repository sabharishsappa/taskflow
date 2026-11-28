package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appLockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "folio_privacy_lock_prefs"
)

enum class AutoLockTimeout(
    val label: String,
    val timeoutMillis: Long,
) {
    IMMEDIATELY("Immediately", 0L),
    ONE_MINUTE("1 min", 60_000L),
    FIVE_MINUTES("5 min", 5L * 60_000L),
    FIFTEEN_MINUTES("15 min", 15L * 60_000L);

    companion object {
        fun fromRaw(raw: String?): AutoLockTimeout {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ONE_MINUTE
        }
    }
}

data class AppLockSettings(
    val isLockEnabled: Boolean = true,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val hideInAppSwitcher: Boolean = true,
)

class AppLockPreferencesRepository(private val context: Context) {
    private val dataStore = context.applicationContext.appLockDataStore

    val settingsFlow: Flow<AppLockSettings> = dataStore.data.map { prefs ->
        AppLockSettings(
            isLockEnabled = prefs[KEY_LOCK_ENABLED] ?: true,
            autoLockTimeout = AutoLockTimeout.fromRaw(prefs[KEY_AUTO_LOCK_TIMEOUT]),
            hideInAppSwitcher = prefs[KEY_HIDE_IN_APP_SWITCHER] ?: true,
        )
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_LOCK_TIMEOUT] = timeout.name
        }
    }

    suspend fun setHideInAppSwitcher(hide: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HIDE_IN_APP_SWITCHER] = hide
        }
    }

    private companion object {
        val KEY_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val KEY_AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val KEY_HIDE_IN_APP_SWITCHER = booleanPreferencesKey("hide_in_app_switcher")
    }
}
