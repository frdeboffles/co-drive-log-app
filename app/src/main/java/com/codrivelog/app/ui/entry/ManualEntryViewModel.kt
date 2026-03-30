package com.codrivelog.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.util.NightMinutesCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for the manual drive entry screen.
 *
 * Handles form validation, computes [nightMinutes] via [NightMinutesCalculator]
 * using a default Colorado coordinate when no GPS fix is available, then
 * persists the session via [DriveSessionRepository].
 */
@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    private val driveRepo:      DriveSessionRepository,
    private val supervisorRepo: SupervisorRepository,
) : ViewModel() {

    /** Default lat/lng used when no GPS fix is available: Denver, CO. */
    private val defaultLat = 39.7392
    private val defaultLng = -104.9903

    /** Available saved supervisors for the picker dropdown. */
    val supervisors: StateFlow<List<Supervisor>> = supervisorRepo
        .getAll()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    /** Tracks save operation outcome. */
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * Validate and persist a manually entered drive.
     *
     * Returns early with [SaveState.ValidationError] if the form is invalid.
     */
    fun save(
        date:               LocalDate,
        startTime:          LocalTime,
        endTime:            LocalTime,
        supervisorName:     String,
        supervisorInitials: String,
        comments:           String,
    ) {
        val trimmedName     = supervisorName.trim()
        val trimmedInitials = supervisorInitials.trim()

        if (trimmedName.isBlank()) {
            _saveState.value = SaveState.ValidationError(Field.SUPERVISOR_NAME)
            return
        }
        if (trimmedInitials.isBlank()) {
            _saveState.value = SaveState.ValidationError(Field.SUPERVISOR_INITIALS)
            return
        }

        val startDt = LocalDateTime.of(date, startTime)
        val endDt   = if (!endTime.isAfter(startTime)) {
            // Drive crosses midnight
            LocalDateTime.of(date.plusDays(1), endTime)
        } else {
            LocalDateTime.of(date, endTime)
        }

        if (!endDt.isAfter(startDt)) {
            _saveState.value = SaveState.ValidationError(Field.END_TIME)
            return
        }

        val totalMinutes = Duration.between(startDt, endDt).toMinutes().toInt()
        val nightMinutes = NightMinutesCalculator.computeNightMinutesForSession(
            start        = startDt,
            end          = endDt,
            latitudeDeg  = defaultLat,
            longitudeDeg = defaultLng,
        )

        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            driveRepo.insert(
                DriveSession(
                    date                = date,
                    startTime           = startDt,
                    endTime             = endDt,
                    totalMinutes        = totalMinutes,
                    nightMinutes        = nightMinutes,
                    supervisorName      = trimmedName,
                    supervisorInitials  = trimmedInitials,
                    comments            = comments.trim().ifBlank { null },
                    isManualEntry       = true,
                )
            )
            _saveState.value = SaveState.Saved
        }
    }

    /** Reset state so the screen can show the form again. */
    fun resetState() {
        _saveState.value = SaveState.Idle
    }
}

/** Outcome states for the save operation. */
sealed interface SaveState {
    data object Idle       : SaveState
    data object Saving     : SaveState
    data object Saved      : SaveState
    data class  ValidationError(val field: Field) : SaveState
}

/** Which field failed validation. */
enum class Field { SUPERVISOR_NAME, SUPERVISOR_INITIALS, END_TIME }
