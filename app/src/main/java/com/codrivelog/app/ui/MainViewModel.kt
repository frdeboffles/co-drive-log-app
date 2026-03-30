package com.codrivelog.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.onboarding.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel owned by [MainActivity].
 *
 * Reads the DataStore onboarding flag and student name; exposes a single
 * [uiState] that [MainActivity] waits on before rendering the NavHost so
 * there is never a flash of the wrong start destination.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    onboardingRepo: OnboardingRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        combine(
            onboardingRepo.isOnboardingComplete,
            onboardingRepo.studentName,
        ) { complete, name ->
            MainUiState(
                ready          = true,
                showOnboarding = !complete,
                studentName    = name,
            )
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )
}

/**
 * @property ready          `false` until the first DataStore emission arrives.
 * @property showOnboarding `true` when onboarding has not yet been completed.
 * @property studentName    The persisted student name (may be empty).
 */
data class MainUiState(
    val ready:          Boolean = false,
    val showOnboarding: Boolean = false,
    val studentName:    String  = "",
)
