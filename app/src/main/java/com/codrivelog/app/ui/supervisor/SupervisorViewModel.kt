package com.codrivelog.app.ui.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.repository.SupervisorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the supervisor management screen.
 *
 * Exposes the full list of [Supervisor] records and provides [add]/[delete]
 * actions. Validates that name and initials are non-blank before inserting.
 *
 * @param repository Repository for supervisor persistence.
 */
@HiltViewModel
class SupervisorViewModel @Inject constructor(
    private val repository: SupervisorRepository,
) : ViewModel() {

    /** UI state containing the full supervisor list. */
    val uiState: StateFlow<SupervisorUiState> = repository
        .getAll()
        .map { SupervisorUiState(supervisors = it) }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = SupervisorUiState(),
        )

    /**
     * Insert a new supervisor if both fields are non-blank.
     *
     * @param name     Full name of the supervisor.
     * @param initials Short initials (1–4 chars).
     * @return `true` if the insert was attempted, `false` if validation failed.
     */
    fun add(name: String, initials: String): Boolean {
        val trimName     = name.trim()
        val trimInitials = initials.trim()
        if (trimName.isBlank() || trimInitials.isBlank()) return false
        viewModelScope.launch {
            repository.insert(Supervisor(name = trimName, initials = trimInitials))
        }
        return true
    }

    /**
     * Permanently delete [supervisor].
     *
     * @param supervisor The [Supervisor] to remove.
     */
    fun delete(supervisor: Supervisor) {
        viewModelScope.launch { repository.delete(supervisor) }
    }
}

/**
 * Immutable UI state for the supervisor management screen.
 *
 * @property supervisors Alphabetically ordered list of all supervisors.
 */
data class SupervisorUiState(
    val supervisors: List<Supervisor> = emptyList(),
)
