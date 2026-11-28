package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipInputStream

enum class ImportFileFormat(val label: String) {
    FOLIO_ZIP("Folio Archive (.zip)"),
    JSON("JSON (.json)"),
    CSV("CSV (.csv)"),
    MARKDOWN("Markdown (.md)"),
    PLAIN_TEXT("Plain Text (.txt)"),
}

data class ImportEntryPreview(
    val heading: String,
    val entryDateMillis: Long,
    val tagCount: Int,
)

data class ImportInspectionResult(
    val uri: Uri,
    val fileName: String,
    val format: ImportFileFormat,
    val entryCount: Int,
    val attachmentCount: Int,
    val previews: List<ImportEntryPreview>,
    internal val parsedEntities: List<JournalEntryEntity>?,
)

data class ImportExecutionResult(
    val importedEntries: Int,
    val importedAttachments: Int,
    val fileName: String,
)

class JournalImportManager(
    private val context: Context,
    private val dao: JournalEntryDao,
    private val backupManager: JournalBackupManager,
) {
    private val appContext = context.applicationContext

    suspend fun inspectUri(uri: Uri): Result<ImportInspectionResult> = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveDisplayName(uri)
            val mime = appContext.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
            val lowerName = fileName.lowercase(Locale.US)

            val isZip = lowerName.endsWith(".zip") || mime.contains("zip")
            if (isZip) {
                return@withContext inspectZipBackup(uri, fileName)
            }

            val rawText = appContext.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }?.removePrefix("\uFEFF")
                ?: return@withContext Result.failure(IllegalStateException("Couldn't read selected file."))

            if (rawText.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("The selected file is empty."))
            }

            val trimmed = rawText.trimStart()
            val format: ImportFileFormat
            val entities: List<JournalEntryEntity>

            when {
                lowerName.endsWith(".json") || mime.contains("json") ||
                    trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                    format = ImportFileFormat.JSON
                    entities = parseFlexibleJson(rawText)
                }
                lowerName.endsWith(".csv") || mime.contains("csv") -> {
                    format = ImportFileFormat.CSV
                    entities = parseCsv(rawText)
                }
                lowerName.endsWith(".md") || lowerName.endsWith(".markdown") || mime.contains("markdown") -> {
                    format = ImportFileFormat.MARKDOWN
                    entities = parseMarkdownOrText(rawText, fileName, isMarkdown = true)
                }
                else -> {
                    format = ImportFileFormat.PLAIN_TEXT
                    entities = parseMarkdownOrText(rawText, fileName, isMarkdown = false)
                }
            }

            if (entities.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("No journal entries found in $fileName.")
                )
            }

            Result.success(
                ImportInspectionResult(
                    uri = uri,
                    fileName = fileName,
                    format = format,
                    entryCount = entities.size,
                    attachmentCount = 0,
                    previews = entities.take(4).map {
                        ImportEntryPreview(
                            heading = it.heading,
                            entryDateMillis = it.entryDateMillis,
                            tagCount = it.tags.size,
                        )
                    },
                    parsedEntities = entities,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeImport(inspection: ImportInspectionResult): Result<ImportExecutionResult> =
        withContext(Dispatchers.IO) {
            try {
                if (inspection.format == ImportFileFormat.FOLIO_ZIP) {
                    backupManager.restoreFromUri(inspection.uri).map { summary ->
                        ImportExecutionResult(
                            importedEntries = summary.restoredEntries,
                            importedAttachments = summary.restoredAttachments,
                            fileName = inspection.fileName,
                        )
                    }
                } else {
                    val entities = inspection.parsedEntities.orEmpty()
                    if (entities.isEmpty()) {
                        return@withContext Result.failure(IllegalStateException("Nothing to import."))
                    }
                    dao.upsertAll(entities)
                    Result.success(
                        ImportExecutionResult(
                            importedEntries = entities.size,
                            importedAttachments = 0,
                            fileName = inspection.fileName,
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun inspectZipBackup(uri: Uri, fileName: String): Result<ImportInspectionResult> {
        var manifestText: String? = null
        var attachmentFiles = 0
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "journal_backup.json" -> {
                                manifestText = zis.readBytes().toString(Charsets.UTF_8)
                            }
                            entry.name.startsWith("attachments/") -> {
                                attachmentFiles++
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } ?: return Result.failure(IllegalStateException("Couldn't open ZIP archive."))

        val manifest = manifestText
            ?: return Result.failure(IllegalArgumentException("ZIP archive doesn't contain a Folio journal backup."))
        val entities = parseFlexibleJson(manifest)
        if (entities.isEmpty()) {
            return Result.failure(IllegalArgumentException("No journal entries found inside ZIP archive."))
        }
        return Result.success(
            ImportInspectionResult(
                uri = uri,
                fileName = fileName,
                format = ImportFileFormat.FOLIO_ZIP,
                entryCount = entities.size,
                attachmentCount = attachmentFiles,
                previews = entities.take(4).map {
                    ImportEntryPreview(
                        heading = it.heading,
                        entryDateMillis = it.entryDateMillis,
                        tagCount = it.tags.size,
                    )
                },
                parsedEntities = null,
            )
        )
    }

    private fun parseFlexibleJson(raw: String): List<JournalEntryEntity> {
        val trimmed = raw.trim()
        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("entries")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("notes")
                    ?: JSONArray().apply { put(root) }
            }
            else -> return emptyList()
        }

        val now = System.currentTimeMillis()
        val results = mutableListOf<JournalEntryEntity>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawHeading = firstNonBlank(
                obj.optString("heading", ""),
                obj.optString("title", ""),
                obj.optString("subject", ""),
            )
            val rawBody = firstNonBlank(
                obj.optString("body", ""),
                obj.optString("text", ""),
                obj.optString("content", ""),
                obj.optString("notes", ""),
                obj.optString("description", ""),
            )

            val heading: String
            val body: String
            if (rawHeading.isNotBlank()) {
                heading = rawHeading.trim().take(JournalValidation.MAX_HEADING_LENGTH)
                body = rawBody.trim().take(JournalValidation.MAX_BODY_LENGTH)
            } else if (rawBody.isNotBlank()) {
                val lines = rawBody.trim().lines()
                heading = lines.first().removePrefix("#").trim().take(JournalValidation.MAX_HEADING_LENGTH)
                body = lines.drop(1).joinToString("\n").trim().take(JournalValidation.MAX_BODY_LENGTH)
            } else {
                continue
            }

            if (heading.isEmpty()) continue

            val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
            val entryDateMillis = resolveJsonDate(obj, now)
            val locationName = resolveJsonLocation(obj).take(JournalValidation.MAX_LOCATION_LENGTH)
            val lat = resolveOptionalDouble(obj, "locationLat")
                ?: obj.optJSONObject("location")?.let { resolveOptionalDouble(it, "latitude") }
            val lng = resolveOptionalDouble(obj, "locationLng")
                ?: obj.optJSONObject("location")?.let { resolveOptionalDouble(it, "longitude") }

            val tags = parseJsonTags(obj)
            val bookmarked = obj.optBoolean("bookmarked", obj.optBoolean("starred", false))
            val createdAt = obj.optLong("createdAtMillis", entryDateMillis).coerceAtLeast(1L)
            val updatedAt = obj.optLong("updatedAtMillis", now).coerceAtLeast(1L)

            results.add(
                JournalEntryEntity(
                    id = id,
                    heading = heading,
                    body = body,
                    entryDateMillis = entryDateMillis,
                    locationName = locationName,
                    locationLat = lat?.takeIf { !it.isNaN() && it in -90.0..90.0 },
                    locationLng = lng?.takeIf { !it.isNaN() && it in -180.0..180.0 },
                    tags = tags,
                    attachments = emptyList(),
                    bookmarked = bookmarked,
                    wordCount = JournalValidation.computeWordCount(heading, body),
                    createdAtMillis = createdAt,
                    updatedAtMillis = updatedAt,
                )
            )
        }
        return results
    }

    private fun parseCsv(raw: String): List<JournalEntryEntity> {
        val rows = parseCsvRows(raw).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()

        val firstRowLower = rows.first().map { it.trim().lowercase(Locale.US) }
        val hasHeader = firstRowLower.any { col ->
            col in setOf("title", "heading", "date", "body", "content", "notes", "description", "tags", "location")
        }

        val dataRows = if (hasHeader) rows.drop(1) else rows
        val colTitle = if (hasHeader) findColumnIndex(firstRowLower, "heading", "title", "subject", "event", "name") else 0
        val colBody = if (hasHeader) findColumnIndex(firstRowLower, "body", "content", "details", "detailed matter", "notes", "description", "text") else 1
        val colDate = if (hasHeader) findColumnIndex(firstRowLower, "date", "entry_date", "entrydate", "created", "timestamp") else 2
        val colLocation = if (hasHeader) findColumnIndex(firstRowLower, "location", "place", "venue") else 3
        val colTags = if (hasHeader) findColumnIndex(firstRowLower, "tags", "tag", "labels") else 4
        val colBookmarked = if (hasHeader) findColumnIndex(firstRowLower, "bookmarked", "starred", "favorite") else -1

        val now = System.currentTimeMillis()
        val results = mutableListOf<JournalEntryEntity>()
        for (row in dataRows) {
            val rawHeading = row.getOrNull(colTitle).orEmpty().trim()
            val rawBody = row.getOrNull(colBody).orEmpty().trim()
            if (rawHeading.isEmpty() && rawBody.isEmpty()) continue

            val heading = (rawHeading.ifEmpty { rawBody.lines().first() })
                .take(JournalValidation.MAX_HEADING_LENGTH)
            val body = (if (rawHeading.isNotEmpty()) rawBody else rawBody.lines().drop(1).joinToString("\n"))
                .take(JournalValidation.MAX_BODY_LENGTH)
            val dateMillis = parseFlexibleDate(row.getOrNull(colDate).orEmpty()) ?: now
            val location = row.getOrNull(colLocation).orEmpty().trim().take(JournalValidation.MAX_LOCATION_LENGTH)
            val tags = row.getOrNull(colTags)
                .orEmpty()
                .split(';', '|', ',')
                .map { JournalValidation.sanitizeTag(it) }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.US) }
                .take(JournalValidation.MAX_TAGS)
            val bookmarked = row.getOrNull(colBookmarked)?.trim()?.lowercase(Locale.US) in setOf("true", "1", "yes")

            results.add(
                JournalEntryEntity(
                    id = UUID.randomUUID().toString(),
                    heading = heading,
                    body = body,
                    entryDateMillis = dateMillis,
                    locationName = location,
                    locationLat = null,
                    locationLng = null,
                    tags = tags,
                    attachments = emptyList(),
                    bookmarked = bookmarked,
                    wordCount = JournalValidation.computeWordCount(heading, body),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
        }
        return results
    }

    private fun parseMarkdownOrText(
        raw: String,
        fileName: String,
        isMarkdown: Boolean,
    ): List<JournalEntryEntity> {
        val now = System.currentTimeMillis()
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val sections = if (isMarkdown && normalized.contains("\n---\n")) {
            normalized.split(Regex("\n-{3,}\n"))
        } else {
            listOf(normalized)
        }

        val fallbackTitle = File(fileName).nameWithoutExtension
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Imported Note" }

        val results = mutableListOf<JournalEntryEntity>()
        for (section in sections) {
            val lines = section.trim().lines()
            if (lines.all { it.isBlank() }) continue

            var heading = ""
            var parsedDate: Long? = null
            var location = ""
            val tags = mutableListOf<String>()
            val bodyLines = mutableListOf<String>()

            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (heading.isEmpty() && trimmed.startsWith("#")) {
                    heading = trimmed.trimStart('#').trim()
                    continue
                }
                if (bodyLines.isEmpty() && trimmed.startsWith("Date:", ignoreCase = true)) {
                    parsedDate = parseFlexibleDate(trimmed.substringAfter(':').trim())
                    continue
                }
                if (bodyLines.isEmpty() && trimmed.startsWith("Location:", ignoreCase = true)) {
                    location = trimmed.substringAfter(':').trim().take(JournalValidation.MAX_LOCATION_LENGTH)
                    continue
                }
                if (bodyLines.isEmpty() && trimmed.startsWith("Tags:", ignoreCase = true)) {
                    trimmed.substringAfter(':')
                        .split(',', ';', ' ')
                        .map { JournalValidation.sanitizeTag(it) }
                        .filter { it.isNotEmpty() }
                        .forEach { tag ->
                            if (tags.size < JournalValidation.MAX_TAGS && tags.none { it.equals(tag, true) }) {
                                tags.add(tag)
                            }
                        }
                    continue
                }
                if (heading.isEmpty() && index == 0 && trimmed.length <= 100 && lines.size > 1) {
                    heading = trimmed
                } else {
                    bodyLines.add(line)
                }
            }

            val finalHeading = heading.ifBlank { fallbackTitle }.take(JournalValidation.MAX_HEADING_LENGTH)
            val finalBody = bodyLines.joinToString("\n").trim().take(JournalValidation.MAX_BODY_LENGTH)
            if (finalHeading.isBlank() && finalBody.isBlank()) continue

            results.add(
                JournalEntryEntity(
                    id = UUID.randomUUID().toString(),
                    heading = finalHeading,
                    body = finalBody,
                    entryDateMillis = parsedDate ?: now,
                    locationName = location,
                    locationLat = null,
                    locationLng = null,
                    tags = tags,
                    attachments = emptyList(),
                    bookmarked = false,
                    wordCount = JournalValidation.computeWordCount(finalHeading, finalBody),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
        }
        return results
    }

    private fun parseCsvRows(raw: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < raw.length && raw[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.clear()
                    }
                    '\r' -> {
                        if (i + 1 < raw.length && raw[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }

    private fun findColumnIndex(headers: List<String>, vararg candidates: String): Int {
        for (candidate in candidates) {
            val idx = headers.indexOfFirst { it == candidate || it.contains(candidate) }
            if (idx >= 0) return idx
        }
        return -1
    }

    private fun resolveJsonDate(obj: JSONObject, fallbackMillis: Long): Long {
        val longVal = obj.optLong("entryDateMillis", -1L)
        if (longVal > 0L) return longVal
        val strDate = firstNonBlank(
            obj.optString("date", ""),
            obj.optString("creationDate", ""),
            obj.optString("entryDate", ""),
            obj.optString("createdAt", ""),
        )
        return parseFlexibleDate(strDate) ?: fallbackMillis
    }

    private fun resolveJsonLocation(obj: JSONObject): String {
        val direct = firstNonBlank(
            obj.optString("locationName", ""),
            obj.optString("place", ""),
        )
        if (direct.isNotBlank()) return direct
        val locObj = obj.optJSONObject("location")
        if (locObj != null) {
            return firstNonBlank(
                locObj.optString("placeName", ""),
                locObj.optString("localityName", ""),
                locObj.optString("name", ""),
            )
        }
        return obj.optString("location", "")
    }

    private fun parseJsonTags(obj: JSONObject): List<String> {
        val tags = mutableListOf<String>()
        val arr = obj.optJSONArray("tags")
        if (arr != null) {
            for (t in 0 until arr.length()) {
                val clean = JournalValidation.sanitizeTag(arr.optString(t, ""))
                if (clean.isNotEmpty() && tags.size < JournalValidation.MAX_TAGS && tags.none { it.equals(clean, true) }) {
                    tags.add(clean)
                }
            }
        } else {
            val str = obj.optString("tags", "")
            if (str.isNotBlank()) {
                str.split(',', ';', '|')
                    .map { JournalValidation.sanitizeTag(it) }
                    .filter { it.isNotEmpty() }
                    .take(JournalValidation.MAX_TAGS)
                    .forEach { tags.add(it) }
            }
        }
        return tags
    }

    private fun resolveOptionalDouble(obj: JSONObject, key: String): Double? {
        return if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key) else null
    }

    private fun parseFlexibleDate(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { numeric ->
            return if (numeric > 10_000_000_000L) numeric else numeric * 1000L
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "MMM d, yyyy",
            "MMMM d, yyyy",
            "EEE, MMM d, yyyy",
        )
        for (pattern in patterns) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    if (pattern.endsWith("'Z'")) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                }
                val parsed: Date? = fmt.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    private fun resolveDisplayName(uri: Uri): String {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "journal_import"
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() }.orEmpty()
    }
}
