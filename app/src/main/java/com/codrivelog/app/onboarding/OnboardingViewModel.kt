package com.codrivelog.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.repository.SupervisorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the onboarding screen.
 *
 * Collects the student's name and first supervisor, persists them, then
 * signals completion via [onboardingComplete].
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepo: OnboardingRepository,
    private val supervisorRepo: SupervisorRepository,
) : ViewModel() {

    /** Current page index (0 = profile entry, 1 = supervisor entry). */
    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    /** Emits `true` once the user has finished onboarding. */
    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Validation error message, or null when the form is valid. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Advance from the profile page to the supervisor page. */
    fun nextPage(studentName: String, permitNumber: String) {
        if (studentName.isBlank()) {
            _error.value = "Please enter the student's name."
            return
        }
        if (permitNumber.isBlank()) {
            _error.value = "Please enter the permit number."
            return
        }
        _error.value = null
        _page.value  = 1
    }

    /** Go back to the name page. */
    fun previousPage() {
        _error.value = null
        _page.value  = 0
    }

    /**
     * Finish onboarding: persist [studentName], add [supervisorName] / [supervisorInitials]
     * as the first supervisor record, then mark onboarding complete.
     */
    fun finish(
        studentName:        String,
        permitNumber:       String,
        supervisorName:     String,
        supervisorInitials: String,
    ) {
        if (supervisorName.isBlank()) {
            _error.value = "Please enter the supervisor's name."
            return
        }
        if (supervisorInitials.isBlank()) {
            _error.value = "Please enter the supervisor's initials."
            return
        }
        _error.value = null

        viewModelScope.launch {
            supervisorRepo.insert(
                Supervisor(
                    name     = supervisorName.trim(),
                    initials = supervisorInitials.trim().uppercase(),
                )
            )
            onboardingRepo.completeOnboarding(studentName, permitNumber)
            _onboardingComplete.value = true
        }
    }

    /** Clear any displayed error. */
    fun clearError() { _error.value = null }
}
