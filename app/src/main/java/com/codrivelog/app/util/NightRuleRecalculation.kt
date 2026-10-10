package com.codrivelog.app.util

import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Shared values and steps for night minutes under the current rule; the
 * minutes themselves come from [DriveMinutes].
 */
object NightRuleRecalculation {


    /** The app is for Colorado drivers, so drives are recorded in this zone unless stored otherwise. */
    val COLORADO_ZONE: ZoneId = ZoneId.of("America/Denver")

    data class Location(val latitude: Double, val longitude: Double)

    /** Denver: location for drives saved without GPS (manual entry, history edit). */
    val DEFAULT_LOCATION = Location(39.7392, -104.9903)

    /**
     * The zone a drive's local times were recorded in: its stored zone if
     * this device knows it, else Colorado, the zone the migration and backup
     * import try first.
     */
    fun zoneOf(storedZone: String?): ZoneId = parseZone(storedZone) ?: COLORADO_ZONE

    /** [id] as a zone, or `null` when it is missing or unknown to this device. */
    fun parseZone(id: String?): ZoneId? = id?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /**
     * Zones a drive without a stored zone may have been recorded in:
     * Colorado first, then the zone the device is in now. The migration and
     * backup import both use this, so they classify a drive the same way.
     */
    fun candidateZones(deviceZone: ZoneId = ZoneId.systemDefault()): List<ZoneId> =
        listOf(COLORADO_ZONE, deviceZone).distinct()

    /** What the classifier needs from a drive's route points: no list of every point. */
    data class RouteSummary(
        val count: Int,
        /** UTC timestamp of the latest point. */
        val lastTimestamp: LocalDateTime,
        val lastLocation: Location,
    ) {
        companion object {
            /** Summary per session id. */
            fun of(routePoints: List<DriveRoutePoint>): Map<Long, RouteSummary> =
                routePoints.groupBy { it.sessionId }.mapValues { (_, points) ->
                    val last = points.maxBy { it.timestamp }
                    RouteSummary(points.size, last.timestamp, Location(last.latitude, last.longitude))
                }
        }
    }

    /** Counts of what [recalculateSessions] did. */
    data class Summary(val recalculated: Int, val kept: Int)

    data class Result(
        val sessions: List<DriveSession>,
        val summary: Summary,
        /** Ids of the drives that keep their stored night minutes. */
        val keptIds: List<Long>,
    )

    /**
     * Classifies sessions stored before the night source was recorded, and
     * recalculates the ones whose value came from the sun. See
     * [NightSourceClassifier].
     */
    fun recalculateSessions(
        sessions: List<DriveSession>,
        routes: Map<Long, RouteSummary>,
        zones: List<ZoneId> = candidateZones(),
    ): Result {
        val keptIds = mutableListOf<Long>()
        val updated = sessions.map { session ->
            // One drive that cannot be classified must not stop the others.
            val c = runCatching { NightSourceClassifier.classify(session, routes[session.id], zones) }.getOrNull()
            if (c == null || !c.recognized) keptIds += session.id
            c?.applyTo(session) ?: session
        }
        return Result(updated, Summary(sessions.size - keptIds.size, keptIds.size), keptIds)
    }

    /** Local time recorded in [zone] → UTC, with the zone's default offset in a gap or overlap. */
    fun LocalDateTime.toUtc(zone: ZoneId): LocalDateTime =
        atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
}
