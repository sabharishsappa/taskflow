package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class JournalRepository(
    private val dao: JournalEntryDao,
) {
    fun observeEntries(): Flow<List<JournalEntry>> {
        return dao.observeAllEntries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun createEntry(
        heading: String,
        body: String,
        entryDateMillis: Long,
        locationName: String,
        locationLat: Double?,
        locationLng: Double?,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        bookmarked: Boolean,
    ): Result<String> {
        return try {
            val cleanHeading = heading.trim().take(JournalValidation.MAX_HEADING_LENGTH)
            require(cleanHeading.isNotEmpty()) { "Please add a heading for your entry." }

            val cleanBody = body.trim().take(JournalValidation.MAX_BODY_LENGTH)
            val cleanLocation = locationName.trim().take(JournalValidation.MAX_LOCATION_LENGTH)
            val cleanTags = tags
                .map { JournalValidation.sanitizeTag(it) }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(JournalValidation.MAX_TAGS)
            val cleanAttachments = attachments.take(JournalValidation.MAX_ATTACHMENTS)
            val safeDateMillis = entryDateMillis.coerceAtLeast(1L)
            val wordCount = JournalValidation.computeWordCount(cleanHeading, cleanBody)
            val now = System.currentTimeMillis()
            val newId = UUID.randomUUID().toString()

            val entity = JournalEntryEntity(
                id = newId,
                heading = cleanHeading,
                body = cleanBody,
                entryDateMillis = safeDateMillis,
                locationName = cleanLocation,
                locationLat = locationLat?.takeIf { it in -90.0..90.0 },
                locationLng = locationLng?.takeIf { it in -180.0..180.0 },
                tags = cleanTags,
                attachments = cleanAttachments,
                bookmarked = bookmarked,
                wordCount = wordCount,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            dao.upsertEntry(entity)
            Result.success(newId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEntry(
        entryId: String,
        heading: String,
        body: String,
        entryDateMillis: Long,
        locationName: String,
        locationLat: Double?,
        locationLng: Double?,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        bookmarked: Boolean,
    ): Result<Unit> {
        return try {
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            val cleanHeading = heading.trim().take(JournalValidation.MAX_HEADING_LENGTH)
            require(cleanHeading.isNotEmpty()) { "Please add a heading for your entry." }

            val existing = dao.getEntryById(entryId)
            val cleanBody = body.trim().take(JournalValidation.MAX_BODY_LENGTH)
            val cleanLocation = locationName.trim().take(JournalValidation.MAX_LOCATION_LENGTH)
            val cleanTags = tags
                .map { JournalValidation.sanitizeTag(it) }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .take(JournalValidation.MAX_TAGS)
            val cleanAttachments = attachments.take(JournalValidation.MAX_ATTACHMENTS)
            val safeDateMillis = entryDateMillis.coerceAtLeast(1L)
            val wordCount = JournalValidation.computeWordCount(cleanHeading, cleanBody)
            val now = System.currentTimeMillis()

            val updated = JournalEntryEntity(
                id = entryId,
                heading = cleanHeading,
                body = cleanBody,
                entryDateMillis = safeDateMillis,
                locationName = cleanLocation,
                locationLat = locationLat?.takeIf { it in -90.0..90.0 },
                locationLng = locationLng?.takeIf { it in -180.0..180.0 },
                tags = cleanTags,
                attachments = cleanAttachments,
                bookmarked = bookmarked,
                wordCount = wordCount,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            )
            dao.upsertEntry(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(entryId: String, bookmarked: Boolean): Result<Unit> {
        return try {
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            dao.updateBookmark(entryId, bookmarked, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(entryId: String): Result<Unit> {
        return try {
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            dao.deleteById(entryId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
