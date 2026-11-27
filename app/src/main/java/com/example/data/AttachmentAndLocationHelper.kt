package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val HELPER_TAG = "EventAttachmentLocation"

object AttachmentAndLocationHelper {

    suspend fun copyUriToPrivateAttachment(
        context: Context,
        uri: Uri
    ): JournalAttachment? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = queryDisplayName(context, uri)
                ?: if (mimeType.startsWith("image/")) "Photo_${System.currentTimeMillis()}.jpg"
                else "Attachment_${System.currentTimeMillis()}"

            val extension = displayName.substringAfterLast('.', "").take(10)
            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val safeFileName = if (extension.isNotBlank()) {
                "${UUID.randomUUID()}.$extension"
            } else {
                UUID.randomUUID().toString()
            }
            val destFile = File(attachmentsDir, safeFileName)

            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            JournalAttachment(
                localPath = destFile.absolutePath,
                displayName = displayName,
                mimeType = mimeType,
                isImage = mimeType.startsWith("image/")
            )
        } catch (e: Exception) {
            Log.w(HELPER_TAG, "Failed to copy attachment URI", e)
            null
        }
    }

    suspend fun deletePrivateFile(localPath: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(localPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(HELPER_TAG, "Failed to delete attachment file: $localPath", e)
        }
    }

    fun openAttachmentExternally(context: Context, attachment: JournalAttachment): Boolean {
        return try {
            val file = File(attachment.localPath)
            if (!file.exists()) return false
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, attachment.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(HELPER_TAG, "Unable to open attachment externally", e)
            false
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentDeviceLocation(context: Context): EventLocation? {
        if (!hasLocationPermission(context)) return null
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location = fusedClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .awaitOrNull(cts)
                ?: fusedClient.lastLocation.awaitOrNull()
                ?: return null

            val placeName = reverseGeocode(context, location.latitude, location.longitude)
                ?: String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)

            EventLocation(
                name = placeName,
                latitude = location.latitude,
                longitude = location.longitude
            )
        } catch (e: Exception) {
            Log.w(HELPER_TAG, "Failed to obtain device location", e)
            null
        }
    }

    private suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        if (cont.isActive) {
                            cont.resume(addresses.firstOrNull())
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
            formatAddress(address)
        } catch (e: Exception) {
            Log.w(HELPER_TAG, "Reverse geocoding failed", e)
            null
        }
    }

    private fun formatAddress(address: Address?): String? {
        if (address == null) return null
        val fullLine = address.getAddressLine(0)?.trim()
        if (!fullLine.isNullOrBlank()) return fullLine.take(JournalConstraints.MAX_LOCATION_NAME_LENGTH)
        val parts = listOfNotNull(
            address.featureName?.takeIf { it.isNotBlank() },
            address.locality?.takeIf { it.isNotBlank() },
            address.adminArea?.takeIf { it.isNotBlank() }
        ).distinct()
        return parts.joinToString(", ").takeIf { it.isNotBlank() }
            ?.take(JournalConstraints.MAX_LOCATION_NAME_LENGTH)
    }

    fun openLocationInMaps(context: Context, location: EventLocation): Boolean {
        if (!location.isPresent) return false
        return try {
            val uri = if (location.latitude != null && location.longitude != null) {
                val label = Uri.encode(location.name.ifBlank { "Event Location" })
                Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}($label)")
            } else {
                Uri.parse("geo:0,0?q=${Uri.encode(location.name.trim())}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(HELPER_TAG, "Unable to open maps app", e)
            false
        }
    }

    private suspend fun <T> Task<T>.awaitOrNull(
        cancellationTokenSource: CancellationTokenSource? = null
    ): T? = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancellationTokenSource?.cancel() }
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
