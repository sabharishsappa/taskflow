package com.example.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

object SpeechDictationHelper {

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isInlineRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun createRecognizerIntent(prompt: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    fun appendDictatedText(
        existingBody: String,
        dictated: String,
        maxLength: Int = JournalValidation.MAX_BODY_LENGTH,
    ): String {
        val clean = dictated.trim()
        if (clean.isEmpty()) return existingBody

        val formatted = if (existingBody.isBlank()) {
            clean.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        } else {
            val separator = if (existingBody.last().isWhitespace()) "" else " "
            val shouldCapitalize = existingBody.trimEnd().lastOrNull() in setOf('.', '!', '?')
            val adjusted = if (shouldCapitalize) {
                clean.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                }
            } else {
                clean
            }
            existingBody + separator + adjusted
        }
        return formatted.take(maxLength)
    }

    fun friendlyErrorMessage(errorCode: Int): String? {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Couldn't record audio from microphone."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for dictation."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unavailable for speech recognition."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again in a moment."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition service encountered an error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything. Tap the mic to try again."
            SpeechRecognizer.ERROR_CLIENT -> null
            else -> "Speech recognition stopped unexpectedly."
        }
    }
}
