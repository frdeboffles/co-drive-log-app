package com.codrivelog.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure-function calculator that splits a drive interval into day and night minutes.
 *
 * "Night" is defined as the time before today's sunrise or after today's sunset
 * at the observer's location, using [SunCalculator] for the astronomical times.
 *
 * ### Design
 * All inputs and outputs are plain value types — no Android framework dependencies —
 * so this object is trivially testable with JUnit on the JVM.
 *
 * ### Algorithm
 * Given a half-open interval `[start, end)` and a reference date + location, the
 * function:
 * 1. Computes sunrise / sunset UTC times for that date.
 * 2. Clips the interval against the night windows:
 *    - pre-sunrise night:  `[midnight, sunrise)`
 *    - post-sunset night:  `[sunset, next-midnight)`
 * 3. Returns the total overlap in whole minutes (truncated, not rounded).
 *
 * Drives that span midnight are handled by calling this function once per
 * calendar day via [computeNightMinutesForSession].
 */
object NightMinutesCalculator {

    /**
     * Computes the number of night minutes for a session that may span
     * multiple calendar days.
     *
     * Each calendar day the interval touches is processed independently with
     * that day's sunrise/sunset at the supplied location.  The results are
     * summed.
     *
     * @param start          Session start (inclusive).
     * @param end            Session end (exclusive).
     * @param latitudeDeg    Observer latitude in decimal degrees (positive = North).
     * @param longitudeDeg   Observer longitude in decimal degrees (positive = East).
     * @return               Night minutes as a non-negative integer.
     */
    fun computeNightMinutesForSession(
        start: LocalDateTime,
        end: LocalDateTime,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): Int {
        require(!end.isBefore(start)) { "end must not be before start" }

        var nightSeconds = 0L
        var dayStart = start.toLocalDate()
        val dayEnd = end.toLocalDate()

        while (!dayStart.isAfter(dayEnd)) {
            val intervalStart = if (dayStart == start.toLocalDate()) start
                                else dayStart.atStartOfDay()
            val intervalEnd   = if (dayStart == dayEnd) end
                                else dayStart.plusDays(1).atStartOfDay()

            nightSeconds += nightSecondsInDay(
                intervalStart = intervalStart,
                intervalEnd   = intervalEnd,
                date          = dayStart,
                latitudeDeg   = latitudeDeg,
                longitudeDeg  = longitudeDeg,
            )
            dayStart = dayStart.plusDays(1)
        }

        return (nightSeconds / 60).toInt()
    }

    /**
     * Computes night seconds within a single calendar day's sub-interval.
     *
     * The caller guarantees that [intervalStart] and [intervalEnd] both fall
     * on [date] (or [intervalEnd] is exactly midnight starting the next day).
     *
     * @param intervalStart  Start of the sub-interval (inclusive).
     * @param intervalEnd    End of the sub-interval (exclusive).
     * @param date           The calendar date for sunrise/sunset lookup.
     * @param latitudeDeg    Observer latitude.
     * @param longitudeDeg   Observer longitude.
     * @return               Night seconds as a non-negative long.
     */
    internal fun nightSecondsInDay(
        intervalStart: LocalDateTime,
        intervalEnd:   LocalDateTime,
        date:          LocalDate,
        latitudeDeg:   Double,
        longitudeDeg:  Double,
    ): Long {
        val sunTimes = SunCalculator.calculate(latitudeDeg, longitudeDeg, date)

        // UTC times — align with the date to form LocalDateTime boundaries.
        // If sunrise/sunset is null (polar conditions) the whole interval is night/day.
        val midnight  = date.atStartOfDay()
        val nextMidnight = date.plusDays(1).atStartOfDay()

        val sunriseTime: LocalDateTime? = sunTimes.sunrise?.let { date.atTime(it) }
        val sunsetTime:  LocalDateTime? = sunTimes.sunset?.let  { it ->
            // Sunset UTC may wrap past midnight → put it on the next calendar day
            if (it < (sunrises(sunTimes) ?: LocalTime.MIDNIGHT)) {
                date.plusDays(1).atTime(it)
            } else {
                date.atTime(it)
            }
        }

        // Build the list of night windows within [midnight, nextMidnight)
        val nightWindows: List<Pair<LocalDateTime, LocalDateTime>> = buildNightWindows(
            midnight     = midnight,
            nextMidnight = nextMidnight,
            sunrise      = sunriseTime,
            sunset       = sunsetTime,
        )

        return nightWindows.sumOf { (wStart, wEnd) ->
            overlapSeconds(intervalStart, intervalEnd, wStart, wEnd)
        }
    }

    /**
     * Constructs the night-time windows for a single calendar day.
     *
     * Night = before sunrise + after sunset.
     *
     * @param midnight      Midnight at the start of the day (LocalDateTime).
     * @param nextMidnight  Midnight at the start of the next day.
     * @param sunrise       Sunrise as a LocalDateTime on this day, or `null` (polar night →
     *                      entire day is night).
     * @param sunset        Sunset as a LocalDateTime (may be on next calendar day if UTC
     *                      wraps past midnight), or `null` (midnight sun → entire day is day).
     * @return A list of `[start, end)` night windows (may be empty for midnight-sun days).
     */
    internal fun buildNightWindows(
        midnight:     LocalDateTime,
        nextMidnight: LocalDateTime,
        sunrise:      LocalDateTime?,
        sunset:       LocalDateTime?,
    ): List<Pair<LocalDateTime, LocalDateTime>> = when {
        sunrise == null && sunset == null -> {
            // Polar night: entire calendar day is night
            listOf(midnight to nextMidnight)
        }
        sunrise == null -> {
            // Sun never rises — treat as polar night
            listOf(midnight to nextMidnight)
        }
        sunset == null -> {
            // Midnight sun: no night
            emptyList()
        }
        else -> buildList {
            // Pre-sunrise window: [midnight, sunrise)
            if (sunrise.isAfter(midnight)) {
                add(midnight to sunrise)
            }
            // Post-sunset window: [sunset, nextMidnight)
            // If sunset has wrapped to the next day it still clips correctly
            // because overlapSeconds handles that.
            val effectiveNextMid = if (sunset.isAfter(nextMidnight)) sunset else nextMidnight
            if (sunset.isBefore(effectiveNextMid)) {
                add(sunset to effectiveNextMid)
            }
        }
    }

    /**
     * Returns the overlap in seconds between interval `[aStart, aEnd)` and
     * window `[bStart, bEnd)`.  Both bounds are treated as half-open.
     */
    internal fun overlapSeconds(
        aStart: LocalDateTime, aEnd: LocalDateTime,
        bStart: LocalDateTime, bEnd: LocalDateTime,
    ): Long {
        val overlapStart = if (aStart.isAfter(bStart)) aStart else bStart
        val overlapEnd   = if (aEnd.isBefore(bEnd))   aEnd   else bEnd
        return if (overlapEnd.isAfter(overlapStart)) {
            java.time.Duration.between(overlapStart, overlapEnd).seconds
        } else {
            0L
        }
    }

    /** Helper to extract the sunrise LocalTime from SunTimes for sunset-wrapping logic. */
    private fun sunrises(sunTimes: SunCalculator.SunTimes): LocalTime? = sunTimes.sunrise
}
