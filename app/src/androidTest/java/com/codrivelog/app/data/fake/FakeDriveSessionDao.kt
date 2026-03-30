package com.codrivelog.app.data.fake

import com.codrivelog.app.data.db.DriveSessionDao
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DriveSessionDao] substitute for instrumented Compose UI tests.
 *
 * Mirrors [com.codrivelog.app.data.fake.FakeDriveSessionDao] from the unit-test
 * source set; kept separate so the two source sets stay independent.
 */
class FakeDriveSessionDao : DriveSessionDao {

    private var nextId = 1L
    val sessions = MutableStateFlow<List<DriveSession>>(emptyList())

    constructor(initial: List<DriveSession> = emptyList()) {
        if (initial.isNotEmpty()) {
            sessions.value = initial
            nextId = initial.maxOf { it.id } + 1L
        }
    }

    override fun getAll(): Flow<List<DriveSession>> =
        sessions.map { list ->
            list.sortedWith(
                compareByDescending<DriveSession> { it.date }
                    .thenByDescending { it.startTime }
            )
        }

    override suspend fun getTotalDrivingMinutes(): Int =
        sessions.value.sumOf { it.totalMinutes }

    override suspend fun getTotalNightMinutes(): Int =
        sessions.value.sumOf { it.nightMinutes }

    override suspend fun getById(id: Long): DriveSession? =
        sessions.value.firstOrNull { it.id == id }

    override suspend fun insert(session: DriveSession): Long {
        val id       = if (session.id == 0L) nextId++ else session.id
        val toInsert = session.copy(id = id)
        sessions.value = sessions.value.filterNot { it.id == id } + toInsert
        return id
    }

    override suspend fun update(session: DriveSession) {
        sessions.value = sessions.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun delete(session: DriveSession) {
        sessions.value = sessions.value.filterNot { it.id == session.id }
    }
}
