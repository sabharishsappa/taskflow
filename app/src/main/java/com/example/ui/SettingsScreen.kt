package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppLockSettings
import com.example.data.AttachmentStorageHelper
import com.example.data.AutoLockTimeout
import com.example.data.BackupFrequency
import com.example.data.BackupSettings
import com.example.data.BiometricAvailability
import com.example.data.LocalBackupSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    lockSettings: AppLockSettings,
    biometricAvailability: BiometricAvailability,
    backupSettings: BackupSettings,
    localBackups: List<LocalBackupSnapshot>,
    totalEntries: Int,
    totalAttachments: Int,
    isBackupBusy: Boolean,
    suggestedExportFileName: String,
    onBack: () -> Unit,
    onRefreshBackups: () -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onSelectAutoLockTimeout: (AutoLockTimeout) -> Unit,
    onToggleHideInAppSwitcher: (Boolean) -> Unit,
    onLockNow: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onBackupNow: () -> Unit,
    onFrequencyChange: (BackupFrequency) -> Unit,
    onIncludeAttachmentsChange: (Boolean) -> Unit,
    onExportToUri: (android.net.Uri) -> Unit,
    onOpenImport: () -> Unit,
    onRestoreLocalSnapshot: (LocalBackupSnapshot) -> Unit,
    onShareLocalSnapshot: (LocalBackupSnapshot) -> Unit,
    onDeleteLocalSnapshot: (LocalBackupSnapshot) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
) {
    var pendingRestoreSnapshot by remember { mutableStateOf<LocalBackupSnapshot?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        onRefreshBackups()
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) onExportToUri(uri)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExportToUri(uri)
    }

    val dateTimeFormatter = remember {
        SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 0.75.dp,
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 16.dp,
                        bottom = 36.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // =========================================================================
                    // 1. PRIVACY & SECURITY (Biometric Lock, Auto-Lock, App Switcher)
                    // =========================================================================
                    item(key = "privacy_header") {
                        SettingsSectionHeader(stringResource(R.string.settings_privacy_header))
                    }

                    item(key = "privacy_card") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Require Biometric Unlock Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleBiometricLock(!lockSettings.isLockEnabled) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsIconTile(icon = Icons.Outlined.Fingerprint)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.privacy_require_biometric),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = if (biometricAvailability == BiometricAvailability.AVAILABLE) {
                                                stringResource(R.string.privacy_require_biometric_sub)
                                            } else {
                                                "No fingerprint, face unlock, or screen PIN enrolled yet"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = lockSettings.isLockEnabled,
                                        onCheckedChange = onToggleBiometricLock,
                                        modifier = Modifier.testTag("biometric_lock_switch"),
                                    )
                                }

                                // Auto-Lock Timing Chips (when enabled)
                                if (lockSettings.isLockEnabled) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.privacy_auto_lock_header),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            AutoLockTimeout.entries.forEach { timeout ->
                                                val selected = lockSettings.autoLockTimeout == timeout
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = { onSelectAutoLockTimeout(timeout) },
                                                    label = { Text(timeout.label) },
                                                    shape = RoundedCornerShape(50),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    ),
                                                    modifier = Modifier.testTag("autolock_${timeout.name.lowercase(Locale.US)}"),
                                                )
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )

                                // Hide in App Switcher Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleHideInAppSwitcher(!lockSettings.hideInAppSwitcher) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsIconTile(icon = Icons.Outlined.VisibilityOff)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.privacy_hide_switcher),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.privacy_hide_switcher_sub),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = lockSettings.hideInAppSwitcher,
                                        onCheckedChange = onToggleHideInAppSwitcher,
                                        modifier = Modifier.testTag("hide_app_switcher_switch"),
                                    )
                                }

                                // Lock Now or Device Security Settings Action
                                if (biometricAvailability != BiometricAvailability.AVAILABLE) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = onOpenSecuritySettings,
                                            shape = RoundedCornerShape(22.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("settings_open_security_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Settings,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.lock_screen_open_settings))
                                        }
                                    }
                                } else if (lockSettings.isLockEnabled) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = onLockNow,
                                            shape = RoundedCornerShape(22.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("lock_journal_now_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.privacy_lock_now))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 2. IMPORT & EXPORT
                    // =========================================================================
                    item(key = "transfer_header") {
                        SettingsSectionHeader(stringResource(R.string.settings_transfer_header))
                    }

                    item(key = "transfer_card") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Import Journal File (.zip, .json, .csv, .md, .txt)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isBackupBusy) { onOpenImport() }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("import_backup_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsIconTile(icon = Icons.Outlined.FileDownload)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.import_option_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.import_option_sub),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )

                                // Export Backup File (SAF CreateDocument)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isBackupBusy) {
                                            if (backupSettings.includeAttachments) {
                                                exportZipLauncher.launch(suggestedExportFileName)
                                            } else {
                                                exportJsonLauncher.launch(suggestedExportFileName)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("export_backup_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsIconTile(icon = Icons.Outlined.FileUpload)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.backup_export_file),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.backup_export_file_sub),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )

                                // Include Attachments Toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onIncludeAttachmentsChange(!backupSettings.includeAttachments) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingsIconTile(icon = Icons.Outlined.PhotoLibrary)
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.backup_include_attachments),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.backup_include_attachments_sub),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = backupSettings.includeAttachments,
                                        onCheckedChange = onIncludeAttachmentsChange,
                                        modifier = Modifier.testTag("include_attachments_switch"),
                                    )
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 3. BACKUP STATUS & FREQUENCY
                    // =========================================================================
                    item(key = "backup_header") {
                        SettingsSectionHeader(stringResource(R.string.backup_title))
                    }

                    item(key = "backup_status_card") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                                                MaterialTheme.colorScheme.surfaceContainer,
                                            )
                                        )
                                    )
                                    .padding(18.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (backupSettings.lastBackupMillis > 0L) {
                                                    Icons.Outlined.CloudDone
                                                } else {
                                                    Icons.Outlined.Backup
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (backupSettings.lastBackupMillis > 0L) {
                                                "Last Backup"
                                            } else {
                                                "No Backups Yet"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = if (backupSettings.lastBackupMillis > 0L) {
                                                dateTimeFormatter.format(Date(backupSettings.lastBackupMillis))
                                            } else {
                                                "Protect your journal with a backup"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "$totalEntries entr${if (totalEntries == 1) "y" else "ies"} · $totalAttachments attachment${if (totalAttachments == 1) "" else "s"} ready to back up",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                val nextScheduled = backupSettings.nextScheduledBackupMillis()
                                val scheduleLine = when {
                                    backupSettings.frequency == BackupFrequency.ON_CHANGE ->
                                        "Auto-backup runs after every saved edit"
                                    nextScheduled != null ->
                                        "Next scheduled (${backupSettings.frequency.label}): ${dateTimeFormatter.format(Date(nextScheduled))}"
                                    else ->
                                        "Automatic backup is off (manual backups only)"
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = scheduleLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = onBackupNow,
                                    enabled = !isBackupBusy,
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("backup_now_button"),
                                ) {
                                    if (isBackupBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(stringResource(R.string.backup_in_progress))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Backup,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.backup_now))
                                    }
                                }
                            }
                        }
                    }

                    item(key = "frequency_header") {
                        SettingsSectionHeader(stringResource(R.string.backup_frequency_header))
                    }

                    item(key = "frequency_card") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                BackupFrequency.entries.forEachIndexed { index, frequency ->
                                    val selected = backupSettings.frequency == frequency
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onFrequencyChange(frequency) }
                                            .padding(horizontal = 16.dp, vertical = 13.dp)
                                            .testTag("freq_${frequency.name.lowercase(Locale.US)}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = frequency.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = frequency.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Icon(
                                            imageVector = if (selected) {
                                                Icons.Rounded.CheckCircle
                                            } else {
                                                Icons.Rounded.RadioButtonUnchecked
                                            },
                                            contentDescription = frequency.label,
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    if (index < BackupFrequency.entries.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // 4. SAVED LOCAL BACKUPS
                    // =========================================================================
                    item(key = "history_header") {
                        SettingsSectionHeader(stringResource(R.string.backup_recent_header))
                    }

                    if (localBackups.isEmpty()) {
                        item(key = "history_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.backup_empty_history),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(localBackups, key = { it.filePath }) { snapshot ->
                            LocalSnapshotRow(
                                snapshot = snapshot,
                                dateLabel = dateTimeFormatter.format(Date(snapshot.createdAtMillis)),
                                enabled = !isBackupBusy,
                                onRestore = { pendingRestoreSnapshot = snapshot },
                                onShare = { onShareLocalSnapshot(snapshot) },
                                onDelete = { onDeleteLocalSnapshot(snapshot) },
                            )
                        }
                    }

                    item(key = "bottom_inset") {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }

    pendingRestoreSnapshot?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingRestoreSnapshot = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestoreLocalSnapshot(snapshot)
                        pendingRestoreSnapshot = null
                    },
                ) {
                    Text(stringResource(R.string.restore_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreSnapshot = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsIconTile(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LocalSnapshotRow(
    snapshot: LocalBackupSnapshot,
    dateLabel: String,
    enabled: Boolean,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val sizeLabel = remember(snapshot.sizeBytes) {
        AttachmentStorageHelper.formatFileSize(snapshot.sizeBytes)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val typeLabel = if (snapshot.includesMedia) "Full Archive (.zip)" else "Text Only (.json)"
                Text(
                    text = "$typeLabel · $sizeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRestore,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = stringResource(R.string.restore_action),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onShare,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
