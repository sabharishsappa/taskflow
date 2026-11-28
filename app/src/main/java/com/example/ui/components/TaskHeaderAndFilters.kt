package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DateGroupStyle
import com.example.data.TaskDateGroup
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrismAmber
import com.example.ui.theme.PrismAmberContainer
import com.example.ui.theme.PrismAmberContainerDark
import com.example.ui.theme.PrismAmberDark
import com.example.ui.theme.PrismCoral
import com.example.ui.theme.PrismCoralContainer
import com.example.ui.theme.PrismCoralContainerDark
import com.example.ui.theme.PrismCoralDark
import com.example.ui.theme.PrismSky
import com.example.ui.theme.PrismSkyContainer
import com.example.ui.theme.PrismSkyContainerDark
import com.example.ui.theme.PrismSkyDark
import com.example.ui.theme.PrismTeal
import com.example.ui.theme.PrismTealContainer
import com.example.ui.theme.PrismTealContainerDark
import com.example.ui.theme.PrismTealDark
import com.example.ui.theme.PrismViolet
import com.example.ui.theme.PrismVioletContainer
import com.example.ui.theme.PrismVioletContainerDark
import com.example.ui.theme.PrismVioletDark

@Composable
fun DateGroupHeader(
    group: TaskDateGroup,
    isFirst: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val accent = when (group.style) {
        DateGroupStyle.OVERDUE -> if (isDark) PrismCoralDark else PrismCoral
        DateGroupStyle.TODAY -> if (isDark) PrismTealDark else PrismTeal
        DateGroupStyle.TOMORROW -> if (isDark) PrismSkyDark else PrismSky
        DateGroupStyle.UPCOMING -> if (isDark) PrismVioletDark else PrismViolet
        DateGroupStyle.NO_DATE -> if (isDark) PrismAmberDark else PrismAmber
    }
    val softContainer = when (group.style) {
        DateGroupStyle.OVERDUE -> if (isDark) PrismCoralContainerDark else PrismCoralContainer
        DateGroupStyle.TODAY -> if (isDark) PrismTealContainerDark else PrismTealContainer
        DateGroupStyle.TOMORROW -> if (isDark) PrismSkyContainerDark else PrismSkyContainer
        DateGroupStyle.UPCOMING -> if (isDark) PrismVioletContainerDark else PrismVioletContainer
        DateGroupStyle.NO_DATE -> if (isDark) PrismAmberContainerDark else PrismAmberContainer
    }

    val totalCount = group.tasks.size
    val doneCount = group.tasks.count { it.completed }
    val remainingCount = totalCount - doneCount
    val allDone = totalCount > 0 && remainingCount == 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (isFirst) 4.dp else 14.dp,
                bottom = 2.dp
            )
            .testTag("date_group_header_${group.key}")
    ) {
        // Calendar Day Stamp badge
        val isFilledStamp = group.style == DateGroupStyle.TODAY || group.style == DateGroupStyle.OVERDUE
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isFilledStamp) {
                        Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.82f)))
                    } else {
                        Brush.linearGradient(listOf(softContainer, softContainer.copy(alpha = 0.72f)))
                    }
                )
                .border(
                    width = if (isFilledStamp) 0.dp else 1.dp,
                    color = accent.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (group.dayOfMonth != null && group.dayOfWeekShort != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = group.dayOfWeekShort,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            letterSpacing = 0.6.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = if (isFilledStamp) {
                            Color.White.copy(alpha = 0.88f)
                        } else {
                            accent.copy(alpha = 0.85f)
                        }
                    )
                    Text(
                        text = group.dayOfMonth,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            lineHeight = 17.sp
                        ),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFilledStamp) Color.White else accent
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.EventNote,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Date title + secondary subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (group.style == DateGroupStyle.OVERDUE) {
                    accent
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
            if (!group.subtitle.isNullOrBlank()) {
                Text(
                    text = group.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (group.style == DateGroupStyle.OVERDUE) {
                        accent.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // Crisp status/count pill
        val badgeColor = if (allDone) {
            if (isDark) PrismTealDark else PrismTeal
        } else {
            accent
        }
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = badgeColor.copy(alpha = if (isDark) 0.18f else 0.12f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                if (allDone) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "All done",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                } else {
                    val label = if (doneCount > 0) {
                        "$remainingCount left"
                    } else if (totalCount == 1) {
                        "1 task"
                    } else {
                        "$totalCount tasks"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}
