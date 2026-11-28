package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

object LocationHelper {

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Result<ResolvedLocation> {
        if (!hasCoarseLocationPermission(context)) {
            return Result.failure(SecurityException("Location permission not granted."))
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val location = getCurrentLocationOrNull(fusedClient) ?: getLastKnownLocationOrNull(fusedClient)
            ?: return Result.failure(IllegalStateException("Unable to detect current location. You can enter a place manually."))

        val placeName = reverseGeocode(context, location.latitude, location.longitude)
            ?: String.format(Locale.getDefault(), "%.4f, %.4f", location.latitude, location.longitude)

        return Result.success(
            ResolvedLocation(
                name = placeName,
                latitude = location.latitude,
                longitude = location.longitude,
            )
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationOrNull(
        fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    ): Location? = suspendCancellableCoroutine { cont ->
        val tokenSource = CancellationTokenSource()
        cont.invokeOnCancellation { tokenSource.cancel() }
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocationOrNull(
        fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    ): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
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
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
            formatAddress(address)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatAddress(address: Address?): String? {
        if (address == null) return null
        val parts = listOfNotNull(
            address.subLocality?.takeIf { it.isNotBlank() },
            address.locality?.takeIf { it.isNotBlank() },
            address.adminArea?.takeIf { it.isNotBlank() },
        ).distinct()
        return when {
            parts.isNotEmpty() -> parts.take(2).joinToString(", ")
            !address.featureName.isNullOrBlank() -> address.featureName
            !address.countryName.isNullOrBlank() -> address.countryName
            else -> null
        }
    }
}
