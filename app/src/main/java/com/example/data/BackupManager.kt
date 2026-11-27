package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

private const val BACKUP_TAG = "DaybookBackup"
private const val BACKUP_MANIFEST_NAME = "backup.json"
private const val BACKUP_ATTACHMENTS_PREFIX = "attachments/"
private const val BACKUP_APP_NAME = "Daybook"
private const val BACKUP_SCHEMA_VERSION = 2

private const val MAX_MANIFEST_BYTES = 20 * 1024 * 1024 // 20 MB
private const val MAX_SINGLE_ATTACHMENT_BYTES = 50L * 1024 * 1024 // 50 MB
private const val MAX_TOTAL_EXTRACTED_BYTES = 250L * 1024 * 1024 // 250 MB
private const val MAX_IMPORT_ITEMS = 10_000

data class BackupExportResult(
    val entryCount: Int,
    val taskCount: Int,
    val habitCount: Int,
    val attachmentCount: Int
)

data class BackupPreview(
    val uri: Uri,
    val entryCount: Int,
    val taskCount: Int,
    val habitCount: Int,
    val attachmentCount: Int,
    val exportedAtMillis: Long?
) {
    val totalItemCount: Int
        get() = entryCount + taskCount + habitCount
}

data class ParsedBackupImport(
    val entries: List<JournalEntry>,
    val tasks: List<TaskItem>,
    val habits: List<HabitItem>,
    val habitCompletions: List<HabitCompletion>,
    val restoredAttachmentCount: Int,
    val extractedFilePaths: List<String>
)

private data class ManifestAttachmentRef(
    val archivePath: String,
    val displayName: String,
    val mimeType: String,
    val isImage: Boolean
)

private data class ManifestEntryRecord(
    val id: String,
    val heading: String,
    val content: String,
    val eventDateMillis: Long,
    val favorite: Boolean,
    val tags: List<String>,
    val location: EventLocation?,
    val attachments: List<ManifestAttachmentRef>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

private data class ParsedManifest(
    val exportedAtMillis: Long?,
    val records: List<ManifestEntryRecord>,
    val tasks: List<TaskItem>,
    val habits: List<HabitItem>,
    val habitCompletions: List<HabitCompletion>
)

object BackupManager {

    fun suggestedBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return "daybook_backup_$stamp.zip"
    }

    fun suggestedJsonExportFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        return "daybook_export_$stamp.json"
    }

    fun suggestedDailyBackupFileName(dateMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dateMillis))
        return "daybook_daily_$stamp.zip"
    }

    suspend fun exportBackupToUri(
        context: Context,
        uri: Uri,
        entries: List<JournalEntry>,
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        habitCompletions: List<HabitCompletion>
    ): Result<BackupExportResult> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val rawOut = try {
                resolver.openOutputStream(uri, "wt")
            } catch (_: Exception) {
                null
            } ?: resolver.openOutputStream(uri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Couldn't open destination file for writing.")
                )

            rawOut.use { outStream ->
                writeZipBackup(
                    outStream = outStream,
                    entries = entries,
                    tasks = tasks,
                    habits = habits,
                    habitCompletions = habitCompletions
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(BACKUP_TAG, "Backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun exportJsonToUri(
        context: Context,
        uri: Uri,
        entries: List<JournalEntry>,
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        habitCompletions: List<HabitCompletion>
    ): Result<BackupExportResult> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val rawOut = try {
                resolver.openOutputStream(uri, "wt")
            } catch (_: Exception) {
                null
            } ?: resolver.openOutputStream(uri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Couldn't open destination file for writing.")
                )

            val entriesArray = JSONArray().apply {
                entries.forEach { entry ->
                    put(buildEntryJson(entry, JSONArray()))
                }
            }
            val rootJson = buildRootManifest(
                entriesArray = entriesArray,
                tasks = tasks,
                habits = habits,
                habitCompletions = habitCompletions
            )
            rawOut.use { outStream ->
                outStream.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                outStream.flush()
            }
            Result.success(
                BackupExportResult(
                    entryCount = entries.size,
                    taskCount = tasks.size,
                    habitCount = habits.size,
                    attachmentCount = 0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(BACKUP_TAG, "JSON export failed", e)
            Result.failure(e)
        }
    }

    suspend fun exportBackupToFile(
        targetFile: File,
        entries: List<JournalEntry>,
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        habitCompletions: List<HabitCompletion>
    ): Result<BackupExportResult> = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            targetFile.parentFile?.mkdirs()
            val result = tempFile.outputStream().use { outStream ->
                writeZipBackup(
                    outStream = outStream,
                    entries = entries,
                    tasks = tasks,
                    habits = habits,
                    habitCompletions = habitCompletions
                )
            }
            if (result.isSuccess) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } else {
                tempFile.delete()
            }
            result
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            Log.w(BACKUP_TAG, "Local file backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun exportBackupToTreeUri(
        context: Context,
        treeUri: Uri,
        fileName: String,
        retentionCount: Int,
        entries: List<JournalEntry>,
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        habitCompletions: List<HabitCompletion>
    ): Result<BackupExportResult> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

            val existingDailyDocs = mutableListOf<Pair<String, Uri>>()
            var existingSameNameUri: Uri? = null

            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (idIdx < 0 || nameIdx < 0) continue
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    if (name == fileName) {
                        existingSameNameUri = docUri
                    }
                    if (name.startsWith("daybook_daily_") && name.endsWith(".zip")) {
                        existingDailyDocs.add(name to docUri)
                    }
                }
            }

            val targetUri = existingSameNameUri ?: DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                "application/zip",
                fileName
            ) ?: return@withContext Result.failure(
                IllegalStateException("Couldn't create backup file in selected folder.")
            )

            val exportResult = exportBackupToUri(
                context = context,
                uri = targetUri,
                entries = entries,
                tasks = tasks,
                habits = habits,
                habitCompletions = habitCompletions
            )

            if (exportResult.isSuccess && retentionCount > 0) {
                val allNames = (existingDailyDocs.map { it.first } + fileName).distinct().sortedDescending()
                val toDeleteNames = allNames.drop(retentionCount).toSet()
                existingDailyDocs
                    .filter { it.first in toDeleteNames && it.second != targetUri }
                    .forEach { (_, docUri) ->
                        try {
                            DocumentsContract.deleteDocument(resolver, docUri)
                        } catch (_: Exception) {
                            // Best-effort pruning in external folder
                        }
                    }
            }

            exportResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(BACKUP_TAG, "Tree URI backup export failed", e)
            Result.failure(e)
        }
    }

    private fun writeZipBackup(
        outStream: OutputStream,
        entries: List<JournalEntry>,
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        habitCompletions: List<HabitCompletion>
    ): Result<BackupExportResult> {
        var exportedAttachments = 0
        val entriesArray = JSONArray()
        val usedArchivePaths = mutableSetOf<String>()

        ZipOutputStream(outStream.buffered()).use { zipOut ->
                    entries.forEachIndexed { entryIdx, entry ->
                        val attachmentsJson = JSONArray()

                        entry.attachments.take(JournalConstraints.MAX_ATTACHMENTS)
                            .forEachIndexed { attIdx, att ->
                                val sourceFile = File(att.localPath)
                                if (sourceFile.exists() && sourceFile.isFile) {
                                    val safeExt = extractSafeExtension(att.displayName, sourceFile.name)
                                    var candidate = "${BACKUP_ATTACHMENTS_PREFIX}e${entryIdx}_a${attIdx}$safeExt"
                                    while (!usedArchivePaths.add(candidate)) {
                                        candidate = "${BACKUP_ATTACHMENTS_PREFIX}${UUID.randomUUID()}$safeExt"
                                    }

                                    zipOut.putNextEntry(ZipEntry(candidate))
                                    sourceFile.inputStream().buffered().use { input ->
                                        input.copyTo(zipOut)
                                    }
                                    zipOut.closeEntry()
                                    exportedAttachments++

                                    attachmentsJson.put(
                                        JSONObject().apply {
                                            put("archivePath", candidate)
                                            put("displayName", att.displayName)
                                            put("mimeType", att.mimeType)
                                            put("isImage", att.isImage)
                                        }
                                    )
                                }
                            }

                        entriesArray.put(buildEntryJson(entry, attachmentsJson))
                    }

                    val rootJson = buildRootManifest(
                        entriesArray = entriesArray,
                        tasks = tasks,
                        habits = habits,
                        habitCompletions = habitCompletions
                    )

                    val manifestBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
                    zipOut.putNextEntry(ZipEntry(BACKUP_MANIFEST_NAME))
                    zipOut.write(manifestBytes)
                    zipOut.closeEntry()
                }

        return Result.success(
            BackupExportResult(
                entryCount = entries.size,
                taskCount = tasks.size,
                habitCount = habits.size,
                attachmentCount = exportedAttachments
            )
        )
    }

    private fun buildEntryJson(entry: JournalEntry, attachmentsJson: JSONArray): JSONObject {
        val tagsJson = JSONArray().apply { entry.tags.forEach { put(it) } }
        val locationJson = entry.location?.takeIf { it.isPresent }?.let { loc ->
            JSONObject().apply {
                put("name", loc.name)
                if (loc.latitude != null) put("latitude", loc.latitude)
                if (loc.longitude != null) put("longitude", loc.longitude)
            }
        }
        return JSONObject().apply {
            put("id", entry.id)
            put("heading", entry.heading)
            put("content", entry.content)
            put("eventDateMillis", entry.eventDateMillis)
            put("favorite", entry.favorite)
            put("tags", tagsJson)
            put("attachments", attachmentsJson)
            if (locationJson != null) put("location", locationJson)
            put("createdAtMillis", entry.createdAtMillis)
            put("updatedAtMillis", entry.updatedAtMillis)
        }
    }

    private fun buildRootManifest(
        entriesArray: JSONArray,
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        habitCompletions: List<HabitCompletion>
    ): JSONObject {
        val tasksArray = JSONArray().apply {
            tasks.forEach { task ->
                val subTasksJson = JSONArray().apply {
                    task.subTasks.forEach { st ->
                        put(
                            JSONObject().apply {
                                put("id", st.id)
                                put("title", st.title)
                                put("isCompleted", st.isCompleted)
                                if (st.dueDateMillis != null) put("dueDateMillis", st.dueDateMillis)
                                if (st.priority != TaskPriority.NONE) put("priority", st.priority.name)
                            }
                        )
                    }
                }
                put(
                    JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("notes", task.notes)
                        if (task.dueDateMillis != null) put("dueDateMillis", task.dueDateMillis)
                        put("priority", task.priority.name)
                        put("category", task.category)
                        put("recurrence", task.recurrence.name)
                        put("subTasks", subTasksJson)
                        put("isCompleted", task.isCompleted)
                        if (task.completedAtMillis != null) put("completedAtMillis", task.completedAtMillis)
                        put("createdAtMillis", task.createdAtMillis)
                        put("updatedAtMillis", task.updatedAtMillis)
                    }
                )
            }
        }

        val habitsArray = JSONArray().apply {
            habits.forEach { habit ->
                val daysArr = JSONArray().apply {
                    habit.scheduledDays.sorted().forEach { put(it) }
                }
                put(
                    JSONObject().apply {
                        put("id", habit.id)
                        put("name", habit.name)
                        put("description", habit.description)
                        put("category", habit.category)
                        put("accent", habit.accent.key)
                        put("frequency", habit.frequency.name)
                        put("scheduledDays", daysArr)
                        put("weeklyTarget", habit.weeklyTarget)
                        put("dailyGoalCount", habit.dailyGoalCount)
                        put("unitLabel", habit.unitLabel)
                        put("isArchived", habit.isArchived)
                        put("createdAtMillis", habit.createdAtMillis)
                        put("updatedAtMillis", habit.updatedAtMillis)
                    }
                )
            }
        }

        val completionsArray = JSONArray().apply {
            habitCompletions.forEach { comp ->
                put(
                    JSONObject().apply {
                        put("habitId", comp.habitId)
                        put("dayKeyMillis", comp.dayKeyMillis)
                        put("count", comp.count)
                    }
                )
            }
        }

        return JSONObject().apply {
            put("app", BACKUP_APP_NAME)
            put("schemaVersion", BACKUP_SCHEMA_VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("entries", entriesArray)
            put("tasks", tasksArray)
            put("habits", habitsArray)
            put("habitCompletions", completionsArray)
        }
    }

    suspend fun inspectBackupUri(
        context: Context,
        uri: Uri
    ): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val manifestText = readManifestTextFromUri(context, uri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("This file does not contain a valid Daybook backup.")
                )
            val parsed = parseManifest(manifestText)
            val attachmentCount = parsed.records.sumOf { it.attachments.size }
            Result.success(
                BackupPreview(
                    uri = uri,
                    entryCount = parsed.records.size,
                    taskCount = parsed.tasks.size,
                    habitCount = parsed.habits.size,
                    attachmentCount = attachmentCount,
                    exportedAtMillis = parsed.exportedAtMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(BACKUP_TAG, "Backup inspection failed", e)
            Result.failure(e)
        }
    }

    suspend fun importBackupFromUri(
        context: Context,
        uri: Uri
    ): Result<ParsedBackupImport> = withContext(Dispatchers.IO) {
        val newlyExtractedFiles = mutableListOf<String>()
        try {
            val manifestText = readManifestTextFromUri(context, uri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("This file does not contain a valid Daybook backup.")
                )
            val parsed = parseManifest(manifestText)

            val expectedArchivePaths = parsed.records
                .flatMap { it.attachments }
                .map { normalizeZipPath(it.archivePath) }
                .filter { it.isNotBlank() }
                .toSet()

            val extractedMap = mutableMapOf<String, String>()
            if (expectedArchivePaths.isNotEmpty() && isZipUri(context, uri)) {
                val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
                val canonicalDir = attachmentsDir.canonicalPath
                var totalExtractedBytes = 0L

                openUriInputStream(context, uri)?.use { rawIn ->
                    ZipInputStream(rawIn.buffered()).use { zipIn ->
                        var zipEntry = zipIn.nextEntry
                        while (zipEntry != null) {
                            if (!zipEntry.isDirectory) {
                                val normalized = normalizeZipPath(zipEntry.name)
                                if (normalized in expectedArchivePaths && !extractedMap.containsKey(normalized)) {
                                    val safeExt = extractSafeExtension(normalized, "")
                                    val destFile = File(attachmentsDir, "${UUID.randomUUID()}$safeExt")
                                    if (!destFile.canonicalPath.startsWith(canonicalDir + File.separator)) {
                                        throw SecurityException("Invalid attachment path detected in backup.")
                                    }

                                    var writtenForFile = 0L
                                    val buffer = ByteArray(8192)
                                    destFile.outputStream().buffered().use { out ->
                                        while (true) {
                                            val read = zipIn.read(buffer)
                                            if (read <= 0) break
                                            writtenForFile += read
                                            totalExtractedBytes += read
                                            if (writtenForFile > MAX_SINGLE_ATTACHMENT_BYTES ||
                                                totalExtractedBytes > MAX_TOTAL_EXTRACTED_BYTES
                                            ) {
                                                throw IllegalArgumentException("Backup archive exceeds safe size limit.")
                                            }
                                            out.write(buffer, 0, read)
                                        }
                                    }

                                    newlyExtractedFiles.add(destFile.absolutePath)
                                    extractedMap[normalized] = destFile.absolutePath
                                }
                            }
                            zipIn.closeEntry()
                            zipEntry = zipIn.nextEntry
                        }
                    }
                }
            }

            val domainEntries = parsed.records.map { rec ->
                val restoredAttachments = rec.attachments.mapNotNull { attRef ->
                    val localPath = extractedMap[normalizeZipPath(attRef.archivePath)]
                        ?: return@mapNotNull null
                    JournalAttachment(
                        localPath = localPath,
                        displayName = attRef.displayName.ifBlank { "Attachment" },
                        mimeType = attRef.mimeType.ifBlank { "application/octet-stream" },
                        isImage = attRef.isImage || attRef.mimeType.startsWith("image/")
                    )
                }.take(JournalConstraints.MAX_ATTACHMENTS)

                JournalEntry(
                    id = rec.id.ifBlank { UUID.randomUUID().toString() },
                    heading = rec.heading,
                    content = rec.content,
                    eventDateMillis = rec.eventDateMillis,
                    favorite = rec.favorite,
                    tags = rec.tags,
                    attachments = restoredAttachments,
                    location = rec.location,
                    createdAtMillis = rec.createdAtMillis,
                    updatedAtMillis = rec.updatedAtMillis
                )
            }

            Result.success(
                ParsedBackupImport(
                    entries = domainEntries,
                    tasks = parsed.tasks,
                    habits = parsed.habits,
                    habitCompletions = parsed.habitCompletions,
                    restoredAttachmentCount = extractedMap.size,
                    extractedFilePaths = newlyExtractedFiles.toList()
                )
            )
        } catch (e: CancellationException) {
            cleanupFiles(newlyExtractedFiles)
            throw e
        } catch (e: Exception) {
            cleanupFiles(newlyExtractedFiles)
            Log.w(BACKUP_TAG, "Backup import failed", e)
            Result.failure(e)
        }
    }

    private fun openUriInputStream(context: Context, uri: Uri): InputStream? {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val f = File(path)
            return if (f.exists() && f.isFile) f.inputStream() else null
        }
        return context.contentResolver.openInputStream(uri)
    }

    private fun readManifestTextFromUri(context: Context, uri: Uri): String? {
        if (isZipUri(context, uri)) {
            openUriInputStream(context, uri)?.use { rawIn ->
                ZipInputStream(rawIn.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val normalized = normalizeZipPath(entry.name)
                        if (!entry.isDirectory && normalized == BACKUP_MANIFEST_NAME) {
                            return readBoundedUtf8(zipIn, MAX_MANIFEST_BYTES)
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            return null
        }

        openUriInputStream(context, uri)?.use { rawIn ->
            val text = readBoundedUtf8(rawIn, MAX_MANIFEST_BYTES).trim()
            if (text.startsWith("{")) return text
        }
        return null
    }

    private fun isZipUri(context: Context, uri: Uri): Boolean {
        return try {
            openUriInputStream(context, uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun readBoundedUtf8(input: java.io.InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Backup manifest is too large.")
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun parseManifest(manifestText: String): ParsedManifest {
        val root = JSONObject(manifestText)
        val hasAnySection = root.has("entries") || root.has("tasks") || root.has("habits")
        if (!hasAnySection) {
            throw IllegalArgumentException("Backup file is missing Daybook data.")
        }
        val schemaVersion = root.optInt("schemaVersion", 1)
        if (schemaVersion < 1) {
            throw IllegalArgumentException("Unsupported backup version.")
        }

        val exportedAt = if (root.has("exportedAtMillis") && !root.isNull("exportedAtMillis")) {
            root.optLong("exportedAtMillis").takeIf { it > 0L }
        } else {
            null
        }

        val now = System.currentTimeMillis()

        // Parse Journal entries
        val records = mutableListOf<ManifestEntryRecord>()
        val entriesJson = root.optJSONArray("entries")
        if (entriesJson != null) {
            val count = minOf(entriesJson.length(), MAX_IMPORT_ITEMS)
            for (i in 0 until count) {
                val obj = entriesJson.optJSONObject(i) ?: continue
                val heading = obj.optString("heading", "").trim()
                    .take(JournalConstraints.MAX_HEADING_LENGTH)
                if (heading.isEmpty()) continue

                val id = obj.optString("id", "").trim().ifBlank { UUID.randomUUID().toString() }
                val content = obj.optString("content", "").trim()
                    .take(JournalConstraints.MAX_CONTENT_LENGTH)
                val eventDateMillis = obj.optLong("eventDateMillis", now).takeIf { it > 0L } ?: now
                val createdAtMillis = obj.optLong("createdAtMillis", eventDateMillis)
                    .takeIf { it > 0L } ?: eventDateMillis
                val updatedAtMillis = obj.optLong("updatedAtMillis", createdAtMillis)
                    .takeIf { it > 0L } ?: createdAtMillis
                val favorite = obj.optBoolean("favorite", false)

                val tags = buildList {
                    val arr = obj.optJSONArray("tags")
                    if (arr != null) {
                        for (t in 0 until arr.length()) {
                            val cleaned = arr.optString(t, "").trim().lowercase().removePrefix("#")
                                .take(JournalConstraints.MAX_TAG_LENGTH)
                            if (cleaned.isNotEmpty() && cleaned !in this) add(cleaned)
                            if (size >= JournalConstraints.MAX_TAGS) break
                        }
                    }
                }

                val location = obj.optJSONObject("location")?.let { locObj ->
                    val name = locObj.optString("name", "").trim()
                        .take(JournalConstraints.MAX_LOCATION_NAME_LENGTH)
                    val lat = if (locObj.has("latitude") && !locObj.isNull("latitude")) {
                        locObj.optDouble("latitude").takeIf { !it.isNaN() && !it.isInfinite() }
                    } else null
                    val lng = if (locObj.has("longitude") && !locObj.isNull("longitude")) {
                        locObj.optDouble("longitude").takeIf { !it.isNaN() && !it.isInfinite() }
                    } else null
                    EventLocation(name = name, latitude = lat, longitude = lng).takeIf { it.isPresent }
                }

                val attachments = buildList {
                    val arr = obj.optJSONArray("attachments")
                    if (arr != null) {
                        for (a in 0 until arr.length()) {
                            val attObj = arr.optJSONObject(a) ?: continue
                            val archivePath = normalizeZipPath(attObj.optString("archivePath", ""))
                            if (archivePath.isNotBlank()) {
                                add(
                                    ManifestAttachmentRef(
                                        archivePath = archivePath,
                                        displayName = attObj.optString("displayName", "Attachment"),
                                        mimeType = attObj.optString("mimeType", "application/octet-stream"),
                                        isImage = attObj.optBoolean("isImage", false)
                                    )
                                )
                            }
                            if (size >= JournalConstraints.MAX_ATTACHMENTS) break
                        }
                    }
                }

                records.add(
                    ManifestEntryRecord(
                        id = id,
                        heading = heading,
                        content = content,
                        eventDateMillis = eventDateMillis,
                        favorite = favorite,
                        tags = tags,
                        location = location,
                        attachments = attachments,
                        createdAtMillis = createdAtMillis,
                        updatedAtMillis = updatedAtMillis
                    )
                )
            }
        }

        // Parse Tasks
        val tasks = mutableListOf<TaskItem>()
        val tasksJson = root.optJSONArray("tasks")
        if (tasksJson != null) {
            val count = minOf(tasksJson.length(), MAX_IMPORT_ITEMS)
            for (i in 0 until count) {
                val obj = tasksJson.optJSONObject(i) ?: continue
                val title = obj.optString("title", "").trim().take(TaskConstraints.MAX_TITLE_LENGTH)
                if (title.isEmpty()) continue
                val subTasks = buildList {
                    val arr = obj.optJSONArray("subTasks")
                    if (arr != null) {
                        for (s in 0 until arr.length()) {
                            val stObj = arr.optJSONObject(s) ?: continue
                            val stTitle = stObj.optString("title", "").trim()
                                .take(TaskConstraints.MAX_SUBTASK_LENGTH)
                            if (stTitle.isNotEmpty()) {
                                val stDue = if (stObj.has("dueDateMillis") && !stObj.isNull("dueDateMillis")) {
                                    stObj.optLong("dueDateMillis").takeIf { it > 0L }
                                } else null
                                add(
                                    SubTask(
                                        id = stObj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                                        title = stTitle,
                                        isCompleted = stObj.optBoolean("isCompleted", false),
                                        dueDateMillis = stDue,
                                        priority = TaskPriority.fromName(stObj.optString("priority", "NONE"))
                                    )
                                )
                            }
                            if (size >= TaskConstraints.MAX_SUBTASKS) break
                        }
                    }
                }
                val due = if (obj.has("dueDateMillis") && !obj.isNull("dueDateMillis")) {
                    obj.optLong("dueDateMillis").takeIf { it > 0L }
                } else null
                val completedAt = if (obj.has("completedAtMillis") && !obj.isNull("completedAtMillis")) {
                    obj.optLong("completedAtMillis").takeIf { it > 0L }
                } else null

                tasks.add(
                    TaskItem(
                        id = obj.optString("id", "").trim().ifBlank { UUID.randomUUID().toString() },
                        title = title,
                        notes = obj.optString("notes", "").trim().take(TaskConstraints.MAX_NOTES_LENGTH),
                        dueDateMillis = due,
                        priority = TaskPriority.fromName(obj.optString("priority", "NONE")),
                        category = obj.optString("category", "").trim().take(TaskConstraints.MAX_CATEGORY_LENGTH),
                        recurrence = TaskRecurrence.fromName(obj.optString("recurrence", "NONE")),
                        subTasks = subTasks,
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedAtMillis = completedAt,
                        createdAtMillis = obj.optLong("createdAtMillis", now).takeIf { it > 0L } ?: now,
                        updatedAtMillis = obj.optLong("updatedAtMillis", now).takeIf { it > 0L } ?: now
                    )
                )
            }
        }

        // Parse Habits & Completions
        val habits = mutableListOf<HabitItem>()
        val habitsJson = root.optJSONArray("habits")
        if (habitsJson != null) {
            val count = minOf(habitsJson.length(), MAX_IMPORT_ITEMS)
            for (i in 0 until count) {
                val obj = habitsJson.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(HabitConstraints.MAX_NAME_LENGTH)
                if (name.isEmpty()) continue
                val days = buildSet {
                    val arr = obj.optJSONArray("scheduledDays")
                    if (arr != null) {
                        for (d in 0 until arr.length()) {
                            val dayVal = arr.optInt(d, -1)
                            if (dayVal in 1..7) add(dayVal)
                        }
                    }
                }.ifEmpty { HabitItem.ALL_WEEKDAYS }

                habits.add(
                    HabitItem(
                        id = obj.optString("id", "").trim().ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        description = obj.optString("description", "").trim()
                            .take(HabitConstraints.MAX_DESCRIPTION_LENGTH),
                        category = obj.optString("category", "Health").trim()
                            .take(HabitConstraints.MAX_CATEGORY_LENGTH).ifBlank { "Health" },
                        accent = HabitAccent.fromKey(obj.optString("accent", "sage")),
                        frequency = HabitFrequency.fromName(obj.optString("frequency", "DAILY")),
                        scheduledDays = days,
                        weeklyTarget = obj.optInt("weeklyTarget", 5).coerceIn(1, 7),
                        dailyGoalCount = obj.optInt("dailyGoalCount", 1)
                            .coerceIn(1, HabitConstraints.MAX_DAILY_GOAL),
                        unitLabel = obj.optString("unitLabel", "").trim()
                            .take(HabitConstraints.MAX_UNIT_LENGTH),
                        isArchived = obj.optBoolean("isArchived", false),
                        createdAtMillis = obj.optLong("createdAtMillis", now).takeIf { it > 0L } ?: now,
                        updatedAtMillis = obj.optLong("updatedAtMillis", now).takeIf { it > 0L } ?: now
                    )
                )
            }
        }

        val completions = mutableListOf<HabitCompletion>()
        val completionsJson = root.optJSONArray("habitCompletions")
        if (completionsJson != null) {
            val count = minOf(completionsJson.length(), MAX_IMPORT_ITEMS * 10)
            for (i in 0 until count) {
                val obj = completionsJson.optJSONObject(i) ?: continue
                val habitId = obj.optString("habitId", "").trim()
                val dayKey = obj.optLong("dayKeyMillis", 0L)
                val c = obj.optInt("count", 0)
                if (habitId.isNotEmpty() && dayKey > 0L && c > 0) {
                    completions.add(HabitCompletion(habitId = habitId, dayKeyMillis = dayKey, count = c))
                }
            }
        }

        return ParsedManifest(
            exportedAtMillis = exportedAt,
            records = records,
            tasks = tasks,
            habits = habits,
            habitCompletions = completions
        )
    }

    private fun normalizeZipPath(raw: String): String {
        val unified = raw.replace('\\', '/').trimStart('/')
        if (unified.contains("..")) return ""
        return unified
    }

    private fun extractSafeExtension(primaryName: String, fallbackName: String): String {
        val candidate = primaryName.substringAfterLast('.', "")
            .ifBlank { fallbackName.substringAfterLast('.', "") }
            .filter { it.isLetterOrDigit() }
            .lowercase(Locale.US)
            .take(10)
        return if (candidate.isNotBlank()) ".$candidate" else ""
    }

    private fun cleanupFiles(paths: List<String>) {
        paths.forEach { path ->
            try {
                File(path).delete()
            } catch (_: Exception) {
                // Best-effort cleanup
            }
        }
    }
}
