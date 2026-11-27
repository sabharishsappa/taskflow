package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ui.DateFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class DailyBackupConfig(
    val enabled: Boolean = false,
    val hourOfDay: Int = 21,
    val retentionDays: Int = 7,
    val folderUriString: String? = null,
    val folderDisplayName: String? = null,
    val lastBackupMillis: Long? = null,
    val lastBackupSummary: String? = null,
    val lastBackupError: String? = null
) {
    fun formattedScheduleTime(): String {
        val h12 = when {
            hourOfDay == 0 -> 12
            hourOfDay > 12 -> hourOfDay - 12
            else -> hourOfDay
        }
        val amPm = if (hourOfDay < 12) "AM" else "PM"
        return "$h12:00 $amPm"
    }
}

data class SavedDailyBackup(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

object DailyBackupManager {
    private const val TAG = "DailyBackupManager"
    private const val PREFS_NAME = "daybook_daily_backup_prefs"
    private const val WORK_NAME = "daybook_daily_backup_work"
    private const val VAULT_DIR_NAME = "daily_backups"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour_of_day"
    private const val KEY_RETENTION = "retention_days"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_LAST_TIME = "last_backup_millis"
    private const val KEY_LAST_SUMMARY = "last_backup_summary"
    private const val KEY_LAST_ERROR = "last_backup_error"

    val SCHEDULE_HOURS = listOf(6, 9, 12, 18, 21, 23)
    val RETENTION_OPTIONS = listOf(7, 14, 30)

    private val _configFlow = MutableStateFlow(DailyBackupConfig())
    val configFlow: StateFlow<DailyBackupConfig> = _configFlow.asStateFlow()

    private val _localBackupsFlow = MutableStateFlow<List<SavedDailyBackup>>(emptyList())
    val localBackupsFlow: StateFlow<List<SavedDailyBackup>> = _localBackupsFlow.asStateFlow()

    fun refreshState(context: Context) {
        val appContext = context.applicationContext
        _configFlow.value = loadConfig(appContext)
        _localBackupsFlow.value = listLocalDailyBackups(appContext)
    }

    fun loadConfig(context: Context): DailyBackupConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastMillis = prefs.getLong(KEY_LAST_TIME, 0L).takeIf { it > 0L }
        return DailyBackupConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hourOfDay = prefs.getInt(KEY_HOUR, 21).coerceIn(0, 23),
            retentionDays = prefs.getInt(KEY_RETENTION, 7).coerceIn(1, 90),
            folderUriString = prefs.getString(KEY_FOLDER_URI, null)?.takeIf { it.isNotBlank() },
            folderDisplayName = prefs.getString(KEY_FOLDER_NAME, null)?.takeIf { it.isNotBlank() },
            lastBackupMillis = lastMillis,
            lastBackupSummary = prefs.getString(KEY_LAST_SUMMARY, null)?.takeIf { it.isNotBlank() },
            lastBackupError = prefs.getString(KEY_LAST_ERROR, null)?.takeIf { it.isNotBlank() }
        )
    }

    private fun saveConfig(context: Context, config: DailyBackupConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_HOUR, config.hourOfDay)
            .putInt(KEY_RETENTION, config.retentionDays)
            .putString(KEY_FOLDER_URI, config.folderUriString)
            .putString(KEY_FOLDER_NAME, config.folderDisplayName)
            .putLong(KEY_LAST_TIME, config.lastBackupMillis ?: 0L)
            .putString(KEY_LAST_SUMMARY, config.lastBackupSummary)
            .putString(KEY_LAST_ERROR, config.lastBackupError)
            .apply()
        _configFlow.value = config
    }

    fun setDailyBackupEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val updated = loadConfig(appContext).copy(enabled = enabled, lastBackupError = null)
        saveConfig(appContext, updated)
        scheduleOrCancelWork(appContext, updated)
    }

    fun setScheduleHour(context: Context, hourOfDay: Int) {
        val appContext = context.applicationContext
        val updated = loadConfig(appContext).copy(hourOfDay = hourOfDay.coerceIn(0, 23))
        saveConfig(appContext, updated)
        scheduleOrCancelWork(appContext, updated)
    }

    fun setRetentionDays(context: Context, retentionDays: Int) {
        val appContext = context.applicationContext
        val updated = loadConfig(appContext).copy(retentionDays = retentionDays.coerceIn(1, 90))
        saveConfig(appContext, updated)
        pruneLocalBackups(appContext, updated.retentionDays)
        _localBackupsFlow.value = listLocalDailyBackups(appContext)
    }

    fun setBackupFolder(context: Context, treeUri: Uri?) {
        val appContext = context.applicationContext
        if (treeUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist folder URI permission", e)
            }
        }
        val displayName = treeUri?.let { extractFriendlyFolderName(it) }
        val updated = loadConfig(appContext).copy(
            folderUriString = treeUri?.toString(),
            folderDisplayName = displayName,
            lastBackupError = null
        )
        saveConfig(appContext, updated)
    }

    fun scheduleOrCancelWork(context: Context, config: DailyBackupConfig = loadConfig(context)) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!config.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val initialDelayMillis = computeInitialDelayMillis(config.hourOfDay)
        val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun runCatchUpBackupIfDue(context: Context) {
        val appContext = context.applicationContext
        val config = loadConfig(appContext)
        refreshState(appContext)
        if (!config.enabled) return

        // Ensure periodic work is registered
        scheduleOrCancelWork(appContext, config)

        val now = System.currentTimeMillis()
        val todayKey = DateFormatters.dayKey(now)
        val lastKey = config.lastBackupMillis?.let { DateFormatters.dayKey(it) }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val neverBackedUp = lastKey == null
        val missedPreviousDay = lastKey != null && lastKey < todayKey &&
            (currentHour >= config.hourOfDay || (now - config.lastBackupMillis) >= TimeUnit.HOURS.toMillis(24))

        if (neverBackedUp || missedPreviousDay) {
            performDailyBackup(appContext)
        }
    }

    suspend fun performDailyBackup(context: Context): Result<BackupExportResult> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val config = loadConfig(appContext)
        try {
            val db = DaybookDatabase.getInstance(appContext)
            val entries = JournalRepository(db.journalEntryDao()).getAllEntriesSnapshot()
            val tasks = TaskRepository(db.taskDao()).getAllTasksSnapshot()
            val (habits, completions) = HabitRepository(db.habitDao()).getAllHabitsSnapshot()

            if (entries.isEmpty() && tasks.isEmpty() && habits.isEmpty()) {
                return@withContext Result.success(BackupExportResult(0, 0, 0, 0))
            }

            val now = System.currentTimeMillis()
            val fileName = BackupManager.suggestedDailyBackupFileName(now)
            val vaultDir = File(appContext.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
            val localFile = File(vaultDir, fileName)

            val localResult = BackupManager.exportBackupToFile(
                targetFile = localFile,
                entries = entries,
                tasks = tasks,
                habits = habits,
                habitCompletions = completions
            )

            if (localResult.isFailure) {
                val errMsg = localResult.exceptionOrNull()?.localizedMessage ?: "Daily backup failed."
                saveConfig(appContext, config.copy(lastBackupError = errMsg))
                return@withContext localResult
            }

            pruneLocalBackups(appContext, config.retentionDays)
            _localBackupsFlow.value = listLocalDailyBackups(appContext)

            val info = localResult.getOrThrow()
            var folderWarning: String? = null
            if (!config.folderUriString.isNullOrBlank()) {
                val treeUri = Uri.parse(config.folderUriString)
                val folderRes = BackupManager.exportBackupToTreeUri(
                    context = appContext,
                    treeUri = treeUri,
                    fileName = fileName,
                    retentionCount = config.retentionDays,
                    entries = entries,
                    tasks = tasks,
                    habits = habits,
                    habitCompletions = completions
                )
                if (folderRes.isFailure) {
                    folderWarning = "Saved to app vault, but couldn't write to chosen folder. Please re-select the folder."
                }
            }

            val parts = buildList {
                if (info.taskCount > 0) add("${info.taskCount} tasks")
                if (info.entryCount > 0) add("${info.entryCount} journal")
                if (info.habitCount > 0) add("${info.habitCount} habits")
            }
            val summary = if (parts.isEmpty()) "0 items" else parts.joinToString(" · ")
            saveConfig(
                appContext,
                config.copy(
                    lastBackupMillis = now,
                    lastBackupSummary = summary,
                    lastBackupError = folderWarning
                )
            )
            Result.success(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Daily backup error", e)
            saveConfig(appContext, config.copy(lastBackupError = e.localizedMessage ?: "Daily backup failed."))
            Result.failure(e)
        }
    }

    fun listLocalDailyBackups(context: Context): List<SavedDailyBackup> {
        val vaultDir = File(context.applicationContext.filesDir, VAULT_DIR_NAME)
        if (!vaultDir.exists() || !vaultDir.isDirectory) return emptyList()
        return vaultDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.map { f ->
                SavedDailyBackup(
                    fileName = f.name,
                    filePath = f.absolutePath,
                    sizeBytes = f.length(),
                    lastModifiedMillis = f.lastModified()
                )
            }
            .orEmpty()
    }

    fun deleteLocalDailyBackup(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            val vaultDir = File(context.applicationContext.filesDir, VAULT_DIR_NAME).canonicalPath
            if (file.canonicalPath.startsWith(vaultDir + File.separator)) {
                file.delete()
            }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
        _localBackupsFlow.value = listLocalDailyBackups(context.applicationContext)
    }

    private fun pruneLocalBackups(context: Context, keepCount: Int) {
        val vaultDir = File(context.applicationContext.filesDir, VAULT_DIR_NAME)
        val files = vaultDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?: return
        files.drop(keepCount.coerceAtLeast(1)).forEach { oldFile ->
            try {
                oldFile.delete()
            } catch (_: Exception) {
                // Best-effort cleanup
            }
        }
    }

    private fun computeInitialDelayMillis(targetHour: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    }

    private fun extractFriendlyFolderName(treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val afterColon = docId.substringAfter(':', docId)
            val leaf = afterColon.substringAfterLast('/').ifBlank { afterColon }
            leaf.ifBlank { "Selected Folder" }
        } catch (_: Exception) {
            treeUri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Selected Folder" } ?: "Selected Folder"
        }
    }
}

class DailyBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = DailyBackupManager.loadConfig(applicationContext)
        if (!config.enabled) return Result.success()

        val backupResult = DailyBackupManager.performDailyBackup(applicationContext)
        return if (backupResult.isSuccess || runAttemptCount >= 3) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
