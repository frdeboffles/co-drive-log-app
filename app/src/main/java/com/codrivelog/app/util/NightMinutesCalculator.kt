package com.codrivelog.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure-function calculator that splits a drive interval into day and night minutes.
 *
 * "Night" is defined per Colorado DR 2324 rules as the time before
 * one hour before sunrise or after one hour after sunset at the observer's
 * location, using [SunCalculator] for the astronomical times.
 *
 * ### Design
 * All inputs and outputs are plain value types — no Android framework dependencies —
 * so this object is trivially testable with JUnit on the JVM.
 *
 * ### Algorithm
 * Given a half-open interval `[start, end)` and a reference date + location, the
 * function:
 * 1. Computes sunrise / sunset UTC times for that date.
 * 2. Applies a one-hour buffer to both solar events.
 * 3. Clips the interval against the night windows:
 *    - pre-sunrise night:  `[midnight, sunrise - 1h)`
 *    - post-sunset night:  `[sunset + 1h, next-midnight)`
 * 4. Returns the total overlap in whole minutes (truncated, not rounded).
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
        val previousDaySunTimes = SunCalculator.calculate(latitudeDeg, longitudeDeg, date.minusDays(1))

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

        // Apply the Colorado DMV one-hour offsets.
        val adjustedSunrise = sunriseTime?.minusHours(1)
        val previousDaySunset = previousDaySunTimes.sunset?.let { prevSunsetLt ->
            val previousDay = date.minusDays(1)
            val previousSunriseLt = previousDaySunTimes.sunrise
            if (previousSunriseLt != null && prevSunsetLt < previousSunriseLt) {
                previousDay.plusDays(1).atTime(prevSunsetLt)
            } else {
                previousDay.atTime(prevSunsetLt)
            }
        }
        val adjustedPreviousDaySunset = previousDaySunset?.plusHours(1)
        val adjustedSunset = sunsetTime?.plusHours(1)

        // Build the list of night windows within [midnight, nextMidnight)
        val nightWindows: List<Pair<LocalDateTime, LocalDateTime>> = buildNightWindows(
            midnight     = midnight,
            nextMidnight = nextMidnight,
            previousSunset = adjustedPreviousDaySunset,
            sunrise      = adjustedSunrise,
            sunset       = adjustedSunset,
        )

        return nightWindows.sumOf { (wStart, wEnd) ->
            overlapSeconds(intervalStart, intervalEnd, wStart, wEnd)
        }
    }

    /**
     * Constructs the night-time windows for a single calendar day.
     *
     * Night = before (sunrise - 1 hour) + after (sunset + 1 hour).
     *
     * @param midnight      Midnight at the start of the day (LocalDateTime).
     * @param nextMidnight  Midnight at the start of the next day.
     * @param previousSunset Previous day's sunset+1h, or `null` if unknown.
     * @param sunrise       Sunrise as a LocalDateTime on this day, or `null` (polar night →
     *                      entire day is night).
     * @param sunset        Sunset as a LocalDateTime (may be on next calendar day if UTC
     *                      wraps past midnight), or `null` (midnight sun → entire day is day).
     * @return A list of `[start, end)` night windows (may be empty for midnight-sun days).
     */
    internal fun buildNightWindows(
        midnight:     LocalDateTime,
        nextMidnight: LocalDateTime,
        previousSunset: LocalDateTime?,
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
            // But night may not start until one hour after the previous sunset.
            val preSunriseStart = maxOf(previousSunset ?: midnight, midnight)
            val preSunriseEnd = minOf(sunrise, nextMidnight)
            if (preSunriseEnd.isAfter(preSunriseStart)) {
                add(preSunriseStart to preSunriseEnd)
            }
            // Post-sunset window: [sunset, nextMidnight)
            val postSunsetStart = maxOf(sunset, midnight)
            if (postSunsetStart.isBefore(nextMidnight)) {
                add(postSunsetStart to nextMidnight)
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
