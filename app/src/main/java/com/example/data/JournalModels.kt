package com.example.data

data class JournalAttachment(
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val isImage: Boolean
)

data class EventLocation(
    val name: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null

    val isPresent: Boolean
        get() = name.isNotBlank() || hasCoordinates

    fun formattedCoordinates(): String? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng)
    }
}

data class JournalEntry(
    val id: String = "",
    val heading: String = "",
    val content: String = "",
    val eventDateMillis: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val attachments: List<JournalAttachment> = emptyList(),
    val location: EventLocation? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

object JournalConstraints {
    const val MAX_HEADING_LENGTH = 160
    const val MAX_CONTENT_LENGTH = 20_000
    const val MAX_TAGS = 8
    const val MAX_TAG_LENGTH = 30
    const val MAX_ATTACHMENTS = 8
    const val MAX_LOCATION_NAME_LENGTH = 200
}
