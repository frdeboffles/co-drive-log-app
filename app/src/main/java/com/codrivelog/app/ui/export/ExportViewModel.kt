package com.codrivelog.app.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.repository.DriveSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the export screen.
 *
 * Exposes summary statistics (session count, total hours, night hours) needed
 * to populate the export screen header.
 *
 * Actual PDF/CSV generation is delegated to the platform via callbacks
 * supplied by the UI layer; this ViewModel merely provides data.
 *
 * @param repository Repository providing all drive sessions.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    repository: DriveSessionRepository,
) : ViewModel() {

    /** Summary data shown at the top of the export screen. */
    val uiState: StateFlow<ExportUiState> = repository
        .getAll()
        .map { sessions ->
            ExportUiState(
                sessionCount = sessions.size,
                totalHours   = sessions.sumOf { it.totalMinutes }.toFloat() / 60f,
                nightHours   = sessions.sumOf { it.nightMinutes }.toFloat() / 60f,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExportUiState(),
        )
}

/**
 * Immutable summary for the export screen.
 *
 * @property sessionCount Total number of recorded sessions.
 * @property totalHours   Aggregate total driving hours.
 * @property nightHours   Aggregate night driving hours.
 */
data class ExportUiState(
    val sessionCount: Int   = 0,
    val totalHours:   Float = 0f,
    val nightHours:   Float = 0f,
)
