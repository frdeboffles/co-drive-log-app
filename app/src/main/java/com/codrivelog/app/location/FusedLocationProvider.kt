package com.codrivelog.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Production [LocationProvider] backed by [LocationManager.getLastKnownLocation].
 *
 * Returns the freshest cached fix from any available provider (GPS → network → passive),
 * or `null` when no fix is available or the permission is missing.
 *
 * Power note: this implementation **never** actively requests location updates.
 * [com.codrivelog.app.service.DriveTimerService] calls this on a periodic timer
 * (every ~60 s) so the device's existing GPS duty-cycle keeps the cache fresh
 * during an active drive.
 *
 * @param context Application context injected by Hilt.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun getLastLocation(): LatLng? {
        if (!hasLocationPermission()) return null

        val current = getCurrentLocation()
        if (current != null) return LatLng(current.latitude, current.longitude)

        val bestCached = PROVIDERS
            .mapNotNull { provider ->
                @Suppress("MissingPermission")
                locationManager.getLastKnownLocation(provider)
            }
            .maxByOrNull { it.time }   // freshest fix wins

        return bestCached?.let { LatLng(it.latitude, it.longitude) }
    }

    private suspend fun getCurrentLocation(): Location? {
        for (provider in PROVIDERS) {
            if (!locationManager.isProviderEnabled(provider)) continue
            val fix = withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }

                    @Suppress("MissingPermission")
                    locationManager.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                    ) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
            }
            if (fix != null) return fix
        }
        return null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Location providers tried in priority order (freshest-first strategy). */
        private val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        private const val CURRENT_LOCATION_TIMEOUT_MS = 8_000L
    }
}
