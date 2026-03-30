package com.codrivelog.app.service

import java.time.LocalDateTime

/**
 * Immutable snapshot of the drive timer's current state, emitted via
 * [DriveTimerRepository.timerState] and consumed by the UI layer.
 *
 * There are three mutually exclusive states:
 * - [Idle]    — no session in progress
 * - [Running] — session actively recording
 * - [Saving]  — session stopped; waiting for Room insert to complete
 */
sealed interface TimerState {

    /** No drive session is currently active. */
    data object Idle : TimerState

    /**
     * A drive session is actively in progress.
     *
     * @property startTime            When the session was started.
     * @property elapsedSeconds       Total seconds elapsed since [startTime].
     * @property nightSeconds         Seconds driven in night conditions so far.
     * @property currentlyNight       Whether the vehicle is currently in night conditions.
     * @property latitude             Last known latitude, or `null` if no fix yet.
     * @property longitude            Last known longitude, or `null` if no fix yet.
     * @property manualNightOverride  When `true` the user has manually forced night mode
     *                                 (only relevant when [latitude] is `null`).
     */
    data class Running(
        val startTime: LocalDateTime,
        val elapsedSeconds: Long,
        val nightSeconds: Long,
        val currentlyNight: Boolean,
        val latitude: Double?,
        val longitude: Double?,
        val manualNightOverride: Boolean = false,
    ) : TimerState

    /**
     * The session has been stopped and is being persisted.
     * The UI should treat this the same as [Idle] while the save is in flight.
     */
    data object Saving : TimerState
}
