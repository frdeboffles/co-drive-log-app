package com.codrivelog.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [LocationProvider] backed by [LocationManager] location requests.
 *
 * During a drive it keeps a continuous request on both GPS and the network
 * provider, and passes network fixes on only when GPS has been silent for
 * [NETWORK_FALLBACK_AFTER_MS]:
 * - while GPS works, its fixes arrive every interval and network fixes,
 *   tens to hundreds of metres off, never reach the route;
 * - in a garage, between tall buildings or with GPS off, the network takes
 *   over after that delay.
 *
 * A provider that is switched off may still be requested: it starts
 * delivering when the user switches location on.
 *
 * @param context Application context injected by Hilt.
 */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun locationUpdates(intervalMillis: Long): Flow<LatLng> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }
        // Time since boot, not Location.time: GPS stamps fixes with satellite
        // time and the network with the phone clock, which can be minutes
        // apart. Counts as a GPS fix at start, so the network waits for GPS
        // first. Only read and written on the main executor.
        var lastGpsFixAtNanos = SystemClock.elapsedRealtimeNanos()
        val listener = LocationListener { location ->
            if (location.provider == LocationManager.GPS_PROVIDER) {
                lastGpsFixAtNanos = location.elapsedRealtimeNanos
            } else if (location.elapsedRealtimeNanos - lastGpsFixAtNanos < NETWORK_FALLBACK_AFTER_NANOS) {
                return@LocationListener
            }
            trySend(location.toLatLng())
        }
        val request = LocationRequest.Builder(intervalMillis)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        try {
            PROVIDERS.filter { locationManager.hasProvider(it) }.forEach { provider ->
                // Throws SecurityException if permission was revoked meanwhile;
                // the flow then fails and the collector retries.
                @Suppress("MissingPermission")
                locationManager.requestLocationUpdates(provider, request, context.mainExecutor, listener)
            }
        } catch (e: Exception) {
            // A provider registered before the failure must not stay active:
            // each retry would add another listener and drain the battery.
            locationManager.removeUpdates(listener)
            throw e
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }

    override fun lastKnownLocation(): LatLng? {
        if (!hasLocationPermission()) return null
        return PROVIDERS
            .mapNotNull { provider ->
                @Suppress("MissingPermission")
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.toLatLng()
    }

    private fun Location.toLatLng() = LatLng(latitude, longitude, accuracy)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )

        /** GPS silence after which network fixes are used. */
        internal const val NETWORK_FALLBACK_AFTER_NANOS = 30_000_000_000L
    }
}
