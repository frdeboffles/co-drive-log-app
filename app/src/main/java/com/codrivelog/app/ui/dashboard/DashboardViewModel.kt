package com.codrivelog.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.onboarding.OnboardingRepository
import com.codrivelog.app.util.NightMinutesCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random
import javax.inject.Inject

/**
 * ViewModel for the dashboard screen.
 *
 * Exposes aggregated progress data (total hours, night hours) and a short list
 * of recent drives as a [StateFlow] the UI collects in a lifecycle-aware manner.
 *
 * @param repository Repository providing access to persisted drive sessions.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DriveSessionRepository,
    private val supervisorRepository: SupervisorRepository,
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {
    private val defaultLat = 39.7392
    private val defaultLng = -104.9903

    /** UI state exposed to the dashboard composable. */
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAll(),
        onboardingRepository.studentName,
        onboardingRepository.permitNumber,
    ) { sessions, studentName, permitNumber ->
            DashboardUiState(
                totalHours   = sessions.sumOf { it.totalMinutes }.toFloat() / 60f,
                nightHours   = sessions.sumOf { it.nightMinutes }.toFloat() / 60f,
                recentDrives = sessions.take(RECENT_DRIVES_COUNT),
                studentName  = studentName,
                permitNumber = permitNumber,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    companion object {
        /** Colorado DMV DR 2324: required total supervised driving hours. */
        const val GOAL_TOTAL_HOURS = 50f

        /** Colorado DMV DR 2324: required night driving hours. */
        const val GOAL_NIGHT_HOURS = 10f

        /** Number of recent drives shown on the dashboard card. */
        const val RECENT_DRIVES_COUNT = 5

        private const val MAX_SESSION_MINUTES = 180
        private const val MIN_SESSION_MINUTES = 25
    }

    fun updateStudentProfile(studentName: String, permitNumber: String) {
        viewModelScope.launch {
            onboardingRepository.updateStudentProfile(studentName, permitNumber)
        }
    }

    fun deleteSession(session: DriveSession) {
        viewModelScope.launch { repository.delete(session) }
    }

    fun updateSession(
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
            repeat(count) {
                val supervisor = supervisors.random()
                val daysBack = Random.nextInt(0, 365)
                val startHour = Random.nextInt(6, 22)
                val startMinute = listOf(0, 15, 30, 45).random()
                val duration = Random.nextInt(MIN_SESSION_MINUTES, MAX_SESSION_MINUTES + 1)

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
                        totalMinutes = Duration.between(start, end).toMinutes().toInt(),
                        nightMinutes = nightMinutes.coerceAtMost(duration),
                        supervisorName = supervisor.name,
                        supervisorInitials = supervisor.initials,
                        comments = "Dev seeded entry #${it + 1}",
                        isManualEntry = true,
                    )
                )
            }
        }
    }
}

/**
 * Immutable snapshot of data needed to render the dashboard.
 *
 * @property totalHours   Total accumulated driving hours across all sessions.
 * @property nightHours   Accumulated night driving hours across all sessions.
 * @property recentDrives The most recent [DashboardViewModel.RECENT_DRIVES_COUNT] sessions.
 */
data class DashboardUiState(
    val totalHours:   Float              = 0f,
    val nightHours:   Float              = 0f,
    val recentDrives: List<DriveSession> = emptyList(),
    val studentName:  String             = "",
    val permitNumber: String             = "",
)
