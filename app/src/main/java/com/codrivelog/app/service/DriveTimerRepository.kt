package com.codrivelog.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped singleton that bridges [DriveTimerService] and the UI layer.
 *
 * The service writes to [_timerState]; ViewModels and Composables observe the
 * read-only [timerState] StateFlow.  Because both the service and the UI share
 * the same Hilt [javax.inject.Singleton] instance they always see the same state
 * without needing a bound-service connection.
 *
 * This is intentionally a thin state-holder — all business logic lives in the
 * service or in the pure-function utilities.
 */
@Singleton
class DriveTimerRepository @Inject constructor() {

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)

    /**
     * Observable timer state.  Emits [TimerState.Idle] when no session is active,
     * [TimerState.Running] while a drive is in progress, and [TimerState.Saving]
     * briefly while the session is being persisted.
     */
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    /** Updates the published timer state.  Called exclusively by [DriveTimerService]. */
    internal fun update(state: TimerState) {
        _timerState.value = state
    }
}
