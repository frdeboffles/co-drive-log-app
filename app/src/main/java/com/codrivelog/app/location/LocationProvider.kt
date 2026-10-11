package com.codrivelog.app.location

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the Android location stack.
 *
 * A thin interface isolates [com.codrivelog.app.service.DriveTimerService] from
 * the concrete [android.location.LocationManager] so that unit tests can inject
 * a fake without any Android framework dependencies.
 */
interface LocationProvider {

    /**
     * Returns the most-recently known location, or `null` if no fix is
     * available (location permission denied, GPS disabled, etc.).
     *
     * Callers must **not** assume the fix is fresh; the implementation is
     * permitted to return a cached value to avoid excessive GPS power drain.
     */
    suspend fun getLastLocation(): LatLng?

    /**
     * Fixes from a continuous location request, about every [intervalMillis],
     * for as long as the flow is collected. Keeps the GPS active, so fixes
     * keep arriving while the screen is off. Uses GPS only, or the network
     * when GPS is off. Empty without location permission.
     */
    fun locationUpdates(intervalMillis: Long): Flow<LatLng>
}

/**
 * Lightweight latitude/longitude value class.
 *
 * @property latitude  Decimal degrees, positive = North.
 * @property longitude Decimal degrees, positive = East.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = Float.POSITIVE_INFINITY,
)
