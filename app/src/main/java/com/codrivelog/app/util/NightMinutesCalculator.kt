package com.codrivelog.app.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pure-function calculator that splits a drive interval into day and night minutes.
 *
 * "Night" is the time between sunset and sunrise at the observer's location,
 * with no buffer, using [SunCalculator] for the astronomical times. No
 * Colorado source (DR 2324, CDOT, DMV) defines night for the 10 required
 * night hours; sunset to sunrise matches the Colorado headlight rule.
 *
 * [nightWindowsUtc] is the only place the rule is defined: the stored night
 * minutes and the live day/night state of the timer both use it.
 *
 * ### Design
 * All inputs and outputs are plain value types — no Android framework dependencies —
 * so this object is trivially testable with JUnit on the JVM.
 *
 * ### Algorithm
 * Given a half-open interval `[start, end)` and a reference date + location, the
 * function:
 * 1. Computes sunrise / sunset UTC times for that date and the day before.
 * 2. Clips the interval against the night windows:
 *    - pre-sunrise night:  `[previous sunset, sunrise)`, clipped to the day
 *    - post-sunset night:  `[sunset, next-midnight)`
 * 3. Returns the total overlap in whole minutes (truncated, not rounded).
 *
 * Drives that span midnight are handled by calling this function once per
 * calendar day via [computeNightMinutesForSession].
 */
object NightMinutesCalculator {

    /**
     * A night rule: the night windows of one UTC calendar day, from that
     * day's and the previous day's sun times.
     */
    fun interface NightRule {
        fun windows(
            date: LocalDate,
            sunTimes: SunCalculator.SunTimes,
            previousDaySunTimes: SunCalculator.SunTimes,
        ): List<Pair<LocalDateTime, LocalDateTime>>
    }

    /** The current rule: sunset to sunrise, no buffer. */
    val CURRENT_RULE = NightRule(::nightWindowsUtc)

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
    ): Int = computeNightMinutes(start, end, latitudeDeg, longitudeDeg, CURRENT_RULE)

    /**
     * Same as [computeNightMinutesForSession] with any [rule]. Lets
     * [LegacyNightRules] reproduce values stored by earlier app versions
     * with the exact same day split and overlap code.
     */
    internal fun computeNightMinutes(
        start: LocalDateTime,
        end: LocalDateTime,
        latitudeDeg: Double,
        longitudeDeg: Double,
        rule: NightRule,
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
                rule          = rule,
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
     * @param rule           Night rule; the current rule unless reproducing an old value.
     * @return               Night seconds as a non-negative long.
     */
    internal fun nightSecondsInDay(
        intervalStart: LocalDateTime,
        intervalEnd:   LocalDateTime,
        date:          LocalDate,
        latitudeDeg:   Double,
        longitudeDeg:  Double,
        rule:          NightRule = CURRENT_RULE,
    ): Long {
        val nightWindows = rule.windows(
            date,
            SunCalculator.calculate(latitudeDeg, longitudeDeg, date),
            SunCalculator.calculate(latitudeDeg, longitudeDeg, date.minusDays(1)),
        )

        return nightWindows.sumOf { (wStart, wEnd) ->
            overlapSeconds(intervalStart, intervalEnd, wStart, wEnd)
        }
    }

    /**
     * Night windows of one UTC calendar day, from that day's and the
     * previous day's sun times. This is the night rule.
     *
     * Sun times are UTC, so in Colorado the evening sunset falls after UTC
     * midnight. The previous day's sunset therefore starts the night on this
     * UTC day, not midnight.
     *
     * @param date                UTC calendar date.
     * @param sunTimes            Sun times (UTC) for [date].
     * @param previousDaySunTimes Sun times (UTC) for the day before [date].
     */
    fun nightWindowsUtc(
        date: LocalDate,
        sunTimes: SunCalculator.SunTimes,
        previousDaySunTimes: SunCalculator.SunTimes,
    ): List<Pair<LocalDateTime, LocalDateTime>> =
        buildNightWindows(
            midnight       = date.atStartOfDay(),
            nextMidnight   = date.plusDays(1).atStartOfDay(),
            previousSunset = sunsetDateTime(date.minusDays(1), previousDaySunTimes),
            sunrise        = sunTimes.sunrise?.let { date.atTime(it) },
            sunset         = sunsetDateTime(date, sunTimes),
        )

    /** `true` when [instantUtc] falls in a night window of its UTC day. */
    fun isNightUtc(
        instantUtc: LocalDateTime,
        sunTimes: SunCalculator.SunTimes,
        previousDaySunTimes: SunCalculator.SunTimes,
    ): Boolean =
        nightWindowsUtc(instantUtc.toLocalDate(), sunTimes, previousDaySunTimes)
            .any { (start, end) -> !instantUtc.isBefore(start) && instantUtc.isBefore(end) }

    /**
     * Sunset of [date] as a date-time. A UTC sunset earlier in the day than
     * sunrise belongs to the evening of [date], so it moves to the next day.
     */
    internal fun sunsetDateTime(date: LocalDate, sunTimes: SunCalculator.SunTimes): LocalDateTime? {
        val sunset = sunTimes.sunset ?: return null
        val sunrise = sunTimes.sunrise
        return if (sunrise != null && sunset < sunrise) date.plusDays(1).atTime(sunset)
               else date.atTime(sunset)
    }

    /**
     * Constructs the night-time windows for a single calendar day.
     *
     * Night = from the previous sunset to sunrise, and from sunset on.
     *
     * @param midnight      Midnight at the start of the day (LocalDateTime).
     * @param nextMidnight  Midnight at the start of the next day.
     * @param previousSunset Previous day's sunset, or `null` if unknown.
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
            // Pre-sunrise window: [previous sunset, sunrise), clipped to the day.
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
}
