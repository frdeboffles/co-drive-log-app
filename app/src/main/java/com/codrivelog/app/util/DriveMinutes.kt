package com.codrivelog.app.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Total and night minutes of a drive, both from the same span of real time.
 *
 * Every path that stores a drive uses this: the timer (instants), manual
 * entry and history edit (typed local times), the database migration and
 * backup import (stored local times). Night minutes therefore never exceed
 * the total, and a daylight-saving change cannot make the two disagree.
 */
object DriveMinutes {

    /** @property night Night minutes, or `null` when no location was given. */
    data class Minutes(val total: Int, val night: Int?)

    /**
     * Minutes between [start] and [end] in real time. Night minutes use the
     * current rule at [location], capped at the total.
     */
    fun fromInstants(start: Instant, end: Instant, location: NightRuleRecalculation.Location?): Minutes {
        val total = Duration.between(start, end).toMinutes().toInt().coerceAtLeast(0)
        val night = location?.let {
            NightMinutesCalculator.computeNightMinutesForSession(
                start        = LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                end          = LocalDateTime.ofInstant(end, ZoneOffset.UTC),
                latitudeDeg  = it.latitude,
                longitudeDeg = it.longitude,
            ).coerceAtMost(total)
        }
        return Minutes(total, night)
    }

    /**
     * Minutes of a drive with local times [start] and [end] recorded in
     * [zone], or `null` when they cannot be a drive.
     *
     * Local times are resolved to instants:
     * - normally, each with the zone's offset at that time;
     * - when [end] is at an earlier clock time than [start] on the
     *   fall-back night, [start] is in the first occurrence of the repeated
     *   hour and [end] in the second: that is the only way a short drive
     *   can end before it started on the clock;
     * - a time that does not exist on the clock (spring-forward gap) means
     *   the moment of the change, 03:00 MDT: 02:30-03:10 is 10 minutes,
     *   02:30-03:40 is 40, 02:30-06:00 is 180. The total grows with the
     *   end time and never gains the skipped hour.
     */
    fun fromLocal(
        start: LocalDateTime,
        end: LocalDateTime,
        zone: ZoneId,
        location: NightRuleRecalculation.Location?,
    ): Minutes? {
        val (startInstant, endInstant) = resolve(start, end, zone) ?: return null
        return fromInstants(startInstant, endInstant, location)
    }

    private fun resolve(start: LocalDateTime, end: LocalDateTime, zone: ZoneId): Pair<Instant, Instant>? {
        if (end.isBefore(start)) {
            val first = start.atZone(zone).withEarlierOffsetAtOverlap().toInstant()
            val second = end.atZone(zone).withLaterOffsetAtOverlap().toInstant()
            return if (second.isBefore(first)) null else first to second
        }
        return instantOf(start, zone) to instantOf(end, zone)
    }

    /** [time] in [zone]; a time in a spring-forward gap is the moment of the change. */
    private fun instantOf(time: LocalDateTime, zone: ZoneId): Instant {
        val rules = zone.rules
        return if (rules.getValidOffsets(time).isEmpty()) {
            rules.getTransition(time).instant
        } else {
            time.atZone(zone).toInstant()
        }
    }

    /**
     * The local start and end of a drive typed as a date and two times of
     * day. This is the only place that decides what an end time not after
     * the start time means:
     * - on the fall-back night, when both times fall in the repeated hour
     *   and the end is earlier, the drive is short and ends in the second
     *   occurrence of that hour (01:50 to 01:10 is 20 minutes, see [fromLocal]);
     * - otherwise the drive crosses midnight and ends the next day; equal
     *   times are a 24-hour drive, as before.
     */
    fun typedInterval(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        zone: ZoneId,
    ): Pair<LocalDateTime, LocalDateTime> {
        val start = date.atTime(startTime)
        val sameDayEnd = date.atTime(endTime)
        return when {
            endTime.isAfter(startTime) -> start to sameDayEnd
            endTime.isBefore(startTime) && isRepeated(start, zone) && isRepeated(sameDayEnd, zone) -> start to sameDayEnd
            else -> start to date.plusDays(1).atTime(endTime)
        }
    }

    /** `true` when [time] occurs twice on the clock (fall-back overlap). */
    private fun isRepeated(time: LocalDateTime, zone: ZoneId): Boolean =
        zone.rules.getValidOffsets(time).size > 1
}
