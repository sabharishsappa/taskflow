package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class ImportMode {
    MERGE,
    REPLACE
}

data class ImportSummary(
    val totalImported: Int,
    val addedCount: Int,
    val updatedCount: Int
)

class JournalRepository(
    private val dao: JournalEntryDao
) {
    fun observeEntries(): Flow<List<JournalEntry>> =
        dao.observeEntries().map { entities -> entities.map { it.toDomain() } }

    suspend fun createEntry(
        heading: String,
        content: String,
        eventDateMillis: Long,
        favorite: Boolean,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        location: EventLocation?
    ): Result<String> {
        return try {
            val cleanHeading = heading.trim()
            val cleanContent = content.trim()
            val cleanTags = sanitizeTags(tags)
            val cleanAttachments = attachments.take(JournalConstraints.MAX_ATTACHMENTS)
            val cleanLocation = sanitizeLocation(location)
            validateFields(cleanHeading, cleanContent, cleanTags, cleanAttachments)

            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val entity = JournalEntryEntity(
                id = id,
                heading = cleanHeading,
                content = cleanContent,
                eventDateMillis = eventDateMillis,
                favorite = favorite,
                tagsSerialized = cleanTags.joinToString("\n"),
                attachmentsSerialized = JournalEntryEntity.encodeAttachments(cleanAttachments),
                locationName = cleanLocation?.name.orEmpty(),
                locationLat = cleanLocation?.latitude,
                locationLng = cleanLocation?.longitude,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.insertEntry(entity)
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEntry(
        entryId: String,
        heading: String,
        content: String,
        eventDateMillis: Long,
        favorite: Boolean,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        location: EventLocation?
    ): Result<Unit> {
        return try {
            require(entryId.isNotBlank()) { "Entry ID must not be blank for update." }
            val cleanHeading = heading.trim()
            val cleanContent = content.trim()
            val cleanTags = sanitizeTags(tags)
            val cleanAttachments = attachments.take(JournalConstraints.MAX_ATTACHMENTS)
            val cleanLocation = sanitizeLocation(location)
            validateFields(cleanHeading, cleanContent, cleanTags, cleanAttachments)

            val previousPaths = dao.getEntryById(entryId)
                ?.toDomain()
                ?.attachments
                ?.map { it.localPath }
                ?.toSet()
                .orEmpty()
            val retainedPaths = cleanAttachments.map { it.localPath }.toSet()

            dao.updateEntry(
                id = entryId,
                heading = cleanHeading,
                content = cleanContent,
                eventDateMillis = eventDateMillis,
                favorite = favorite,
                tagsSerialized = cleanTags.joinToString("\n"),
                attachmentsSerialized = JournalEntryEntity.encodeAttachments(cleanAttachments),
                locationName = cleanLocation?.name.orEmpty(),
                locationLat = cleanLocation?.latitude,
                locationLng = cleanLocation?.longitude,
                updatedAtMillis = System.currentTimeMillis()
            )

            // Clean up any removed attachment files
            (previousPaths - retainedPaths).forEach { removedPath ->
                AttachmentAndLocationHelper.deletePrivateFile(removedPath)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(entryId: String, favorite: Boolean): Result<Unit> {
        return try {
            require(entryId.isNotBlank()) { "Entry ID must not be blank." }
            dao.updateFavorite(
                id = entryId,
                favorite = favorite,
                updatedAtMillis = System.currentTimeMillis()
            )
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
            val existing = dao.getEntryById(entryId)?.toDomain()
            dao.deleteEntryById(entryId)
            existing?.attachments?.forEach { attachment ->
                AttachmentAndLocationHelper.deletePrivateFile(attachment.localPath)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllEntriesSnapshot(): List<JournalEntry> {
        return dao.getAllEntriesOnce().map { it.toDomain() }
    }

    suspend fun importEntries(
        importedEntries: List<JournalEntry>,
        mode: ImportMode
    ): Result<ImportSummary> {
        return try {
            val existingList = dao.getAllEntriesOnce().map { it.toDomain() }
            val existingById = existingList.associateBy { it.id }

            val entitiesToInsert = importedEntries.mapNotNull { entry ->
                val cleanHeading = entry.heading.trim().take(JournalConstraints.MAX_HEADING_LENGTH)
                if (cleanHeading.isEmpty()) return@mapNotNull null
                val cleanContent = entry.content.trim().take(JournalConstraints.MAX_CONTENT_LENGTH)
                val cleanTags = sanitizeTags(entry.tags)
                val cleanAttachments = entry.attachments.take(JournalConstraints.MAX_ATTACHMENTS)
                val cleanLocation = sanitizeLocation(entry.location)

                JournalEntryEntity(
                    id = entry.id.ifBlank { UUID.randomUUID().toString() },
                    heading = cleanHeading,
                    content = cleanContent,
                    eventDateMillis = entry.eventDateMillis,
                    favorite = entry.favorite,
                    tagsSerialized = cleanTags.joinToString("\n"),
                    attachmentsSerialized = JournalEntryEntity.encodeAttachments(cleanAttachments),
                    locationName = cleanLocation?.name.orEmpty(),
                    locationLat = cleanLocation?.latitude,
                    locationLng = cleanLocation?.longitude,
                    createdAtMillis = entry.createdAtMillis,
                    updatedAtMillis = entry.updatedAtMillis
                )
            }

            val retainedPaths = when (mode) {
                ImportMode.REPLACE -> entitiesToInsert
                    .flatMap { JournalEntryEntity.decodeAttachments(it.attachmentsSerialized) }
                    .map { it.localPath }
                    .toSet()

                ImportMode.MERGE -> {
                    val importedIds = entitiesToInsert.map { it.id }.toSet()
                    val unchangedExistingPaths = existingList
                        .filter { it.id !in importedIds }
                        .flatMap { it.attachments }
                        .map { it.localPath }
                    val newPaths = entitiesToInsert
                        .flatMap { JournalEntryEntity.decodeAttachments(it.attachmentsSerialized) }
                        .map { it.localPath }
                    (unchangedExistingPaths + newPaths).toSet()
                }
            }

            val previousPaths = existingList
                .flatMap { it.attachments }
                .map { it.localPath }
                .toSet()

            if (mode == ImportMode.REPLACE) {
                dao.deleteAllEntries()
            }
            if (entitiesToInsert.isNotEmpty()) {
                dao.insertEntries(entitiesToInsert)
            }

            // Clean up obsolete private attachment files
            (previousPaths - retainedPaths).forEach { obsoletePath ->
                AttachmentAndLocationHelper.deletePrivateFile(obsoletePath)
            }

            val updatedExistingCount = entitiesToInsert.count { existingById.containsKey(it.id) }
            val addedCount = entitiesToInsert.size - updatedExistingCount
            Result.success(
                ImportSummary(
                    totalImported = entitiesToInsert.size,
                    addedCount = addedCount,
                    updatedCount = updatedExistingCount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeTags(tags: List<String>): List<String> {
        return tags
            .map { it.trim().lowercase().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(JournalConstraints.MAX_TAGS)
    }

    private fun sanitizeLocation(location: EventLocation?): EventLocation? {
        if (location == null) return null
        val trimmedName = location.name.trim().take(JournalConstraints.MAX_LOCATION_NAME_LENGTH)
        val cleaned = EventLocation(
            name = trimmedName,
            latitude = location.latitude,
            longitude = location.longitude
        )
        return cleaned.takeIf { it.isPresent }
    }

    private fun validateFields(
        heading: String,
        content: String,
        tags: List<String>,
        attachments: List<JournalAttachment>
    ) {
        require(heading.isNotEmpty() && heading.length <= JournalConstraints.MAX_HEADING_LENGTH) {
            "Heading must be between 1 and ${JournalConstraints.MAX_HEADING_LENGTH} characters."
        }
        require(content.length <= JournalConstraints.MAX_CONTENT_LENGTH) {
            "Details must be ${JournalConstraints.MAX_CONTENT_LENGTH} characters or fewer."
        }
        require(tags.size <= JournalConstraints.MAX_TAGS) {
            "No more than ${JournalConstraints.MAX_TAGS} tags are allowed."
        }
        require(tags.all { it.isNotEmpty() && it.length <= JournalConstraints.MAX_TAG_LENGTH }) {
            "Each tag must be between 1 and ${JournalConstraints.MAX_TAG_LENGTH} characters."
        }
        require(attachments.size <= JournalConstraints.MAX_ATTACHMENTS) {
            "No more than ${JournalConstraints.MAX_ATTACHMENTS} attachments are allowed."
        }
    }
}
