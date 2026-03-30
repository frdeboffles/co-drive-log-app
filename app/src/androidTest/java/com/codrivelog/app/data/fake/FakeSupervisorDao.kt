package com.codrivelog.app.data.fake

import com.codrivelog.app.data.db.SupervisorDao
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [SupervisorDao] substitute for instrumented Compose UI tests.
 */
class FakeSupervisorDao(initial: List<Supervisor> = emptyList()) : SupervisorDao {

    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L
    private val _supervisors = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Supervisor>> =
        _supervisors.map { list -> list.sortedBy { it.name } }

    override suspend fun getById(id: Long): Supervisor? =
        _supervisors.value.firstOrNull { it.id == id }

    override suspend fun insert(supervisor: Supervisor): Long {
        val id       = if (supervisor.id == 0L) nextId++ else supervisor.id
        val toInsert = supervisor.copy(id = id)
        _supervisors.value = _supervisors.value.filterNot { it.id == id } + toInsert
        return id
    }

    override suspend fun delete(supervisor: Supervisor) {
        _supervisors.value = _supervisors.value.filterNot { it.id == supervisor.id }
    }
}
