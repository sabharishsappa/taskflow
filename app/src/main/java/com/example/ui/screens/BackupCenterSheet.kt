package com.example.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DailyBackupConfig
import com.example.data.DailyBackupManager
import com.example.data.SavedDailyBackup
import com.example.ui.DateFormatters
import com.example.ui.components.OptionDropdownField

@Composable
fun BackupMenuButton(
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    dailyBackupConfig: DailyBackupConfig,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onOpenBackupCenter: () -> Unit,
    buttonTestTag: String
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { menuExpanded = true },
            enabled = !isBackupBusy,
            modifier = Modifier.testTag(buttonTestTag)
        ) {
            Box {
                Icon(
                    imageVector = Icons.Outlined.Backup,
                    contentDescription = stringResource(R.string.action_backup_menu),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                if (dailyBackupConfig.enabled) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-1).dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.action_export_zip_backup),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.export_zip_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                enabled = canExportBackup && !isBackupBusy,
                onClick = {
                    menuExpanded = false
                    onExportZipClick()
                },
                modifier = Modifier.testTag("export_zip_option")
            )

            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.action_export_json_backup),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.export_json_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.DataObject, contentDescription = null) },
                enabled = canExportBackup && !isBackupBusy,
                onClick = {
                    menuExpanded = false
                    onExportJsonClick()
                },
                modifier = Modifier.testTag("export_json_option")
            )

            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.action_import_backup),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.import_menu_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                enabled = !isBackupBusy,
                onClick = {
                    menuExpanded = false
                    onImportClick()
                },
                modifier = Modifier.testTag("import_backup_option")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.action_daily_backup_settings),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (dailyBackupConfig.enabled) {
                                stringResource(
                                    R.string.daily_backup_menu_on_format,
                                    dailyBackupConfig.formattedScheduleTime()
                                )
                            } else {
                                stringResource(R.string.daily_backup_menu_off)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dailyBackupConfig.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = if (dailyBackupConfig.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                onClick = {
                    menuExpanded = false
                    onOpenBackupCenter()
                },
                modifier = Modifier.testTag("daily_backup_option")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupCenterSheet(
    config: DailyBackupConfig,
    savedBackups: List<SavedDailyBackup>,
    isBackupBusy: Boolean,
    canExportBackup: Boolean,
    onDismiss: () -> Unit,
    onExportZipClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onImportClick: () -> Unit,
    onToggleDailyEnabled: (Boolean) -> Unit,
    onSelectScheduleHour: (Int) -> Unit,
    onSelectRetentionDays: (Int) -> Unit,
    onPickFolderClick: () -> Unit,
    onClearFolderClick: () -> Unit,
    onRunDailyBackupNow: () -> Unit,
    onRestoreSavedBackup: (SavedDailyBackup) -> Unit,
    onDeleteSavedBackup: (SavedDailyBackup) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var exportMenuExpanded by remember { mutableStateOf(false) }
    var timeMenuExpanded by remember { mutableStateOf(false) }
    var retentionMenuExpanded by remember { mutableStateOf(false) }
    var historyMenuExpanded by remember { mutableStateOf(false) }

    var selectedBackupPath by remember(savedBackups) {
        mutableStateOf(savedBackups.firstOrNull()?.filePath)
    }
    val selectedBackup = savedBackups.firstOrNull { it.filePath == selectedBackupPath }
        ?: savedBackups.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.backup_center_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.backup_center_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (isBackupBusy) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Section 1: Manual Export & Import Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_manual_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.backup_manual_section_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { exportMenuExpanded = true },
                                enabled = canExportBackup && !isBackupBusy,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("backup_center_export_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_export_short),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = exportMenuExpanded,
                                onDismissRequest = { exportMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.action_export_zip_backup))
                                            Text(
                                                text = stringResource(R.string.export_zip_subtitle),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        onExportZipClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.action_export_json_backup))
                                            Text(
                                                text = stringResource(R.string.export_json_subtitle),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.DataObject, contentDescription = null) },
                                    onClick = {
                                        exportMenuExpanded = false
                                        onExportJsonClick()
                                    }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onImportClick,
                            enabled = !isBackupBusy,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_center_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_import_short),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section 2: Daily Auto-Backup Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (config.enabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.daily_backup_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (config.enabled) {
                                    stringResource(
                                        R.string.daily_backup_status_on_format,
                                        config.formattedScheduleTime()
                                    )
                                } else {
                                    stringResource(R.string.daily_backup_status_off)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (config.enabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = onToggleDailyEnabled,
                            modifier = Modifier.testTag("daily_backup_switch")
                        )
                    }

                    // Compact 2-dropdown row: Schedule Time & Retention
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OptionDropdownField(
                            label = stringResource(R.string.daily_backup_time_label),
                            value = config.formattedScheduleTime(),
                            expanded = timeMenuExpanded,
                            onExpandedChange = { timeMenuExpanded = it },
                            leadingIcon = Icons.Outlined.Schedule,
                            leadingIconTint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("daily_backup_time_dropdown")
                        ) {
                            DailyBackupManager.SCHEDULE_HOURS.forEach { hour ->
                                val label = DailyBackupConfig(hourOfDay = hour).formattedScheduleTime()
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = if (config.hourOfDay == hour) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        timeMenuExpanded = false
                                        onSelectScheduleHour(hour)
                                    }
                                )
                            }
                        }

                        OptionDropdownField(
                            label = stringResource(R.string.daily_backup_retention_label),
                            value = stringResource(R.string.daily_backup_retention_format, config.retentionDays),
                            expanded = retentionMenuExpanded,
                            onExpandedChange = { retentionMenuExpanded = it },
                            leadingIcon = Icons.Outlined.History,
                            leadingIconTint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("daily_backup_retention_dropdown")
                        ) {
                            DailyBackupManager.RETENTION_OPTIONS.forEach { days ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.daily_backup_retention_format, days)) },
                                    trailingIcon = if (config.retentionDays == days) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        retentionMenuExpanded = false
                                        onSelectRetentionDays(days)
                                    }
                                )
                            }
                        }
                    }

                    // Destination Folder (optional SAF Drive/Device folder + internal vault)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.daily_backup_destination_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = config.folderDisplayName
                                        ?: stringResource(R.string.daily_backup_destination_vault),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (config.folderUriString != null) {
                                IconButton(
                                    onClick = onClearFolderClick,
                                    modifier = Modifier.testTag("daily_backup_clear_folder")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_clear_backup_folder),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onPickFolderClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("daily_backup_choose_folder")
                            ) {
                                Text(
                                    text = stringResource(R.string.action_choose_folder),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // Last backup status & manual trigger
                    if (config.lastBackupMillis != null) {
                        Text(
                            text = buildString {
                                append(
                                    stringResource(
                                        R.string.daily_backup_last_run_format,
                                        DateFormatters.formatDateTime(config.lastBackupMillis)
                                    )
                                )
                                if (!config.lastBackupSummary.isNullOrBlank()) {
                                    append(" · ")
                                    append(config.lastBackupSummary)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!config.lastBackupError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = config.lastBackupError,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onRunDailyBackupNow,
                        enabled = canExportBackup && !isBackupBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("daily_backup_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_backup_now))
                    }
                }
            }

            // Section 3: Saved Daily Snapshots (dropdown picker + restore)
            if (savedBackups.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.daily_backup_history_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val selectedLabel = selectedBackup?.let { b ->
                            val sizeStr = Formatter.formatShortFileSize(context, b.sizeBytes)
                            "${DateFormatters.formatDateTime(b.lastModifiedMillis)} ($sizeStr)"
                        } ?: stringResource(R.string.option_none)

                        OptionDropdownField(
                            label = stringResource(R.string.daily_backup_snapshot_label),
                            value = selectedLabel,
                            expanded = historyMenuExpanded,
                            onExpandedChange = { historyMenuExpanded = it },
                            leadingIcon = Icons.Outlined.History,
                            leadingIconTint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("daily_backup_history_dropdown")
                        ) {
                            savedBackups.forEach { backup ->
                                val sizeStr = Formatter.formatShortFileSize(context, backup.sizeBytes)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(DateFormatters.formatDateTime(backup.lastModifiedMillis))
                                            Text(
                                                text = "${backup.fileName} · $sizeStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = if (backup.filePath == selectedBackup?.filePath) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        selectedBackupPath = backup.filePath
                                        historyMenuExpanded = false
                                    }
                                )
                            }
                        }

                        if (selectedBackup != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onRestoreSavedBackup(selectedBackup) },
                                    enabled = !isBackupBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("restore_daily_backup_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_restore_snapshot))
                                }
                                IconButton(
                                    onClick = { onDeleteSavedBackup(selectedBackup) },
                                    enabled = !isBackupBusy,
                                    modifier = Modifier.testTag("delete_daily_backup_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.action_confirm_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
