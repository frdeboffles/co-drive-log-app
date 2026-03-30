package com.codrivelog.app.ui.timer

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.service.DriveTimerRepository
import com.codrivelog.app.service.DriveTimerService
import com.codrivelog.app.service.TimerState
import com.codrivelog.app.util.ElapsedTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the drive timer widget embedded in the Dashboard.
 *
 * Translates [TimerState] from [DriveTimerRepository] into a display-ready
 * [DriveTimerUiState] and fires intents to [DriveTimerService].
 *
 * @param timerRepository Shared state holder written to by the service.
 * @param context         Application context used to start/stop the service.
 */
@HiltViewModel
class DriveTimerViewModel @Inject constructor(
    private val timerRepository: DriveTimerRepository,
    supervisorRepository: SupervisorRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Display state observed by the Composable timer widget. */
    val uiState: StateFlow<DriveTimerUiState> = timerRepository.timerState
        .map { it.toUiState() }
        .stateIn(
            scope   = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DriveTimerUiState.Idle,
        )

    /** Saved supervisors used by the start-drive form dropdown. */
    val supervisors: StateFlow<List<Supervisor>> = supervisorRepository
        .getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Starts a new drive session.
     *
     * @param supervisorName     Full name of the supervising adult.
     * @param supervisorInitials Initials for the printed log.
     * @param comments           Optional free-text notes.
     */
    fun startDrive(
        supervisorName:     String,
        supervisorInitials: String,
        comments:           String? = null,
    ) {
        val intent = Intent(context, DriveTimerService::class.java).apply {
            action = DriveTimerService.ACTION_START
            putExtra(DriveTimerService.EXTRA_SUPERVISOR_NAME,     supervisorName)
            putExtra(DriveTimerService.EXTRA_SUPERVISOR_INITIALS, supervisorInitials)
            if (comments != null) putExtra(DriveTimerService.EXTRA_COMMENTS, comments)
        }
        context.startForegroundService(intent)
    }

    /**
     * Stops the current drive session, triggering persistence and service shutdown.
     */
    fun stopDrive() {
        val intent = Intent(context, DriveTimerService::class.java).apply {
            action = DriveTimerService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Toggles manual night override when GPS is unavailable.
     * Delegates to [DriveTimerRepository.setManualNightOverride]; the service
     * picks up the new value on its next tick.
     */
    fun setManualNightOverride(isNight: Boolean) {
        timerRepository.setManualNightOverride(isNight)
    }

    // ---- Mapping ----

    private fun TimerState.toUiState(): DriveTimerUiState = when (this) {
        TimerState.Idle, TimerState.Saving -> DriveTimerUiState.Idle
        is TimerState.Running -> DriveTimerUiState.Active(
            elapsedFormatted       = ElapsedTimeFormatter.formatHms(elapsedSeconds),
            nightFormatted         = ElapsedTimeFormatter.formatHm(nightSeconds),
            currentlyNight         = currentlyNight,
            hasGpsFix              = latitude != null,
            manualNightOverride    = manualNightOverride,
            manualOverrideAvailable = latitude == null,
        )
    }
}

/**
 * Immutable display state for the timer widget.
 */
sealed interface DriveTimerUiState {

    /** No session is active; show a "Start Drive" button. */
    data object Idle : DriveTimerUiState

    /**
     * A session is in progress; show elapsed time and a "Stop Drive" button.
     *
     * @property elapsedFormatted        Total elapsed time as `H:MM:SS`.
     * @property nightFormatted          Night time as `H:MM`.
     * @property currentlyNight          Whether the current moment is classified as night.
     * @property hasGpsFix               Whether a GPS fix is available.
     * @property manualNightOverride     Current value of the manual override toggle.
     * @property manualOverrideAvailable `true` when the toggle should be shown (no GPS fix).
     */
    data class Active(
        val elapsedFormatted: String,
        val nightFormatted:   String,
        val currentlyNight:   Boolean,
        val hasGpsFix:        Boolean,
        val manualNightOverride:     Boolean = false,
        val manualOverrideAvailable: Boolean = false,
    ) : DriveTimerUiState
}
