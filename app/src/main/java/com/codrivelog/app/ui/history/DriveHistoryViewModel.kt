package com.codrivelog.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.util.NightMinutesCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

    private val defaultLat = 39.7392
    private val defaultLng = -104.9903

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
        val start = LocalDateTime.of(date, startTime)
        val end = if (!endTime.isAfter(startTime)) {
            LocalDateTime.of(date.plusDays(1), endTime)
        } else {
            LocalDateTime.of(date, endTime)
        }
        if (!end.isAfter(start)) return

        val totalMinutes = Duration.between(start, end).toMinutes().toInt()
        val nightMinutes = NightMinutesCalculator.computeNightMinutesForSession(
            start = start,
            end = end,
            latitudeDeg = defaultLat,
            longitudeDeg = defaultLng,
        )

        viewModelScope.launch {
            repository.update(
                session.copy(
                    date = date,
                    startTime = start,
                    endTime = end,
                    totalMinutes = totalMinutes,
                    nightMinutes = nightMinutes,
                    supervisorName = supervisorName.trim(),
                    supervisorInitials = supervisorInitials.trim().uppercase(),
                    comments = comments.trim().ifBlank { null },
                )
            )
        }
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
}

/**
 * Immutable UI state for the history screen.
 *
 * @property sessions All persisted sessions in reverse-chronological order.
 */
data class DriveHistoryUiState(
    val sessions: List<DriveSession> = emptyList(),
    val sessionIdsWithRoute: Set<Long> = emptySet(),
)
