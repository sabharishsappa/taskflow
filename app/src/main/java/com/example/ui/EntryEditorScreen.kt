package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AttachmentKind
import com.example.data.AttachmentStorageHelper
import com.example.data.JournalAttachment
import com.example.data.JournalEntry
import com.example.data.JournalValidation
import com.example.data.LocationHelper
import com.example.data.ReflectionPrompt
import com.example.data.SpeechDictationHelper
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

private val StarterSuggestedTags = listOf(
    "Event", "Activity", "Travel", "Fitness", "Family", "Work", "Gratitude", "Ideas"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(
    existingEntry: JournalEntry?,
    initialPrompt: ReflectionPrompt?,
    existingTags: List<String>,
    recentLocations: List<String>,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: (
        heading: String,
        body: String,
        entryDateMillis: Long,
        locationName: String,
        locationLat: Double?,
        locationLng: Double?,
        tags: List<String>,
        attachments: List<JournalAttachment>,
        bookmarked: Boolean,
    ) -> Unit,
    onInfoMessage: (String) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictatePrompt = stringResource(R.string.dictate_prompt)

    var heading by remember(existingEntry?.id, initialPrompt) {
        mutableStateOf(existingEntry?.heading ?: initialPrompt?.defaultHeading.orEmpty())
    }
    var body by remember(existingEntry?.id, initialPrompt) {
        mutableStateOf(existingEntry?.body.orEmpty())
    }
    var entryDateMillis by remember(existingEntry?.id) {
        mutableLongStateOf(existingEntry?.entryDateMillis ?: System.currentTimeMillis())
    }
    var locationName by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.locationName.orEmpty())
    }
    var locationLat by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.locationLat)
    }
    var locationLng by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.locationLng)
    }
    var tags by remember(existingEntry?.id, initialPrompt) {
        val initial = existingEntry?.tags
            ?: initialPrompt?.defaultTag?.let { listOf(it) }
            ?: emptyList()
        mutableStateOf(initial)
    }
    var attachments by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.attachments ?: emptyList())
    }
    var bookmarked by remember(existingEntry?.id) {
        mutableStateOf(existingEntry?.bookmarked ?: false)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var isImportingMedia by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var partialTranscript by remember { mutableStateOf("") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var viewerPhoto by remember { mutableStateOf<JournalAttachment?>(null) }

    BackHandler(onBack = onCancel)

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
        }
    }

    // Fallback system speech recognition activity (used when inline SpeechRecognizer isn't available)
    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                body = SpeechDictationHelper.appendDictatedText(body, spoken)
            }
        }
    }

    fun launchSystemSpeechIntent() {
        try {
            speechIntentLauncher.launch(SpeechDictationHelper.createRecognizerIntent(dictatePrompt))
        } catch (_: ActivityNotFoundException) {
            onInfoMessage("Speech recognition isn't available on this device.")
        }
    }

    fun startInlineDictation() {
        if (!SpeechDictationHelper.isInlineRecognitionAvailable(context)) {
            launchSystemSpeechIntent()
            return
        }
        try {
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                speechRecognizer = it
            }
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        partialTranscript = ""
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            partialTranscript = partial
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val spoken = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (!spoken.isNullOrEmpty()) {
                            body = SpeechDictationHelper.appendDictatedText(body, spoken)
                        } else if (partialTranscript.isNotBlank()) {
                            body = SpeechDictationHelper.appendDictatedText(body, partialTranscript)
                        }
                        partialTranscript = ""
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        val pending = partialTranscript.trim()
                        if (pending.isNotEmpty() && error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            body = SpeechDictationHelper.appendDictatedText(body, pending)
                        } else {
                            SpeechDictationHelper.friendlyErrorMessage(error)?.let(onInfoMessage)
                        }
                        partialTranscript = ""
                        isListening = false
                    }
                }
            )
            isListening = true
            partialTranscript = ""
            recognizer.startListening(SpeechDictationHelper.createRecognizerIntent(dictatePrompt))
        } catch (_: Exception) {
            isListening = false
            partialTranscript = ""
            launchSystemSpeechIntent()
        }
    }

    fun stopDictation() {
        val recognizer = speechRecognizer
        if (recognizer != null) {
            if (partialTranscript.isNotBlank()) {
                runCatching { recognizer.stopListening() }
            } else {
                runCatching { recognizer.cancel() }
                isListening = false
                partialTranscript = ""
            }
        } else {
            isListening = false
            partialTranscript = ""
        }
    }

    // Microphone runtime permission launcher for inline SpeechRecognizer
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startInlineDictation()
        } else {
            onInfoMessage("Microphone permission is needed for voice dictation.")
        }
    }

    fun toggleDictation() {
        if (isListening) {
            stopDictation()
        } else if (SpeechDictationHelper.isInlineRecognitionAvailable(context)) {
            if (SpeechDictationHelper.hasRecordAudioPermission(context)) {
                startInlineDictation()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            launchSystemSpeechIntent()
        }
    }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isImportingMedia = true
            try {
                val remaining = (JournalValidation.MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)
                if (remaining == 0) {
                    onInfoMessage("Maximum ${JournalValidation.MAX_ATTACHMENTS} attachments per entry.")
                    return@launch
                }
                val imported = mutableListOf<JournalAttachment>()
                for (uri in uris.take(remaining)) {
                    AttachmentStorageHelper.importUri(context, uri, AttachmentKind.IMAGE)
                        .onSuccess { imported.add(it) }
                }
                if (imported.isNotEmpty()) {
                    attachments = attachments + imported
                } else {
                    onInfoMessage("Couldn't import selected photo.")
                }
            } finally {
                isImportingMedia = false
            }
        }
    }

    // Zero-permission Storage Access Framework Document Picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (attachments.size >= JournalValidation.MAX_ATTACHMENTS) {
                onInfoMessage("Maximum ${JournalValidation.MAX_ATTACHMENTS} attachments per entry.")
                return@launch
            }
            isImportingMedia = true
            try {
                AttachmentStorageHelper.importUri(context, uri)
                    .onSuccess { attachments = attachments + it }
                    .onFailure { onInfoMessage("Couldn't attach selected file.") }
            } finally {
                isImportingMedia = false
            }
        }
    }

    // Coarse Location runtime permission launcher (Play Policy compliant)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                isDetectingLocation = true
                LocationHelper.fetchCurrentLocation(context)
                    .onSuccess { resolved ->
                        locationName = resolved.name
                        locationLat = resolved.latitude
                        locationLng = resolved.longitude
                        showLocationDialog = false
                    }
                    .onFailure { err ->
                        onInfoMessage(err.localizedMessage ?: "Unable to fetch location.")
                    }
                isDetectingLocation = false
            }
        } else {
            onInfoMessage("Location permission declined. You can type a place name instead.")
        }
    }

    val wordCount = remember(heading, body) {
        JournalValidation.computeWordCount(heading, body)
    }
    val imageAttachments = remember(attachments) {
        attachments.filter { it.attachmentKind() == AttachmentKind.IMAGE }
    }
    val docAttachments = remember(attachments) {
        attachments.filter { it.attachmentKind() == AttachmentKind.DOCUMENT }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
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
            // Apple Journal Composer Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("editor_cancel_button"),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                // Tappable Date Selector Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showDatePicker = true }
                        .testTag("editor_date_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.cd_pick_date),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = JournalDateFormatter.formatCompactDate(entryDateMillis),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { bookmarked = !bookmarked },
                        modifier = Modifier.testTag("editor_bookmark_button"),
                    ) {
                        Icon(
                            imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(R.string.cd_bookmark),
                            tint = if (bookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = {
                            onSave(
                                heading,
                                body,
                                entryDateMillis,
                                locationName,
                                locationLat,
                                locationLng,
                                tags,
                                attachments,
                                bookmarked,
                            )
                        },
                        enabled = heading.trim().isNotEmpty() && !isSaving && !isImportingMedia,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("editor_save_button"),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.save),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 0.75.dp,
            )

            // Main Scrollable Writing Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    // Borderless Serif Heading Field
                    BasicTextField(
                        value = heading,
                        onValueChange = { if (it.length <= JournalValidation.MAX_HEADING_LENGTH) heading = it },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (heading.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.heading_placeholder),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_heading_input"),
                    )

                    // Active Location & Tags Meta Chips
                    if (locationName.isNotBlank() || tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (locationName.isNotBlank()) {
                                LocationPill(
                                    locationName = locationName,
                                    onClick = { showLocationDialog = true },
                                    onClear = {
                                        locationName = ""
                                        locationLat = null
                                        locationLng = null
                                    },
                                )
                            }
                            tags.forEach { tag ->
                                TagPill(
                                    tag = tag,
                                    onRemove = { tags = tags.filterNot { it == tag } },
                                )
                            }
                        }
                    }

                    // Importing Media Indicator
                    if (isImportingMedia) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Adding attachment…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Photo Attachments Strip
                    if (imageAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(imageAttachments, key = { it.id }) { photo ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 148.dp, height = 148.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                ) {
                                    AttachmentPhotoTile(
                                        attachment = photo,
                                        onClick = { viewerPhoto = photo },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .clickable {
                                                attachments = attachments.filterNot { it.id == photo.id }
                                            },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.cd_remove_attachment),
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Document Attachments List
                    if (docAttachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            docAttachments.forEach { doc ->
                                DocumentAttachmentItem(
                                    attachment = doc,
                                    onClick = { AttachmentStorageHelper.openAttachment(context, doc) },
                                    onRemove = { attachments = attachments.filterNot { it.id == doc.id } },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Voice Dictation Banner
                    AnimatedVisibility(
                        visible = isListening,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        VoiceDictationBanner(
                            partialTranscript = partialTranscript,
                            onStop = { stopDictation() },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }

                    // Expansive Detailed Matter Field
                    BasicTextField(
                        value = body,
                        onValueChange = { if (it.length <= JournalValidation.MAX_BODY_LENGTH) body = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 26.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (body.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.body_placeholder),
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("editor_body_input"),
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$wordCount words · ${attachments.size}/${JournalValidation.MAX_ATTACHMENTS} attachments · ${tags.size}/${JournalValidation.MAX_TAGS} tags",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Signature Apple Journal Floating Bottom Accessory Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerToolbarItem(
                        icon = Icons.Outlined.PhotoLibrary,
                        label = stringResource(R.string.add_photos),
                        active = imageAttachments.isNotEmpty(),
                        testTag = "toolbar_add_photo",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    ComposerToolbarItem(
                        icon = Icons.Outlined.AttachFile,
                        label = stringResource(R.string.add_files),
                        active = docAttachments.isNotEmpty(),
                        testTag = "toolbar_add_file",
                        onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                    )
                    ComposerToolbarItem(
                        icon = if (isListening) Icons.Rounded.GraphicEq else Icons.Outlined.Mic,
                        label = if (isListening) stringResource(R.string.dictate_stop) else stringResource(R.string.dictate_label),
                        active = isListening,
                        highlightColor = if (isListening) MaterialTheme.colorScheme.error else null,
                        contentDescription = if (isListening) {
                            stringResource(R.string.cd_dictate_stop)
                        } else {
                            stringResource(R.string.cd_dictate_start)
                        },
                        testTag = "toolbar_dictate",
                        onClick = { toggleDictation() },
                    )
                    ComposerToolbarItem(
                        icon = Icons.Outlined.LocationOn,
                        label = stringResource(R.string.location_label),
                        active = locationName.isNotBlank(),
                        testTag = "toolbar_add_location",
                        onClick = { showLocationDialog = true },
                    )
                    ComposerToolbarItem(
                        icon = Icons.Outlined.Tag,
                        label = stringResource(R.string.tags_label),
                        active = tags.isNotEmpty(),
                        testTag = "toolbar_add_tags",
                        onClick = { showTagsDialog = true },
                    )
                }
            }
        }
    }

    // Material 3 Date Picker Dialog (timezone-safe UTC -> local noon conversion)
    if (showDatePicker) {
        val initialUtcMillis = remember(entryDateMillis) {
            localMillisToUtcDatePickerMillis(entryDateMillis)
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtc ->
                            entryDateMillis = utcDatePickerMillisToLocalNoon(selectedUtc)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Location Picker Dialog (GPS Current Location + Manual Place + Recent Places)
    if (showLocationDialog) {
        var draftPlace by remember(locationName) { mutableStateOf(locationName) }
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text(stringResource(R.string.add_location)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (LocationHelper.hasCoarseLocationPermission(context)) {
                                scope.launch {
                                    isDetectingLocation = true
                                    LocationHelper.fetchCurrentLocation(context)
                                        .onSuccess { resolved ->
                                            locationName = resolved.name
                                            locationLat = resolved.latitude
                                            locationLng = resolved.longitude
                                            showLocationDialog = false
                                        }
                                        .onFailure { err ->
                                            onInfoMessage(err.localizedMessage ?: "Unable to fetch location.")
                                        }
                                    isDetectingLocation = false
                                }
                            } else {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        enabled = !isDetectingLocation,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("use_current_location_button"),
                    ) {
                        if (isDetectingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detecting place…")
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.use_current_location))
                        }
                    }

                    OutlinedTextField(
                        value = draftPlace,
                        onValueChange = { if (it.length <= JournalValidation.MAX_LOCATION_LENGTH) draftPlace = it },
                        label = { Text(stringResource(R.string.manual_location_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_location_input"),
                    )

                    if (recentLocations.isNotEmpty()) {
                        Text(
                            text = "Recent Places",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            recentLocations.forEach { recent ->
                                LocationPill(
                                    locationName = recent,
                                    onClick = {
                                        locationName = recent
                                        locationLat = null
                                        locationLng = null
                                        showLocationDialog = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = draftPlace.trim()
                        if (trimmed != locationName) {
                            locationName = trimmed
                            locationLat = null
                            locationLng = null
                        }
                        showLocationDialog = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Tag Manager Dialog
    if (showTagsDialog) {
        var tagInput by remember { mutableStateOf("") }
        val suggested = remember(existingTags, tags) {
            (existingTags + StarterSuggestedTags)
                .distinctBy { it.lowercase() }
                .filterNot { candidate -> tags.any { it.equals(candidate, ignoreCase = true) } }
                .take(12)
        }

        fun addDraftTag() {
            val clean = JournalValidation.sanitizeTag(tagInput)
            if (clean.isEmpty()) return
            if (tags.size >= JournalValidation.MAX_TAGS) {
                onInfoMessage("Maximum ${JournalValidation.MAX_TAGS} tags per entry.")
                return
            }
            if (tags.none { it.equals(clean, ignoreCase = true) }) {
                tags = tags + clean
            }
            tagInput = ""
        }

        AlertDialog(
            onDismissRequest = { showTagsDialog = false },
            title = { Text(stringResource(R.string.tags_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { if (it.length <= JournalValidation.MAX_TAG_LENGTH) tagInput = it },
                            placeholder = { Text(stringResource(R.string.add_tag_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { addDraftTag() }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tag_input_field"),
                        )
                        IconButton(
                            onClick = { addDraftTag() },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .testTag("add_tag_confirm_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.cd_add_tags),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (tags.isNotEmpty()) {
                        Text(
                            text = "Selected (${tags.size}/${JournalValidation.MAX_TAGS})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tags.forEach { tag ->
                                TagPill(
                                    tag = tag,
                                    selected = true,
                                    onRemove = { tags = tags.filterNot { it == tag } },
                                )
                            }
                        }
                    }

                    if (suggested.isNotEmpty()) {
                        Text(
                            text = "Suggestions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            suggested.forEach { candidate ->
                                TagPill(
                                    tag = candidate,
                                    onClick = {
                                        if (tags.size < JournalValidation.MAX_TAGS) {
                                            tags = tags + candidate
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addDraftTag()
                        showTagsDialog = false
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        )
    }

    viewerPhoto?.let { photo ->
        FullScreenPhotoViewer(
            attachment = photo,
            onDismiss = { viewerPhoto = null },
        )
    }
}

@Composable
private fun VoiceDictationBanner(
    partialTranscript: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mic_pulse_alpha",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_dictation_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(pulseAlpha),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (partialTranscript.isBlank()) {
                        stringResource(R.string.dictate_listening)
                    } else {
                        partialTranscript
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onStop)
                    .testTag("dictation_stop_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = stringResource(R.string.cd_dictate_stop),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.dictate_stop),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerToolbarItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
    highlightColor: Color? = null,
    contentDescription: String = label,
) {
    val tint = when {
        highlightColor != null -> highlightColor
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

private fun localMillisToUtcDatePickerMillis(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
        )
    }
    return utcCal.timeInMillis
}

private fun utcDatePickerMillisToLocalNoon(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val localCal = Calendar.getInstance().apply {
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }
    return localCal.timeInMillis
}
