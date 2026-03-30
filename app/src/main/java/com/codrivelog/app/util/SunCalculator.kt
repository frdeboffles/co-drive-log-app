package com.codrivelog.app.util

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calculates astronomical sunrise and sunset times for a given location and date.
 *
 * Uses the algorithm described in the USNO Astronomical Almanac (also known as
 * the "General Solar Position Calculations" published by NOAA/ESRL). No
 * third-party libraries are required.
 *
 * ### Accuracy
 * Results are accurate to within ±1–2 minutes for latitudes between roughly
 * 65°S and 65°N. At extreme latitudes (e.g. northern Alaska in summer) the sun
 * may not rise or set at all; in that case [calculate] returns `null` for the
 * relevant time.
 *
 * ### Reference
 * - NOAA Solar Calculator: https://gml.noaa.gov/grad/solcalc/
 * - Jean Meeus, *Astronomical Algorithms*, 2nd ed.
 */
object SunCalculator {

    /**
     * Result of a sunrise/sunset calculation.
     *
     * @property sunrise Local (wall-clock) time of sunrise, or `null` if the
     *                   sun does not rise on this date at the given location
     *                   (polar night).
     * @property sunset  Local (wall-clock) time of sunset, or `null` if the
     *                   sun does not set on this date at the given location
     *                   (midnight sun).
     */
    data class SunTimes(
        val sunrise: LocalTime?,
        val sunset: LocalTime?,
    )

    // Solar zenith angle for standard sunrise/sunset (centre of sun at horizon,
    // accounting for atmospheric refraction and solar disc radius).
    private const val ZENITH_DEG = 90.833

    /**
     * Calculates local sunrise and sunset times.
     *
     * Times are returned as [LocalTime] values in the **same UTC offset that
     * the caller's longitude implies** — i.e. they are expressed in *solar
     * apparent time*, not a named time zone. For Colorado (UTC−7 MDT / UTC−6
     * MST) the caller should adjust by the appropriate offset when displaying
     * to users.
     *
     * @param latitudeDeg  Observer latitude in decimal degrees (positive = North).
     * @param longitudeDeg Observer longitude in decimal degrees (positive = East,
     *                     negative = West, e.g. Denver = −104.9°).
     * @param date         The calendar date for which to compute the times.
     * @return [SunTimes] with UTC-based sunrise/sunset, or `null` fields for
     *         polar conditions.
     */
    fun calculate(
        latitudeDeg: Double,
        longitudeDeg: Double,
        date: LocalDate,
    ): SunTimes {
        // Day of year (1-based)
        val dayOfYear = date.dayOfYear.toDouble()

        // Longitude hour — converts degrees to "hours"
        val lngHour = longitudeDeg / 15.0

        // Approximate time of event in decimal hours (UTC)
        val tRise = dayOfYear + (6.0 - lngHour) / 24.0
        val tSet  = dayOfYear + (18.0 - lngHour) / 24.0

        val sunrise = computeEventUtcHours(tRise, lngHour, latitudeDeg, rising = true)
        val sunset  = computeEventUtcHours(tSet,  lngHour, latitudeDeg, rising = false)

        return SunTimes(
            sunrise = sunrise?.let { decimalHoursToLocalTime(it) },
            sunset  = sunset?.let  { decimalHoursToLocalTime(it) },
        )
    }

    // ---- Private helpers ----

    /**
     * Computes the UTC event time (decimal hours) for sunrise or sunset.
     *
     * @param t        Approximate time parameter for this event.
     * @param lngHour  Longitude expressed in hours.
     * @param latDeg   Observer latitude in decimal degrees.
     * @param rising   `true` for sunrise, `false` for sunset.
     * @return UTC time as decimal hours [0, 24), or `null` for polar conditions.
     */
    private fun computeEventUtcHours(
        t: Double,
        lngHour: Double,
        latDeg: Double,
        rising: Boolean,
    ): Double? {
        // Sun's mean anomaly (degrees)
        val m = (0.9856 * t) - 3.289

        // Sun's true longitude (degrees), corrected for equation of centre
        var l = m + (1.916 * sinDeg(m)) + (0.020 * sinDeg(2.0 * m)) + 282.634
        l = normalizeDeg(l)

        // Sun's right ascension (hours)
        var ra = atanDeg(0.91764 * tanDeg(l)) / 15.0
        ra = normalizeDegToHours(ra)

        // RA must be in the same quadrant as L
        val lQuadrant  = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra * 15.0 / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant) / 15.0

        // Sun's declination
        val sinDec = 0.39782 * sinDeg(l)
        val cosDec = cosDeg(asinDeg(sinDec))

        // Sun's local hour angle
        val zenith = ZENITH_DEG
        val cosH = (cosDeg(zenith) - sinDec * sinDeg(latDeg)) / (cosDec * cosDeg(latDeg))

        return when {
            cosH >  1.0 -> null  // sun never rises (polar night)
            cosH < -1.0 -> null  // sun never sets  (midnight sun)
            else -> {
                val h = if (rising) {
                    360.0 - acosDeg(cosH)
                } else {
                    acosDeg(cosH)
                }
                val hHours = h / 15.0

                // Local mean time of the event
                val localMeanTime = hHours + ra - (0.06571 * t) - 6.622

                // Convert to UTC
                var utc = localMeanTime - lngHour
                utc = normalizeHours(utc)
                utc
            }
        }
    }

    /** Converts a decimal-hours value to a [LocalTime], clamped to [0, 24). */
    private fun decimalHoursToLocalTime(hours: Double): LocalTime {
        val h = normalizeHours(hours)
        val totalSeconds = (h * 3600).toLong()
        val hh = (totalSeconds / 3600).toInt()
        val mm = ((totalSeconds % 3600) / 60).toInt()
        val ss = (totalSeconds % 60).toInt()
        return LocalTime.of(hh.coerceIn(0, 23), mm.coerceIn(0, 59), ss.coerceIn(0, 59))
    }

    // ---- Trig helpers (degree-based) ----

    private fun sinDeg(deg: Double) = sin(Math.toRadians(deg))
    private fun cosDeg(deg: Double) = cos(Math.toRadians(deg))
    private fun tanDeg(deg: Double) = tan(Math.toRadians(deg))
    private fun asinDeg(x: Double) = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double) = Math.toDegrees(acos(x))
    private fun atanDeg(x: Double) = Math.toDegrees(kotlin.math.atan(x))

    /** Normalises an angle to [0, 360). */
    private fun normalizeDeg(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    /** Normalises RA (hours) to [0, 24). */
    private fun normalizeDegToHours(hours: Double): Double {
        var h = hours % 24.0
        if (h < 0) h += 24.0
        return h
    }

    /** Normalises decimal hours to [0, 24). */
    private fun normalizeHours(hours: Double): Double {
        var h = hours % 24.0
        if (h < 0) h += 24.0
        return h
    }
}
