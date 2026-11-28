package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class LocalBackupSnapshot(
    val fileName: String,
    val filePath: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val includesMedia: Boolean,
)

data class BackupResultSummary(
    val entryCount: Int,
    val attachmentCount: Int,
    val sizeBytes: Long,
    val fileName: String,
)

data class RestoreResultSummary(
    val restoredEntries: Int,
    val restoredAttachments: Int,
)

class JournalBackupManager(
    private val context: Context,
    private val dao: JournalEntryDao,
    private val preferences: BackupPreferencesRepository,
) {
    private val appContext = context.applicationContext

    private fun backupsDir(): File = File(appContext.filesDir, BACKUPS_DIR).apply { mkdirs() }
    private fun attachmentsDir(): File = File(appContext.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }

    suspend fun listLocalBackups(): List<LocalBackupSnapshot> = withContext(Dispatchers.IO) {
        backupsDir()
            .listFiles { file -> file.isFile && (file.name.endsWith(".zip") || file.name.endsWith(".json")) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                LocalBackupSnapshot(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    createdAtMillis = file.lastModified(),
                    sizeBytes = file.length(),
                    includesMedia = file.name.endsWith(".zip"),
                )
            }
            ?: emptyList()
    }

    suspend fun createLocalBackup(includeAttachments: Boolean): Result<BackupResultSummary> =
        withContext(Dispatchers.IO) {
            try {
                val entries = dao.getAllEntriesOnce()
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val extension = if (includeAttachments) "zip" else "json"
                val fileName = "folio_backup_${stamp}.$extension"
                val targetFile = File(backupsDir(), fileName)

                val attachmentCount = targetFile.outputStream().use { out ->
                    if (includeAttachments) {
                        writeZipBackup(entries, out)
                    } else {
                        writeJsonBackup(entries, out)
                        0
                    }
                }

                pruneOldBackups()
                val now = System.currentTimeMillis()
                preferences.recordBackupCompleted(now, entries.size)

                Result.success(
                    BackupResultSummary(
                        entryCount = entries.size,
                        attachmentCount = attachmentCount,
                        sizeBytes = targetFile.length(),
                        fileName = fileName,
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun exportBackupToUri(uri: Uri, includeAttachments: Boolean): Result<BackupResultSummary> =
        withContext(Dispatchers.IO) {
            try {
                val entries = dao.getAllEntriesOnce()
                var attachmentCount = 0
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    attachmentCount = if (includeAttachments) {
                        writeZipBackup(entries, out)
                    } else {
                        writeJsonBackup(entries, out)
                        0
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Could not open destination file."))

                preferences.recordBackupCompleted(System.currentTimeMillis(), entries.size)

                Result.success(
                    BackupResultSummary(
                        entryCount = entries.size,
                        attachmentCount = attachmentCount,
                        sizeBytes = 0L,
                        fileName = uri.lastPathSegment ?: "folio_backup.zip",
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun restoreFromLocalBackup(filePath: String): Result<RestoreResultSummary> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(IllegalStateException("Backup file no longer exists."))
                }
                val isZip = file.name.endsWith(".zip", ignoreCase = true)
                file.inputStream().use { input ->
                    if (isZip) restoreZipStream(input) else restoreJsonStream(input)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun restoreFromUri(uri: Uri): Result<RestoreResultSummary> =
        withContext(Dispatchers.IO) {
            try {
                val mime = appContext.contentResolver.getType(uri).orEmpty()
                val pathHint = uri.lastPathSegment.orEmpty()
                val looksJson = mime.contains("json", ignoreCase = true) ||
                    pathHint.endsWith(".json", ignoreCase = true)

                if (looksJson) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        restoreJsonStream(input)
                    } ?: Result.failure(IllegalStateException("Unable to read selected backup file."))
                } else {
                    // Try ZIP first, fall back to JSON if not a ZIP archive
                    val zipResult = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        restoreZipStream(input)
                    }
                    if (zipResult != null && zipResult.isSuccess) {
                        zipResult
                    } else {
                        appContext.contentResolver.openInputStream(uri)?.use { input ->
                            restoreJsonStream(input)
                        } ?: (zipResult ?: Result.failure(IllegalStateException("Unable to read backup file.")))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteLocalBackup(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val backupsCanonical = backupsDir().canonicalPath
        if (file.canonicalPath.startsWith(backupsCanonical)) {
            file.delete()
        } else {
            false
        }
    }

    fun shareLocalBackup(activityContext: Context, snapshot: LocalBackupSnapshot): Boolean {
        return try {
            val file = File(snapshot.filePath)
            if (!file.exists()) return false
            val uri = FileProvider.getUriForFile(
                activityContext,
                "${activityContext.packageName}.fileprovider",
                file
            )
            val mime = if (snapshot.includesMedia) "application/zip" else "application/json"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Folio Journal Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(Intent.createChooser(intent, "Share Backup"))
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun runScheduledBackupIfDue(): Boolean {
        val settings = preferences.getSettingsOnce()
        if (!settings.isScheduledBackupDue()) return false
        val entries = withContext(Dispatchers.IO) { dao.getAllEntriesOnce() }
        if (entries.isEmpty()) return false
        return createLocalBackup(settings.includeAttachments).isSuccess
    }

    suspend fun runOnChangeBackupIfEnabled() {
        val settings = preferences.getSettingsOnce()
        if (settings.frequency == BackupFrequency.ON_CHANGE) {
            createLocalBackup(settings.includeAttachments)
        }
    }

    fun suggestedExportFileName(includeAttachments: Boolean): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val ext = if (includeAttachments) "zip" else "json"
        return "FolioJournal_Backup_$stamp.$ext"
    }

    private fun writeJsonBackup(entries: List<JournalEntryEntity>, out: OutputStream) {
        val json = buildManifestJson(entries, mediaArchiveMap = emptyMap())
        out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun writeZipBackup(entries: List<JournalEntryEntity>, out: OutputStream): Int {
        val archiveNameByAttachmentId = linkedMapOf<String, String>()
        val filesToBundle = mutableListOf<Pair<String, File>>()

        for (entry in entries) {
            for (att in entry.attachments) {
                val file = File(att.localPath)
                if (file.exists() && file.isFile) {
                    val safeName = file.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val archivePath = "attachments/${att.id.take(8)}_$safeName"
                    if (archivePath !in archiveNameByAttachmentId.values) {
                        archiveNameByAttachmentId[att.id] = archivePath
                        filesToBundle.add(archivePath to file)
                    }
                }
            }
        }

        val manifestJson = buildManifestJson(entries, archiveNameByAttachmentId)
        ZipOutputStream(BufferedOutputStream(out)).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
            zos.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for ((archivePath, sourceFile) in filesToBundle) {
                zos.putNextEntry(ZipEntry(archivePath))
                sourceFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
            zos.finish()
        }
        return filesToBundle.size
    }

    private suspend fun restoreJsonStream(input: InputStream): Result<RestoreResultSummary> {
        val raw = input.bufferedReader(Charsets.UTF_8).readText()
        val parsed = parseManifestJson(raw, extractedPaths = emptyMap())
            ?: return Result.failure(IllegalArgumentException("Invalid journal backup format."))
        dao.upsertAll(parsed)
        return Result.success(RestoreResultSummary(restoredEntries = parsed.size, restoredAttachments = 0))
    }

    private suspend fun restoreZipStream(input: InputStream): Result<RestoreResultSummary> {
        var manifestText: String? = null
        val extractedPaths = linkedMapOf<String, String>()
        val targetAttachmentsDir = attachmentsDir()
        val canonicalBase = targetAttachmentsDir.canonicalPath

        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                if (!zipEntry.isDirectory) {
                    val name = zipEntry.name
                    if (name == MANIFEST_ENTRY_NAME) {
                        manifestText = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (name.startsWith("attachments/")) {
                        val safeLeaf = File(name).name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        if (safeLeaf.isNotBlank()) {
                            val outFile = File(targetAttachmentsDir, safeLeaf)
                            if (outFile.canonicalPath.startsWith(canonicalBase)) {
                                outFile.outputStream().use { out -> zis.copyTo(out) }
                                extractedPaths[name] = outFile.absolutePath
                            }
                        }
                    }
                }
                zis.closeEntry()
                zipEntry = zis.nextEntry
            }
        }

        val manifest = manifestText
            ?: return Result.failure(IllegalArgumentException("Backup archive is missing journal data."))
        val entities = parseManifestJson(manifest, extractedPaths)
            ?: return Result.failure(IllegalArgumentException("Could not parse entries from backup archive."))

        dao.upsertAll(entities)
        return Result.success(
            RestoreResultSummary(
                restoredEntries = entities.size,
                restoredAttachments = extractedPaths.size,
            )
        )
    }

    private fun buildManifestJson(
        entries: List<JournalEntryEntity>,
        mediaArchiveMap: Map<String, String>,
    ): JSONObject {
        val entriesArray = JSONArray()
        for (entry in entries) {
            val tagsArray = JSONArray()
            entry.tags.forEach { tagsArray.put(it) }

            val attArray = JSONArray()
            entry.attachments.forEach { att ->
                attArray.put(
                    JSONObject().apply {
                        put("id", att.id)
                        put("name", att.name)
                        put("mimeType", att.mimeType)
                        put("localPath", att.localPath)
                        put("kind", att.kind)
                        put("sizeBytes", att.sizeBytes)
                        mediaArchiveMap[att.id]?.let { put("archivePath", it) }
                    }
                )
            }

            entriesArray.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("heading", entry.heading)
                    put("body", entry.body)
                    put("entryDateMillis", entry.entryDateMillis)
                    put("locationName", entry.locationName)
                    if (entry.locationLat != null) put("locationLat", entry.locationLat)
                    if (entry.locationLng != null) put("locationLng", entry.locationLng)
                    put("tags", tagsArray)
                    put("attachments", attArray)
                    put("bookmarked", entry.bookmarked)
                    put("wordCount", entry.wordCount)
                    put("createdAtMillis", entry.createdAtMillis)
                    put("updatedAtMillis", entry.updatedAtMillis)
                }
            )
        }

        return JSONObject().apply {
            put("app", "Folio Journal")
            put("schemaVersion", 1)
            put("exportedAtMillis", System.currentTimeMillis())
            put("entries", entriesArray)
        }
    }

    private fun parseManifestJson(
        raw: String,
        extractedPaths: Map<String, String>,
    ): List<JournalEntryEntity>? {
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("entries") ?: return null
            val now = System.currentTimeMillis()
            val results = mutableListOf<JournalEntryEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val heading = obj.optString("heading", "").trim().take(JournalValidation.MAX_HEADING_LENGTH)
                if (heading.isEmpty()) continue
                val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                val body = obj.optString("body", "").take(JournalValidation.MAX_BODY_LENGTH)
                val entryDate = obj.optLong("entryDateMillis", now).coerceAtLeast(1L)
                val locationName = obj.optString("locationName", "").take(JournalValidation.MAX_LOCATION_LENGTH)
                val lat = if (obj.has("locationLat") && !obj.isNull("locationLat")) obj.optDouble("locationLat") else null
                val lng = if (obj.has("locationLng") && !obj.isNull("locationLng")) obj.optDouble("locationLng") else null

                val tagsArr = obj.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        val clean = JournalValidation.sanitizeTag(tagsArr.optString(t, ""))
                        if (clean.isNotEmpty() && tags.size < JournalValidation.MAX_TAGS) {
                            tags.add(clean)
                        }
                    }
                }

                val attArr = obj.optJSONArray("attachments")
                val attachments = mutableListOf<JournalAttachment>()
                if (attArr != null) {
                    for (a in 0 until attArr.length()) {
                        val aObj = attArr.optJSONObject(a) ?: continue
                        if (attachments.size >= JournalValidation.MAX_ATTACHMENTS) break
                        val archivePath = aObj.optString("archivePath", "")
                        val resolvedPath = extractedPaths[archivePath]
                            ?: aObj.optString("localPath", "")
                        attachments.add(
                            JournalAttachment(
                                id = aObj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                                name = aObj.optString("name", "attachment").take(120),
                                mimeType = aObj.optString("mimeType", "application/octet-stream").take(80),
                                localPath = resolvedPath.take(500),
                                kind = AttachmentKind.fromRaw(aObj.optString("kind", "DOCUMENT")).firestoreValue,
                                sizeBytes = aObj.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                            )
                        )
                    }
                }

                results.add(
                    JournalEntryEntity(
                        id = id,
                        heading = heading,
                        body = body,
                        entryDateMillis = entryDate,
                        locationName = locationName,
                        locationLat = lat?.takeIf { !it.isNaN() && it in -90.0..90.0 },
                        locationLng = lng?.takeIf { !it.isNaN() && it in -180.0..180.0 },
                        tags = tags,
                        attachments = attachments,
                        bookmarked = obj.optBoolean("bookmarked", false),
                        wordCount = obj.optLong("wordCount", JournalValidation.computeWordCount(heading, body)),
                        createdAtMillis = obj.optLong("createdAtMillis", now),
                        updatedAtMillis = obj.optLong("updatedAtMillis", now),
                    )
                )
            }
            results
        } catch (_: Exception) {
            null
        }
    }

    private fun pruneOldBackups() {
        val files = backupsDir()
            .listFiles { file -> file.isFile && (file.name.endsWith(".zip") || file.name.endsWith(".json")) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_LOCAL_SNAPSHOTS).forEach { it.delete() }
    }

    private companion object {
        const val BACKUPS_DIR = "journal_backups"
        const val ATTACHMENTS_DIR = "journal_attachments"
        const val MANIFEST_ENTRY_NAME = "journal_backup.json"
        const val MAX_LOCAL_SNAPSHOTS = 6
    }
}
