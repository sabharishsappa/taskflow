package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object AttachmentStorageHelper {

    private const val ATTACHMENTS_DIR = "journal_attachments"

    suspend fun importUri(
        context: Context,
        uri: Uri,
        forcedKind: AttachmentKind? = null,
    ): Result<JournalAttachment> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(uri) ?: "application/octet-stream"
            var displayName = "attachment"
            var reportedSize = 0L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            reportedSize = cursor.getLong(sizeIdx)
                        }
                    }
                }

            val kind = forcedKind ?: if (detectedMime.startsWith("image/", ignoreCase = true)) {
                AttachmentKind.IMAGE
            } else {
                AttachmentKind.DOCUMENT
            }

            val safeExt = displayName.substringAfterLast('.', "").takeIf { it.length in 1..6 }
                ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(detectedMime)
                ?: if (kind == AttachmentKind.IMAGE) "jpg" else "bin"

            val attachmentId = UUID.randomUUID().toString()
            val dir = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
            val cleanBase = displayName
                .substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(40)
                .ifBlank { if (kind == AttachmentKind.IMAGE) "photo" else "file" }

            val targetFile = File(dir, "${attachmentId.take(8)}_${cleanBase}.$safeExt")

            resolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open selected file."))

            val finalSize = targetFile.length().takeIf { it > 0L } ?: reportedSize

            Result.success(
                JournalAttachment(
                    id = attachmentId,
                    name = displayName.take(120),
                    mimeType = detectedMime.take(80),
                    localPath = targetFile.absolutePath,
                    kind = kind.firestoreValue,
                    sizeBytes = finalSize,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openAttachment(context: Context, attachment: JournalAttachment): Boolean {
        return try {
            val file = File(attachment.localPath)
            if (!file.exists()) return false
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, attachment.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, attachment.name))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareEntry(context: Context, entry: JournalEntry) {
        val dateText = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            .format(Date(entry.entryDateMillis))
        val text = buildString {
            appendLine(entry.heading)
            appendLine(dateText)
            if (entry.locationName.isNotBlank()) {
                appendLine("Location: ${entry.locationName}")
            }
            if (entry.tags.isNotEmpty()) {
                appendLine(entry.tags.joinToString(" ") { "#$it" })
            }
            if (entry.body.isNotBlank()) {
                appendLine()
                append(entry.body)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, entry.heading)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Journal Entry"))
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
