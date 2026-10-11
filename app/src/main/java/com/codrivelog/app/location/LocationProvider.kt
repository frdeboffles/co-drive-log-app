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
     * Fixes from a continuous location request, about every [intervalMillis],
     * for as long as the flow is collected. Keeps the GPS active, so fixes
     * keep arriving while the screen is off. Uses GPS, and the network only
     * after GPS has been silent for a while. Completes at once without
     * location permission, and fails if the request is refused; the caller
     * retries in both cases.
     */
    fun locationUpdates(intervalMillis: Long): Flow<LatLng>

    /**
     * The phone's freshest cached location, of any age, or `null`. Never
     * requests a fix. Only for the night calculation of a drive that got no
     * fix at all; never for the route.
     */
    fun lastKnownLocation(): LatLng?
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
