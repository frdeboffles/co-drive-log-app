package com.codrivelog.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.onboarding.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {
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

    }

    fun updateStudentProfile(studentName: String, permitNumber: String) {
        viewModelScope.launch {
            onboardingRepository.updateStudentProfile(studentName, permitNumber)
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
