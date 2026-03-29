package com.codrivelog.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.codrivelog.app.data.repository.DriveSessionRepository
import javax.inject.Inject

/**
 * ViewModel for the dashboard screen.
 *
 * Exposes aggregated progress data (total hours, night hours) as a [StateFlow]
 * that the UI collects in a lifecycle-aware manner.
 *
 * @param repository Repository providing access to persisted drive sessions.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DriveSessionRepository,
) : ViewModel() {

    /** UI state exposed to the dashboard composable. */
    val uiState: StateFlow<DashboardUiState> = repository
        .getAllSessions()
        .map { sessions ->
            DashboardUiState(
                totalHours = sessions.sumOf { it.totalMinutes }.toFloat() / 60f,
                nightHours = sessions.sumOf { it.nightMinutes }.toFloat() / 60f,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    companion object {
        /** Colorado DMV DR 2324: required total supervised driving hours. */
        const val GOAL_TOTAL_HOURS = 50f

        /** Colorado DMV DR 2324: required night driving hours. */
        const val GOAL_NIGHT_HOURS = 10f
    }
}

/**
 * Immutable snapshot of data needed to render the dashboard.
 *
 * @property totalHours Total accumulated driving hours across all sessions.
 * @property nightHours Accumulated night driving hours across all sessions.
 */
data class DashboardUiState(
    val totalHours: Float = 0f,
    val nightHours: Float = 0f,
)
