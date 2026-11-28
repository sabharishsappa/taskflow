package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AttachmentStorageHelper
import com.example.data.JournalAttachment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object JournalDateFormatter {
    fun formatCardDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val dayMonth = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
        return when {
            isSameDay(cal, today) -> "Today · $dayMonth"
            isSameDay(cal, yesterday) -> "Yesterday · $dayMonth"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(millis))
            else ->
                SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(millis))
        }
    }

    fun formatFullDate(millis: Long): String {
        return SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(millis))
    }

    fun formatCompactDate(millis: Long): String {
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))
    }

    fun formatDateGroupHeader(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val shortDay = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
        return when {
            isSameDay(cal, today) -> "Today · $shortDay"
            isSameDay(cal, yesterday) -> "Yesterday · $shortDay"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> shortDay
            else -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(millis))
        }
    }

    fun formatShortDateRange(startMillis: Long, endMillis: Long?): String {
        val todayYear = Calendar.getInstance().get(Calendar.YEAR)
        val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
        val endCal = endMillis?.let { Calendar.getInstance().apply { timeInMillis = it } }
        val fmtSameYear = SimpleDateFormat("MMM d", Locale.getDefault())
        val fmtWithYear = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        fun formatSingle(cal: Calendar): String {
            return if (cal.get(Calendar.YEAR) == todayYear) {
                fmtSameYear.format(cal.time)
            } else {
                fmtWithYear.format(cal.time)
            }
        }

        if (endCal == null || isSameDay(startCal, endCal)) {
            return formatSingle(startCal)
        }
        return "${formatSingle(startCal)} – ${formatSingle(endCal)}"
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}

@Composable
fun LocationPill(
    locationName: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = if (onClear != null) 4.dp else 10.dp,
                top = 5.dp,
                bottom = 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = locationName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClear != null) {
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_remove_attachment),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TagPill(
    tag: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 10.dp,
                end = if (onRemove != null) 4.dp else 10.dp,
                top = 5.dp,
                bottom = 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRemove != null) {
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_remove_attachment),
                        tint = contentColor,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoCollageGrid(
    images: List<JournalAttachment>,
    onImageClick: (JournalAttachment) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    cornerRadius: Dp = 18.dp,
) {
    if (images.isEmpty()) return
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape),
    ) {
        when (images.size) {
            1 -> {
                AttachmentPhotoTile(
                    attachment = images[0],
                    onClick = { onImageClick(images[0]) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    AttachmentPhotoTile(
                        attachment = images[0],
                        onClick = { onImageClick(images[0]) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    AttachmentPhotoTile(
                        attachment = images[1],
                        onClick = { onImageClick(images[1]) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            else -> {
                val extraCount = images.size - 3
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    AttachmentPhotoTile(
                        attachment = images[0],
                        onClick = { onImageClick(images[0]) },
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        AttachmentPhotoTile(
                            attachment = images[1],
                            onClick = { onImageClick(images[1]) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            AttachmentPhotoTile(
                                attachment = images[2],
                                onClick = { onImageClick(images[2]) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (extraCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.48f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "+$extraCount",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPhotoTile(
    attachment: JournalAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileExists = remember(attachment.localPath) {
        attachment.localPath.isNotBlank() && File(attachment.localPath).exists()
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (fileExists) {
            AsyncImage(
                model = File(attachment.localPath),
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun DocumentAttachmentItem(
    attachment: JournalAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val icon = when {
        attachment.mimeType.contains("pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
        attachment.mimeType.startsWith("audio/", ignoreCase = true) -> Icons.Outlined.AudioFile
        attachment.mimeType.contains("text", ignoreCase = true) ||
            attachment.mimeType.contains("word", ignoreCase = true) -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
    val sizeLabel = remember(attachment.sizeBytes) {
        AttachmentStorageHelper.formatFileSize(attachment.sizeBytes)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sizeLabel.isNotBlank()) {
                    Text(
                        text = sizeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_remove_attachment),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenPhotoViewer(
    attachment: JournalAttachment,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
        ) {
            val file = remember(attachment.localPath) { File(attachment.localPath) }
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 20.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_viewer),
                        tint = Color.White,
                    )
                }
            }
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 40.dp),
            )
        }
    }
}
