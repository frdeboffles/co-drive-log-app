package com.codrivelog.app.location

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
)
