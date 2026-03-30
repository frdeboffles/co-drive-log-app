package com.codrivelog.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    /** Full ordered list of all sessions (most-recent first). */
    val uiState: StateFlow<DriveHistoryUiState> = repository
        .getAll()
        .map { DriveHistoryUiState(sessions = it) }
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
}

/**
 * Immutable UI state for the history screen.
 *
 * @property sessions All persisted sessions in reverse-chronological order.
 */
data class DriveHistoryUiState(
    val sessions: List<DriveSession> = emptyList(),
)
