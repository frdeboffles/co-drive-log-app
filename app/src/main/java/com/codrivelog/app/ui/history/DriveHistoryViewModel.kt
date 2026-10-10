package com.codrivelog.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.util.DriveMinutes
import com.codrivelog.app.util.NightRuleRecalculation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.ZoneOffset
import kotlin.random.Random
import javax.inject.Inject

/**
 * ViewModel for the drive history screen.
 *
 * Exposes the full ordered list of [DriveSession] records and provides a
 * [delete] action for swipe-to-delete.
 *
 * @param repository Repository providing the single source of truth.
 */
@HiltViewModel
class DriveHistoryViewModel @Inject constructor(
    private val repository: DriveSessionRepository,
    private val supervisorRepository: SupervisorRepository,
    private val routeRepository: DriveRouteRepository,
) : ViewModel() {

    /** Full ordered list of all sessions (most-recent first). */
    val uiState: StateFlow<DriveHistoryUiState> = combine(
        repository.getAll(),
        routeRepository.getSessionIdsWithPoints(),
    ) { sessions, sessionIdsWithRoute ->
        DriveHistoryUiState(
            sessions = sessions,
            sessionIdsWithRoute = sessionIdsWithRoute,
        )
    }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = DriveHistoryUiState(),
        )

    /**
     * Permanently delete [session].
     *
     * @param session The [DriveSession] to remove.
     */
    fun delete(session: DriveSession) {
        viewModelScope.launch { repository.delete(session) }
    }

    fun update(
        session: DriveSession,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        supervisorName: String,
        supervisorInitials: String,
        comments: String,
    ) {
        // The picker works in whole minutes; drop any seconds a caller passes.
        val startTime = startTime.truncatedTo(ChronoUnit.MINUTES)
        val endTime = endTime.truncatedTo(ChronoUnit.MINUTES)

        // Compare what the picker shows: the date and two times of day, in
        // whole minutes. Comparing rebuilt date-times instead would see a
        // change for a drive under one minute, or one that ends at an earlier
        // clock time across the fall-back hour, and rebuild its end on the
        // next day. Unchanged times keep every stored time field.
        val timesOfDayChanged = timesOfDayChanged(session, startTime, endTime)
        if (date == session.date && !timesOfDayChanged) {
            viewModelScope.launch {
                repository.update(
                    session.copy(
                        supervisorName = supervisorName.trim(),
                        supervisorInitials = supervisorInitials.trim().uppercase(),
                        comments = comments.trim().ifBlank { null },
                    )
                )
            }
            return
        }

        val (start, end) = if (!timesOfDayChanged) {
            // Only the date changed: move the drive and keep its seconds, so
            // a drive under one minute stays one.
            val days = ChronoUnit.DAYS.between(session.date, date)
            session.startTime.plusDays(days) to session.endTime.plusDays(days)
        } else {
            DriveMinutes.typedInterval(date, startTime, endTime, NightRuleRecalculation.zoneOf(session.timeZone))
        }

        viewModelScope.launch {
            // Times that cannot be resolved are refused rather than saved
            // with the old totals.
            val retimed = retimed(session, date, start, end) ?: return@launch
            repository.update(
                retimed.copy(
                    supervisorName = supervisorName.trim(),
                    supervisorInitials = supervisorInitials.trim().uppercase(),
                    comments = comments.trim().ifBlank { null },
                )
            )
        }
    }

    private fun timesOfDayChanged(session: DriveSession, startTime: LocalTime, endTime: LocalTime): Boolean =
        startTime != session.startTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES) ||
            endTime != session.endTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES)

    /**
     * [session] moved to [date], [start] and [end], with total minutes and
     * night fields from the same instants ([DriveMinutes]). Only called when
     * the times changed.
     *
     * - [NightSource.MANUAL]: the manual switch value is kept, capped at the
     *   new duration; it cannot be recalculated.
     * - otherwise: recalculated at the stored location, else the drive's last
     *   route point, else the Denver default, in the stored zone.
     *
     * Returns `null` when the times cannot be resolved; the edit is then refused.
     */
    private suspend fun retimed(
        session: DriveSession,
        date: LocalDate,
        start: LocalDateTime,
        end: LocalDateTime,
    ): DriveSession? {
        val zone = NightRuleRecalculation.zoneOf(session.timeZone)
        val moved = session.copy(date = date, startTime = start, endTime = end)
        if (session.nightSource == NightSource.MANUAL) {
            val total = DriveMinutes.fromLocal(start, end, zone, location = null)?.total ?: return null
            return moved.copy(totalMinutes = total, nightMinutes = session.nightMinutes.coerceAtMost(total))
        }

        val storedLocation = session.nightLatitude?.let { lat ->
            session.nightLongitude?.let { lng -> NightRuleRecalculation.Location(lat, lng) }
        }
        val location = storedLocation
            ?: routeRepository.getLatestBySession(session.id)
                ?.let { NightRuleRecalculation.Location(it.latitude, it.longitude) }
            ?: NightRuleRecalculation.DEFAULT_LOCATION
        val minutes = DriveMinutes.fromLocal(start, end, zone, location) ?: return null
        return moved.copy(
            totalMinutes   = minutes.total,
            nightMinutes   = minutes.night ?: session.nightMinutes,
            nightSource    = NightSource.SUN,
            nightLatitude  = location.latitude,
            nightLongitude = location.longitude,
            timeZone       = zone.id,
        )
    }

    fun seedRandomEntries(count: Int = 100) {
        viewModelScope.launch {
            val supervisors = supervisorRepository.getAll()
                .first()
                .ifEmpty { listOf(Supervisor(name = "Default Supervisor", initials = "DS")) }
            val now = LocalDateTime.now()

            repeat(count) { index ->
                val supervisor = supervisors.random()
                val daysBack = Random.nextInt(0, 365)
                val startHour = Random.nextInt(6, 22)
                val startMinute = listOf(0, 15, 30, 45).random()
                val duration = Random.nextInt(25, 181)

                val start = now
                    .minusDays(daysBack.toLong())
                    .withHour(startHour)
                    .withMinute(startMinute)
                    .withSecond(0)
                    .withNano(0)
                val end = start.plusMinutes(duration.toLong())
                val nightMinutes = when {
                    startHour < 7 || startHour >= 20 -> (duration * 0.7).toInt()
                    else -> (duration * 0.15).toInt()
                }

                repository.insert(
                    DriveSession(
                        date = start.toLocalDate(),
                        startTime = start,
                        endTime = end,
                        totalMinutes = duration,
                        nightMinutes = nightMinutes.coerceAtMost(duration),
                        supervisorName = supervisor.name,
                        supervisorInitials = supervisor.initials,
                        comments = "Dev seeded entry #${index + 1}",
                        isManualEntry = true,
                    )
                )
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    suspend fun buildGoogleMapsDirectionsUrl(sessionId: Long): String? {
        val points = routeRepository.getBySession(sessionId).first()
        if (points.size < 2) return null

        val intermediate = points.drop(1).dropLast(1)
        val sampled = sampleWaypoints(intermediate, MAX_MAP_WAYPOINTS)
        val waypointsParam = if (sampled.isEmpty()) {
            ""
        } else {
            sampled.joinToString("|") { "${it.latitude.formatCoord()},${it.longitude.formatCoord()}" }
        }
        val originLat = points.first().latitude.formatCoord()
        val originLng = points.first().longitude.formatCoord()
        val destLat = points.last().latitude.formatCoord()
        val destLng = points.last().longitude.formatCoord()

        return if (waypointsParam.isBlank()) {
            "https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=driving"
        } else {
            "https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=driving&waypoints=$waypointsParam"
        }
    }

    suspend fun getRoutePath(sessionId: Long): List<RouteCoordinate> =
        routeRepository.getBySession(sessionId)
            .first()
            .sortedBy { it.timestamp }
            .map { point ->
                RouteCoordinate(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    timestampEpochSeconds = point.timestamp.toEpochSecond(ZoneOffset.UTC),
                )
            }

    private fun <T> sampleWaypoints(values: List<T>, maxCount: Int): List<T> {
        if (values.size <= maxCount) return values
        if (maxCount <= 0) return emptyList()

        val step = values.size.toDouble() / maxCount.toDouble()
        return (0 until maxCount)
            .map { index -> values[(index * step).toInt().coerceAtMost(values.lastIndex)] }
    }

    private companion object {
        const val MAX_MAP_WAYPOINTS = 8
    }
}

private fun Double.formatCoord(): String =
    java.lang.String.format(java.util.Locale.US, "%.6f", this)

/**
 * Immutable UI state for the history screen.
 *
 * @property sessions All persisted sessions in reverse-chronological order.
 */
data class DriveHistoryUiState(
    val sessions: List<DriveSession> = emptyList(),
    val sessionIdsWithRoute: Set<Long> = emptySet(),
)

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
    val timestampEpochSeconds: Long,
)
