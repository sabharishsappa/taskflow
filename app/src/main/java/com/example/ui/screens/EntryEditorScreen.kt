package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AttachmentAndLocationHelper
import com.example.data.EventLocation
import com.example.data.JournalAttachment
import com.example.data.JournalConstraints
import com.example.ui.DateFormatters
import com.example.ui.JournalViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(
    entryId: String?,
    viewModel: JournalViewModel,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val existingEntry = remember(entryId) { entryId?.let { viewModel.findEntryById(it) } }
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val existingTagsPool by viewModel.availableTags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var eventDateMillis by rememberSaveable(entryId) {
        mutableLongStateOf(existingEntry?.eventDateMillis ?: System.currentTimeMillis())
    }
    var heading by rememberSaveable(entryId) { mutableStateOf(existingEntry?.heading.orEmpty()) }
    var content by rememberSaveable(entryId) { mutableStateOf(existingEntry?.content.orEmpty()) }
    var favorite by rememberSaveable(entryId) { mutableStateOf(existingEntry?.favorite ?: false) }
    var tags by rememberSaveable(entryId) { mutableStateOf(existingEntry?.tags.orEmpty()) }
    var tagInput by rememberSaveable { mutableStateOf("") }

    var attachments by remember(entryId) { mutableStateOf(existingEntry?.attachments.orEmpty()) }
    var newlyAddedAttachmentPaths by remember(entryId) { mutableStateOf<Set<String>>(emptySet()) }

    var locationName by rememberSaveable(entryId) { mutableStateOf(existingEntry?.location?.name.orEmpty()) }
    var locationLat by rememberSaveable(entryId) { mutableStateOf(existingEntry?.location?.latitude) }
    var locationLng by rememberSaveable(entryId) { mutableStateOf(existingEntry?.location?.longitude) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val currentLocationObj = remember(locationName, locationLat, locationLng) {
        EventLocation(
            name = locationName.trim(),
            latitude = locationLat,
            longitude = locationLng
        ).takeIf { it.isPresent }
    }

    val hasUnsavedChanges = remember(
        eventDateMillis,
        heading,
        content,
        favorite,
        tags,
        attachments,
        currentLocationObj,
        existingEntry
    ) {
        if (existingEntry == null) {
            heading.isNotBlank() ||
                content.isNotBlank() ||
                tags.isNotEmpty() ||
                attachments.isNotEmpty() ||
                currentLocationObj != null
        } else {
            eventDateMillis != existingEntry.eventDateMillis ||
                heading != existingEntry.heading ||
                content != existingEntry.content ||
                favorite != existingEntry.favorite ||
                tags != existingEntry.tags ||
                attachments != existingEntry.attachments ||
                currentLocationObj != existingEntry.location
        }
    }

    fun cleanupUnsavedTempAttachments() {
        val pathsToDelete = newlyAddedAttachmentPaths
        if (pathsToDelete.isNotEmpty()) {
            scope.launch {
                pathsToDelete.forEach { AttachmentAndLocationHelper.deletePrivateFile(it) }
            }
        }
    }

    fun handlePickedUri(uri: android.net.Uri?) {
        if (uri == null) return
        if (attachments.size >= JournalConstraints.MAX_ATTACHMENTS) {
            errorMessage = context.getString(R.string.validation_attachments_limit)
            return
        }
        scope.launch {
            val copied = AttachmentAndLocationHelper.copyUriToPrivateAttachment(context, uri)
            if (copied != null) {
                attachments = attachments + copied
                newlyAddedAttachmentPaths = newlyAddedAttachmentPaths + copied.localPath
                errorMessage = null
            } else {
                errorMessage = context.getString(R.string.attachment_copy_failed)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedUri(uri) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> handlePickedUri(uri) }

    fun fetchLocationNow() {
        isFetchingLocation = true
        errorMessage = null
        scope.launch {
            val detected = AttachmentAndLocationHelper.fetchCurrentDeviceLocation(context)
            isFetchingLocation = false
            if (detected != null) {
                locationName = detected.name
                locationLat = detected.latitude
                locationLng = detected.longitude
            } else {
                errorMessage = context.getString(R.string.location_unavailable)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLocationNow()
        } else {
            errorMessage = context.getString(R.string.location_permission_denied)
        }
    }

    fun requestClose() {
        if (hasUnsavedChanges && !isSaving) {
            showDiscardDialog = true
        } else if (!isSaving) {
            cleanupUnsavedTempAttachments()
            onNavigateBack()
        }
    }

    fun tryAddTag(rawTag: String = tagInput) {
        val cleaned = rawTag.trim().lowercase().removePrefix("#")
        if (cleaned.isEmpty()) return
        if (cleaned.length > JournalConstraints.MAX_TAG_LENGTH) {
            errorMessage = context.getString(R.string.validation_tag_length)
            return
        }
        if (tags.size >= JournalConstraints.MAX_TAGS) {
            errorMessage = context.getString(R.string.validation_tags_limit)
            return
        }
        if (!tags.contains(cleaned)) {
            tags = tags + cleaned
        }
        if (rawTag == tagInput) tagInput = ""
        errorMessage = null
    }

    fun submitSave() {
        val cleanHeading = heading.trim()
        val cleanContent = content.trim()
        val validationError = when {
            cleanHeading.isEmpty() -> context.getString(R.string.validation_heading_required)
            cleanHeading.length > JournalConstraints.MAX_HEADING_LENGTH ->
                context.getString(R.string.validation_heading_too_long)
            cleanContent.length > JournalConstraints.MAX_CONTENT_LENGTH ->
                context.getString(R.string.validation_content_too_long)
            tags.size > JournalConstraints.MAX_TAGS ->
                context.getString(R.string.validation_tags_limit)
            attachments.size > JournalConstraints.MAX_ATTACHMENTS ->
                context.getString(R.string.validation_attachments_limit)
            else -> null
        }

        if (validationError != null) {
            errorMessage = validationError
            return
        }

        errorMessage = null
        viewModel.saveEntry(
            entryId = existingEntry?.id,
            heading = cleanHeading,
            content = cleanContent,
            eventDateMillis = eventDateMillis,
            favorite = favorite,
            tags = tags,
            attachments = attachments,
            location = currentLocationObj,
            onSuccess = { savedId ->
                newlyAddedAttachmentPaths = emptySet()
                onSaved(savedId)
            },
            onError = { msg -> errorMessage = msg }
        )
    }

    BackHandler(enabled = true) {
        requestClose()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateFormatters.localMillisToUtcPickerMillis(eventDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtc ->
                            eventDateMillis = DateFormatters.utcPickerMillisToLocalMiddayMillis(selectedUtc)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("confirm_date_button")
                ) {
                    Text(stringResource(R.string.action_confirm_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.editor_discard_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.editor_discard_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        cleanupUnsavedTempAttachments()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_discard_button")
                ) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = ::requestClose,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(
                            if (existingEntry == null) R.string.editor_new_title
                            else R.string.editor_edit_title
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(
                        onClick = { favorite = !favorite },
                        modifier = Modifier.testTag("editor_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (favorite) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.action_toggle_favorite),
                            tint = if (favorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Button(
                        onClick = ::submitSave,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("save_entry_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_save),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
                .testTag("entry_editor_screen"),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 36.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                // Event Date Picker Row
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showDatePicker = true }
                        .testTag("event_date_picker_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.editor_date_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = DateFormatters.formatFullDate(eventDateMillis),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.action_change_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Heading Input (Mandatory)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    if (heading.isEmpty()) {
                        Text(
                            text = stringResource(R.string.editor_heading_placeholder),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    BasicTextField(
                        value = heading,
                        onValueChange = { next ->
                            if (next.length <= JournalConstraints.MAX_HEADING_LENGTH) {
                                heading = next
                                if (errorMessage != null) errorMessage = null
                            }
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_heading_input")
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Content Input (Optional)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    if (content.isEmpty()) {
                        Text(
                            text = stringResource(R.string.editor_body_placeholder),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = { next ->
                            if (next.length <= JournalConstraints.MAX_CONTENT_LENGTH) {
                                content = next
                                if (errorMessage != null) errorMessage = null
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .testTag("editor_body_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Attachments Section
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.editor_attachments_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box {
                            FilledTonalIconButton(
                                onClick = { showAttachMenu = true },
                                enabled = attachments.size < JournalConstraints.MAX_ATTACHMENTS,
                                modifier = Modifier.testTag("add_attachment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = stringResource(R.string.action_add_attachment),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_add_photo)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("add_photo_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_add_file)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AttachFile,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    modifier = Modifier.testTag("add_file_button")
                                )
                            }
                        }
                    }

                    if (attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(attachments, key = { it.localPath }) { item ->
                                AttachmentPreviewChip(
                                    attachment = item,
                                    onRemove = {
                                        attachments = attachments - item
                                        if (newlyAddedAttachmentPaths.contains(item.localPath)) {
                                            newlyAddedAttachmentPaths = newlyAddedAttachmentPaths - item.localPath
                                            scope.launch {
                                                AttachmentAndLocationHelper.deletePrivateFile(item.localPath)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Map Location Section
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.editor_location_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FilledTonalButton(
                            onClick = {
                                if (AttachmentAndLocationHelper.hasLocationPermission(context)) {
                                    fetchLocationNow()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                            },
                            enabled = !isFetchingLocation,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("current_location_button")
                        ) {
                            if (isFetchingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.location_fetching),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_use_current_location),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { next ->
                            if (next.length <= JournalConstraints.MAX_LOCATION_NAME_LENGTH) {
                                locationName = next
                                // Clear stale coordinates if the user manually edits the place text
                                if (next.isBlank()) {
                                    locationLat = null
                                    locationLng = null
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.editor_location_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingIcon = {
                            if (locationName.isNotEmpty() || locationLat != null) {
                                IconButton(
                                    onClick = {
                                        locationName = ""
                                        locationLat = null
                                        locationLng = null
                                    },
                                    modifier = Modifier.testTag("clear_location_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_clear_location)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_location_input")
                    )

                    val activeLoc = currentLocationObj
                    if (activeLoc != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val coordsText = activeLoc.formattedCoordinates()
                            if (coordsText != null) {
                                Text(
                                    text = coordsText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                            TextButton(
                                onClick = {
                                    AttachmentAndLocationHelper.openLocationInMaps(context, activeLoc)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("editor_preview_map_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Map,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_open_in_maps),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Custom Tags Section
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.editor_tags_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            tags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = { tags = tags - tag },
                                    label = { Text("#$tag") },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_remove_tag),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (tags.size < JournalConstraints.MAX_TAGS) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tagInput,
                                onValueChange = { next ->
                                    if (next.length <= JournalConstraints.MAX_TAG_LENGTH) {
                                        tagInput = next
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.editor_tag_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { tryAddTag() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_tag_input")
                            )

                            IconButton(
                                onClick = { tryAddTag() },
                                enabled = tagInput.isNotBlank(),
                                modifier = Modifier.testTag("add_tag_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.action_add_tag)
                                )
                            }
                        }

                        // Reusable tag suggestions from user's existing custom tags
                        val suggestions = remember(existingTagsPool, tags) {
                            existingTagsPool.filterNot { tags.contains(it) }.take(6)
                        }
                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(suggestions, key = { "suggest_$it" }) { suggestion ->
                                    AssistChip(
                                        onClick = { tryAddTag(suggestion) },
                                        label = { Text("+$suggestion") }
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
private fun AttachmentPreviewChip(
    attachment: JournalAttachment,
    onRemove: () -> Unit
) {
    if (attachment.isImage) {
        Box(modifier = Modifier.size(96.dp)) {
            AsyncImage(
                model = File(attachment.localPath),
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_remove_attachment),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 220.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_remove_attachment),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
