package com.codrivelog.app.util

import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.util.NightRuleRecalculation.Location
import com.codrivelog.app.util.NightRuleRecalculation.RouteSummary
import com.codrivelog.app.util.NightRuleRecalculation.toUtc
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Works out where the stored night minutes of a drive saved before 1.2.0
 * came from, and recalculates them with the current rule when that is safe.
 *
 * | Drive | Night minutes came from | Result |
 * | --- | --- | --- |
 * | Manual entry | the sun at Denver, in every version | recalculated |
 * | Timed, a route point before its end | the sun at the GPS fix | recalculated at the last point |
 * | Timed, only the final route point | the manual switch, or coarse fixes that were too inaccurate to record | recalculated only if matched, and only upward; else UNKNOWN |
 * | Timed, no route points | the manual switch, or a history edit at Denver | recalculated only if matched, and only upward; else UNKNOWN |
 *
 * "Matched" means that a computation of an earlier version reproduces the
 * stored value at a location and zone the app could have used. A value from
 * the manual switch almost never matches. Removing the one-hour buffer can
 * only add night minutes, so for an uncertain drive a match that would lower
 * the value is treated as a false match and the value is kept.
 *
 * An uncertain drive that is not recalculated keeps its value as
 * [NightSource.UNKNOWN], not [NightSource.MANUAL]: its origin is not known,
 * so a later edit of its times recalculates it, as app 1.1.0 did. A stored
 * 0 never matches (it is the switch's default and also a daytime result), so
 * it is never raised here.
 *
 * The earlier computations are not limited by drive date: a user could keep
 * an older version installed after the next one was released.
 *
 * The final route point is stamped with the drive's end time, converted to
 * UTC; when it exists, it gives the drive's exact UTC offset, so no zone has
 * to be guessed.
 */
object NightSourceClassifier {

    data class Classification(
        val source: NightSource,
        val location: Location?,
        val zone: ZoneId?,
        /** New total and night minutes, or `null` to keep the stored values. */
        val minutes: DriveMinutes.Minutes?,
    ) {
        val recognized: Boolean get() = minutes != null
        val nightMinutes: Int? get() = minutes?.night

        fun applyTo(session: DriveSession): DriveSession = session.copy(
            totalMinutes   = minutes?.total ?: session.totalMinutes,
            nightMinutes   = minutes?.night ?: session.nightMinutes,
            nightSource    = source,
            nightLatitude  = location?.latitude,
            nightLongitude = location?.longitude,
            timeZone       = zone?.id ?: session.timeZone,
        )
    }

    private enum class Input { UTC, LOCAL }

    /** A computation of an earlier version. */
    private data class Variant(val rule: NightMinutesCalculator.NightRule, val input: Input)

    private val VARIANTS = listOf(
        Variant(LegacyNightRules.BUFFERED_1_0_3, Input.UTC),
        Variant(LegacyNightRules.BEFORE_1_0_3, Input.UTC),
        Variant(LegacyNightRules.BEFORE_1_0_3, Input.LOCAL),
        Variant(NightMinutesCalculator.CURRENT_RULE, Input.UTC),
    )

    /**
     * @param session A drive stored before 1.2.0.
     * @param route   Summary of its route points, or `null` when it has none.
     * @param zones   Zones it may have been recorded in, most likely first.
     */
    fun classify(
        session: DriveSession,
        route: RouteSummary?,
        zones: List<ZoneId>,
    ): Classification {
        val finalOffset = route?.let { finalPointOffset(session, it) }
        val zonesToTry = finalOffset?.let { listOf(regionZoneFor(it, route.lastTimestamp, zones)) } ?: zones
        val denver = NightRuleRecalculation.DEFAULT_LOCATION

        return when {
            session.isManualEntry ->
                certain(session, target = denver, matchAt = listOf(denver), zonesToTry)

            route == null ->
                uncertain(session, target = denver, matchAt = listOf(denver), zonesToTry)

            route.count >= 2 || finalOffset == null ->
                // A point recorded before the end: the timer had a fix, so it
                // used the sun. A history edit may have used Denver.
                certain(session, target = route.lastLocation, matchAt = listOf(route.lastLocation, denver), zonesToTry)

            else ->
                // Only the final point: no fix that passed the accuracy filter
                // while the drive ran.
                uncertain(session, target = route.lastLocation, matchAt = listOf(route.lastLocation, denver), zonesToTry)
        }
    }

    /** The sun was certainly used: recalculate; matching only picks the zone. */
    private fun certain(
        session: DriveSession,
        target: Location,
        matchAt: List<Location>,
        zones: List<ZoneId>,
    ): Classification {
        val zone = zones.singleOrNull()
            ?: zones.firstOrNull { zone -> matchAt.any { reproduces(session, it, zone) } }
            ?: zones.first()
        val minutes = DriveMinutes.fromLocal(session.startTime, session.endTime, zone, target)
        return Classification(
            source   = if (minutes != null) NightSource.SUN else NightSource.UNKNOWN,
            location = target,
            zone     = zone,
            minutes  = minutes,
        )
    }

    /** The value may come from the manual switch: recalculate only on a match, and only upward. */
    private fun uncertain(
        session: DriveSession,
        target: Location,
        matchAt: List<Location>,
        zones: List<ZoneId>,
    ): Classification {
        // 0 is the switch's default and also what a daytime calculation gives.
        if (session.nightMinutes > 0) {
            for (zone in zones) {
                if (matchAt.none { reproduces(session, it, zone) }) continue
                val minutes = DriveMinutes.fromLocal(session.startTime, session.endTime, zone, target)
                if (minutes?.night != null && minutes.night >= session.nightMinutes) {
                    return Classification(NightSource.SUN, target, zone, minutes)
                }
            }
        }
        return Classification(NightSource.UNKNOWN, location = null, zone = null, minutes = null)
    }

    private fun reproduces(session: DriveSession, location: Location, zone: ZoneId): Boolean {
        // The timer's fix at stop is a few seconds from its last route point.
        val tolerance = if (location == NightRuleRecalculation.DEFAULT_LOCATION) 0 else 1
        return VARIANTS.any { variant ->
            val (start, end) = when (variant.input) {
                Input.UTC -> session.startTime.toUtc(zone) to session.endTime.toUtc(zone)
                Input.LOCAL -> session.startTime to session.endTime
            }
            if (end.isBefore(start)) return@any false
            val value = NightMinutesCalculator.computeNightMinutes(start, end, location.latitude, location.longitude, variant.rule)
            abs(value - session.nightMinutes) <= tolerance
        }
    }

    /**
     * A region zone from [zones] with [offset] at the drive's end, so a later
     * edit across daylight saving uses the right offset; else the fixed offset.
     */
    private fun regionZoneFor(offset: ZoneOffset, endUtc: LocalDateTime, zones: List<ZoneId>): ZoneId =
        zones.firstOrNull { zone -> zone.rules.getOffset(endUtc.toInstant(ZoneOffset.UTC)) == offset }
            ?: offset

    /**
     * The drive's UTC offset, when its latest route point is the final fix
     * stamped with its end time; otherwise `null`.
     *
     * The final point's timestamp is the end time converted to UTC with the
     * same nanoseconds, so the difference is a whole number of quarter hours.
     * Any other point, including the start fix, is stamped by its own
     * `LocalDateTime.now()` call, so its nanoseconds differ.
     */
    internal fun finalPointOffset(session: DriveSession, route: RouteSummary): ZoneOffset? {
        val offset = Duration.between(route.lastTimestamp, session.endTime)
        val quarterHours = offset.dividedBy(Duration.ofMinutes(15))
        if (offset != Duration.ofMinutes(15).multipliedBy(quarterHours)) return null
        if (abs(offset.toHours()) > 14) return null
        return ZoneOffset.ofTotalSeconds(offset.seconds.toInt())
    }
}
