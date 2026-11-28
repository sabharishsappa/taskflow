package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppSettings
import com.example.data.AppTextScale
import com.example.data.AppThemeMode
import com.example.data.TaskRowSize
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrismAmber
import com.example.ui.theme.PrismAmberDark
import com.example.ui.theme.PrismCoral
import com.example.ui.theme.PrismCoralDark
import com.example.ui.theme.PrismSky
import com.example.ui.theme.PrismSkyDark
import com.example.ui.theme.PrismTeal
import com.example.ui.theme.PrismTealDark
import com.example.ui.theme.PrismViolet
import com.example.ui.theme.PrismVioletDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    totalTaskCount: Int,
    completedTaskCount: Int,
    isBackupBusy: Boolean,
    snackbarHostState: SnackbarHostState,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectRowSize: (TaskRowSize) -> Unit,
    onSelectTextScale: (AppTextScale) -> Unit,
    onToggle24HourTime: (Boolean) -> Unit,
    onToggleShowCompleted: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (replaceExisting: Boolean) -> Unit,
    onClearCompleted: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = !isBackupBusy, onBack = onBack)

    val isDark = LocalIsDarkTheme.current
    val violetAccent = if (isDark) PrismVioletDark else PrismViolet
    val tealAccent = if (isDark) PrismTealDark else PrismTeal
    val skyAccent = if (isDark) PrismSkyDark else PrismSky
    val amberAccent = if (isDark) PrismAmberDark else PrismAmber
    val coralAccent = if (isDark) PrismCoralDark else PrismCoral

    var replaceExistingOnImport by rememberSaveable { mutableStateOf(false) }
    var showClearCompletedDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !isBackupBusy,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_desc)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 36.dp)
            ) {
                // SECTION 1: Appearance (Theme, Task Size, Text Size)
                SettingsSectionLabel(text = stringResource(R.string.settings_section_appearance))

                SettingsGroupCard {
                    // Theme Mode
                    SettingsOptionHeader(
                        icon = Icons.Rounded.Palette,
                        accent = violetAccent,
                        title = stringResource(R.string.settings_theme_title),
                        subtitle = stringResource(R.string.settings_theme_desc)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedChoicePill(
                            label = AppThemeMode.SYSTEM.label,
                            icon = Icons.Rounded.BrightnessAuto,
                            selected = settings.themeMode == AppThemeMode.SYSTEM,
                            accent = violetAccent,
                            onClick = { onSelectTheme(AppThemeMode.SYSTEM) },
                            testTag = "settings_theme_system",
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedChoicePill(
                            label = AppThemeMode.LIGHT.label,
                            icon = Icons.Rounded.LightMode,
                            selected = settings.themeMode == AppThemeMode.LIGHT,
                            accent = amberAccent,
                            onClick = { onSelectTheme(AppThemeMode.LIGHT) },
                            testTag = "settings_theme_light",
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedChoicePill(
                            label = AppThemeMode.DARK.label,
                            icon = Icons.Rounded.DarkMode,
                            selected = settings.themeMode == AppThemeMode.DARK,
                            accent = skyAccent,
                            onClick = { onSelectTheme(AppThemeMode.DARK) },
                            testTag = "settings_theme_dark",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )

                    // Task Row Size
                    SettingsOptionHeader(
                        icon = Icons.Rounded.ViewAgenda,
                        accent = tealAccent,
                        title = stringResource(R.string.settings_task_size_title),
                        subtitle = stringResource(R.string.settings_task_size_desc)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TaskRowSize.entries.forEach { sizeOption ->
                            SegmentedChoicePill(
                                label = sizeOption.label,
                                icon = null,
                                selected = settings.taskRowSize == sizeOption,
                                accent = tealAccent,
                                onClick = { onSelectRowSize(sizeOption) },
                                testTag = "settings_row_size_${sizeOption.name.lowercase()}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )

                    // Text Size
                    SettingsOptionHeader(
                        icon = Icons.Rounded.FormatSize,
                        accent = skyAccent,
                        title = stringResource(R.string.settings_text_size_title),
                        subtitle = stringResource(R.string.settings_text_size_desc)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppTextScale.entries.forEach { scaleOption ->
                            SegmentedChoicePill(
                                label = scaleOption.label,
                                icon = null,
                                selected = settings.textScale == scaleOption,
                                accent = skyAccent,
                                onClick = { onSelectTextScale(scaleOption) },
                                testTag = "settings_text_size_${scaleOption.name.lowercase()}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // SECTION 2: Task View & Display
                SettingsSectionLabel(text = stringResource(R.string.settings_section_display))

                SettingsGroupCard {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Schedule,
                        accent = amberAccent,
                        title = stringResource(R.string.settings_24h_title),
                        subtitle = if (settings.use24HourTime) "Example: 14:30" else "Example: 2:30 PM",
                        checked = settings.use24HourTime,
                        onCheckedChange = onToggle24HourTime,
                        testTag = "settings_24h_switch"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.TaskAlt,
                        accent = tealAccent,
                        title = stringResource(R.string.settings_show_completed_title),
                        subtitle = stringResource(R.string.settings_show_completed_desc),
                        checked = settings.showCompletedTasks,
                        onCheckedChange = onToggleShowCompleted,
                        testTag = "settings_show_completed_switch"
                    )
                }

                // SECTION 3: Backup & Restore
                SettingsSectionLabel(text = stringResource(R.string.settings_section_backup))

                SettingsGroupCard {
                    // Export Backup
                    SettingsOptionHeader(
                        icon = Icons.Rounded.FileUpload,
                        accent = tealAccent,
                        title = stringResource(R.string.backup_export_title),
                        subtitle = if (totalTaskCount == 1) {
                            stringResource(R.string.backup_task_count_single)
                        } else {
                            stringResource(R.string.backup_task_count_plural, totalTaskCount)
                        }
                    )

                    Button(
                        onClick = onExportBackup,
                        enabled = !isBackupBusy && totalTaskCount > 0,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tealAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_backup_button")
                    ) {
                        if (isBackupBusy) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (totalTaskCount > 0) {
                                    stringResource(R.string.backup_export_action)
                                } else {
                                    stringResource(R.string.backup_export_empty)
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )

                    // Import Backup
                    SettingsOptionHeader(
                        icon = Icons.Rounded.FileDownload,
                        accent = violetAccent,
                        title = stringResource(R.string.backup_import_title),
                        subtitle = stringResource(R.string.backup_import_desc)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedChoicePill(
                            label = stringResource(R.string.backup_mode_merge),
                            icon = Icons.Rounded.Merge,
                            selected = !replaceExistingOnImport,
                            accent = violetAccent,
                            onClick = { replaceExistingOnImport = false },
                            testTag = "import_mode_merge",
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedChoicePill(
                            label = stringResource(R.string.backup_mode_replace),
                            icon = Icons.Rounded.Sync,
                            selected = replaceExistingOnImport,
                            accent = coralAccent,
                            onClick = { replaceExistingOnImport = true },
                            testTag = "import_mode_replace",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = if (replaceExistingOnImport) {
                            stringResource(R.string.backup_mode_replace_hint)
                        } else {
                            stringResource(R.string.backup_mode_merge_hint)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (replaceExistingOnImport) coralAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { onImportBackup(replaceExistingOnImport) },
                        enabled = !isBackupBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (replaceExistingOnImport) coralAccent else violetAccent
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (replaceExistingOnImport) coralAccent else violetAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.backup_import_action),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (completedTaskCount > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !isBackupBusy) {
                                    showClearCompletedDialog = true
                                }
                                .padding(vertical = 4.dp)
                                .testTag("settings_clear_completed_row")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(coralAccent.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = coralAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_clear_completed_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = coralAccent
                                )
                                Text(
                                    text = if (completedTaskCount == 1) {
                                        stringResource(R.string.settings_clear_completed_single)
                                    } else {
                                        stringResource(R.string.settings_clear_completed_plural, completedTaskCount)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            title = { Text(stringResource(R.string.settings_clear_completed_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_clear_completed_dialog_body,
                        completedTaskCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCompletedDialog = false
                        onClearCompleted()
                    },
                    modifier = Modifier.testTag("confirm_clear_completed_button")
                ) {
                    Text(
                        text = stringResource(R.string.clear),
                        color = coralAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp)
    )
}

@Composable
private fun SettingsGroupCard(
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsOptionHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Color.White
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SegmentedChoicePill(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
