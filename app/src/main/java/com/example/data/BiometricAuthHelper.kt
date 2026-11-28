package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    UNAVAILABLE,
}

object BiometricAuthHelper {

    fun allowedAuthenticators(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val strongWithDevice = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            if (biometricManager.canAuthenticate(strongWithDevice) == BiometricManager.BIOMETRIC_SUCCESS) {
                strongWithDevice
            } else {
                Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
            }
        } else {
            // API 24-29 safely supports BIOMETRIC_WEAK | DEVICE_CREDENTIAL without throwing
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun checkAvailability(context: Context): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        val authenticators = allowedAuthenticators(context)
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        val authenticators = allowedAuthenticators(activity)
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val userDismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (userDismissed) {
                        onError(null)
                    } else {
                        onError(errString.toString().ifBlank { "Authentication couldn't be completed." })
                    }
                }

                override fun onAuthenticationFailed() {
                    // System BiometricPrompt already displays retry guidance inline
                }
            },
        )

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)

        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Biometric authentication is unavailable right now.")
        }
    }

    fun openSecurityEnrollmentSettings(context: Context): Boolean {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                        putExtra(
                            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL,
                        )
                    }
                )
            }
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try fallback settings screen
            }
        }
        return false
    }

    tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
