package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.RepeatRule
import com.example.data.SubtaskItem
import com.example.data.TaskDateUtils
import com.example.data.TaskItem
import com.example.data.TaskRowSize
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrismCoral
import com.example.ui.theme.PrismCoralDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: TaskItem,
    isExpanded: Boolean,
    isSelected: Boolean,
    rowSize: TaskRowSize = TaskRowSize.NORMAL,
    use24HourTime: Boolean = false,
    onToggleComplete: () -> Unit,
    onCardClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleSubtask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val palette = task.taskColor.palette(isDark)
    val isOverdue = TaskDateUtils.isOverdue(task.dueDateMillis, task.completed)
    val checkScale by animateFloatAsState(
        targetValue = if (task.completed) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check_scale"
    )

    val cardBgColor = when {
        isSelected -> palette.softContainer.copy(alpha = if (isDark) 0.55f else 0.72f)
        task.completed -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }

    val cardBorderColor = when {
        isSelected -> palette.accent
        task.completed -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    }

    val cardCorner = when (rowSize) {
        TaskRowSize.COMPACT -> 14.dp
        TaskRowSize.NORMAL -> 16.dp
        TaskRowSize.LARGE -> 20.dp
    }
    val rowVerticalPadding = when (rowSize) {
        TaskRowSize.COMPACT -> 0.dp
        TaskRowSize.NORMAL -> 3.dp
        TaskRowSize.LARGE -> 9.dp
    }
    val contentVerticalPadding = when (rowSize) {
        TaskRowSize.COMPACT -> 4.dp
        TaskRowSize.NORMAL -> 6.dp
        TaskRowSize.LARGE -> 8.dp
    }
    val checkCircleSize = when (rowSize) {
        TaskRowSize.COMPACT -> 20.dp
        TaskRowSize.NORMAL -> 22.dp
        TaskRowSize.LARGE -> 25.dp
    }

    Surface(
        shape = RoundedCornerShape(cardCorner),
        color = cardBgColor,
        tonalElevation = if (task.completed) 0.dp else 1.dp,
        shadowElevation = if (task.completed) 0.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .clip(RoundedCornerShape(cardCorner))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(cardCorner)
            )
            .clickable(onClick = onCardClick)
            .testTag("task_card_${task.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 8.dp, end = 12.dp, top = rowVerticalPadding, bottom = rowVerticalPadding)
            ) {
                // Soft rounded inset color pill accent
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (task.completed) palette.accent.copy(alpha = 0.3f) else palette.accent,
                                    if (task.completed) palette.accent.copy(alpha = 0.18f) else palette.accent.copy(alpha = 0.62f)
                                )
                            )
                        )
                )

                // 48dp accessible circular check button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleComplete)
                        .testTag("task_check_${task.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(checkCircleSize)
                            .scale(checkScale)
                            .clip(CircleShape)
                            .background(
                                if (task.completed) palette.accent else palette.softContainer.copy(alpha = 0.45f)
                            )
                            .border(
                                width = 2.dp,
                                color = if (task.completed) palette.accent else palette.accent.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                    ) {
                        if (task.completed) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Mark task incomplete",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (task.completed) 0.58f else 1f)
                        .padding(vertical = contentVerticalPadding)
                ) {
                    // Title row with aligned timing pill and repeat indicator at end of line
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        task.reminderMillis?.let { rem ->
                            TimeSlotPill(
                                text = TaskDateUtils.formatTimeOnly(rem, use24Hour = use24HourTime),
                                accent = palette.accent,
                                container = palette.softContainer
                            )
                        }

                        if (task.repeatRuleEnum != RepeatRule.NONE) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(palette.softContainer.copy(alpha = 0.72f))
                                    .testTag("task_repeat_icon_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Repeat,
                                    contentDescription = "Repeating task",
                                    tint = palette.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (task.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val hasMeta = isOverdue || task.hasSubtasks

                    if (hasMeta) {
                        Spacer(modifier = Modifier.height(5.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isOverdue) {
                                MetadataBadge(
                                    icon = Icons.Rounded.WarningAmber,
                                    text = "Overdue",
                                    accent = if (isDark) PrismCoralDark else PrismCoral
                                )
                            }

                            if (task.hasSubtasks) {
                                SubtaskSummaryBadge(
                                    doneCount = task.completedSubtasksCount,
                                    totalCount = task.subtasks.size,
                                    accent = palette.accent,
                                    isExpanded = isExpanded,
                                    onClick = onToggleExpand,
                                    testTag = "task_subtasks_toggle_${task.id}"
                                )
                            }
                        }
                    }

                    // Slim subtask progress bar when subtasks exist and card is collapsed
                    if (task.hasSubtasks && !isExpanded) {
                        val progress by animateFloatAsState(
                            targetValue = task.completedSubtasksCount.toFloat() / task.subtasks.size.coerceAtLeast(1),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "subtask_progress"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(palette.softContainer.copy(alpha = if (isDark) 0.55f else 0.85f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(palette.accent)
                            )
                        }
                    }
                }
            }

            // Smooth expandable inline subtasks checklist
            AnimatedVisibility(
                visible = isExpanded && task.hasSubtasks,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.softContainer.copy(alpha = if (isDark) 0.22f else 0.34f))
                ) {
                    HorizontalDivider(
                        color = palette.accent.copy(alpha = 0.14f),
                        thickness = 1.dp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                    ) {
                        task.subtasks.forEach { subtask ->
                            InlineSubtaskRow(
                                subtask = subtask,
                                accent = palette.accent,
                                onToggle = { onToggleSubtask(subtask.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotPill(
    text: String,
    accent: Color,
    container: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container.copy(alpha = 0.72f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Alarm,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetadataBadge(
    icon: ImageVector,
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.13f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SubtaskSummaryBadge(
    doneCount: Int,
    totalCount: Int,
    accent: Color,
    isExpanded: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.15f),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Checklist,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$doneCount/$totalCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse subtasks" else "Expand subtasks",
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun InlineSubtaskRow(
    subtask: SubtaskItem,
    accent: Color,
    onToggle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(21.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (subtask.done) accent else Color.Transparent)
                .border(
                    width = 1.8.dp,
                    color = accent,
                    shape = RoundedCornerShape(7.dp)
                )
        ) {
            if (subtask.done) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Subtask completed",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (subtask.done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (subtask.done) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
