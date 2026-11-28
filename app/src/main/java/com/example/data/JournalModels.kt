package com.example.data

object JournalValidation {
    const val MAX_HEADING_LENGTH = 200
    const val MAX_BODY_LENGTH = 20000
    const val MAX_LOCATION_LENGTH = 200
    const val MAX_TAGS = 10
    const val MAX_TAG_LENGTH = 32
    const val MAX_ATTACHMENTS = 10
    const val MAX_WORDS = 50000

    fun computeWordCount(heading: String, body: String): Long {
        val combined = "$heading $body".trim()
        if (combined.isEmpty()) return 0L
        return combined.split(Regex("\\s+")).count { it.isNotBlank() }.toLong().coerceAtMost(MAX_WORDS.toLong())
    }

    fun sanitizeTag(raw: String): String {
        return raw.trim()
            .removePrefix("#")
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_TAG_LENGTH)
    }
}

enum class AttachmentKind(val firestoreValue: String) {
    IMAGE("IMAGE"),
    DOCUMENT("DOCUMENT");

    companion object {
        fun fromRaw(raw: String?): AttachmentKind {
            return entries.firstOrNull { it.firestoreValue.equals(raw, ignoreCase = true) }
                ?: DOCUMENT
        }
    }
}

data class JournalAttachment(
    val id: String = "",
    val name: String = "",
    val mimeType: String = "application/octet-stream",
    val localPath: String = "",
    val kind: String = AttachmentKind.DOCUMENT.firestoreValue,
    val sizeBytes: Long = 0L,
) {
    fun attachmentKind(): AttachmentKind = AttachmentKind.fromRaw(kind)
}

data class JournalEntry(
    val id: String = "",
    val heading: String = "",
    val body: String = "",
    val entryDateMillis: Long = System.currentTimeMillis(),
    val locationName: String = "",
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val tags: List<String> = emptyList(),
    val attachments: List<JournalAttachment> = emptyList(),
    val bookmarked: Boolean = false,
    val wordCount: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun imageAttachments(): List<JournalAttachment> =
        attachments.filter { it.attachmentKind() == AttachmentKind.IMAGE }

    fun documentAttachments(): List<JournalAttachment> =
        attachments.filter { it.attachmentKind() == AttachmentKind.DOCUMENT }
}

data class JournalStats(
    val totalEntries: Int = 0,
    val totalWords: Long = 0L,
    val daysJournaled: Int = 0,
    val currentStreakDays: Int = 0,
)

data class ReflectionPrompt(
    val title: String,
    val subtitle: String,
    val defaultHeading: String,
    val defaultTag: String,
)

val DefaultReflectionPrompts = listOf(
    ReflectionPrompt(
        title = "Highlight of the Day",
        subtitle = "What moment brought you energy or quiet joy today?",
        defaultHeading = "Today's Highlight",
        defaultTag = "Gratitude",
    ),
    ReflectionPrompt(
        title = "Log an Activity",
        subtitle = "Record a workout, walk, hobby session, or creative project.",
        defaultHeading = "Afternoon Activity",
        defaultTag = "Activity",
    ),
    ReflectionPrompt(
        title = "Capture an Event",
        subtitle = "Remember a gathering, trip, dinner, or special milestone.",
        defaultHeading = "Memorable Event",
        defaultTag = "Event",
    ),
)
