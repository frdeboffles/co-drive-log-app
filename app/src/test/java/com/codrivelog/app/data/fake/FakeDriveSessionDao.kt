package com.codrivelog.app.data.fake

import com.codrivelog.app.data.db.DriveSessionDao
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake implementation of [DriveSessionDao] for use in unit tests.
 *
 * Stores sessions in a [MutableStateFlow] so that [getAll] emits reactively
 * whenever the backing list changes — mirroring Room's observable behaviour
 * without touching any Android framework code.
 */
class FakeDriveSessionDao : DriveSessionDao {

    private var nextId = 1L
    private val _sessions = MutableStateFlow<List<DriveSession>>(emptyList())

    // ---- Queries ----

    override fun getAll(): Flow<List<DriveSession>> =
        _sessions.map { list ->
            list.sortedWith(compareByDescending<DriveSession> { it.date }
                .thenByDescending { it.startTime })
        }

    override suspend fun getTotalDrivingMinutes(): Int =
        _sessions.value.sumOf { it.totalMinutes }

    override suspend fun getTotalNightMinutes(): Int =
        _sessions.value.sumOf { it.nightMinutes }

    override suspend fun getById(id: Long): DriveSession? =
        _sessions.value.firstOrNull { it.id == id }

    // ---- Mutations ----

    override suspend fun insert(session: DriveSession): Long {
        val id = if (session.id == 0L) nextId++ else session.id
        val toInsert = session.copy(id = id)
        _sessions.value = _sessions.value
            .filterNot { it.id == id } + toInsert
        return id
    }

    override suspend fun update(session: DriveSession) {
        _sessions.value = _sessions.value.map { existing ->
            if (existing.id == session.id) session else existing
        }
    }

    override suspend fun delete(session: DriveSession) {
        _sessions.value = _sessions.value.filterNot { it.id == session.id }
    }
}
