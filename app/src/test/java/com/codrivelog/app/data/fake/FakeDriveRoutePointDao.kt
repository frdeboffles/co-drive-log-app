package com.codrivelog.app.data.fake

import com.codrivelog.app.data.db.DriveRoutePointDao
import com.codrivelog.app.data.model.DriveRoutePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDriveRoutePointDao : DriveRoutePointDao {
    private var nextId = 1L
    private val points = MutableStateFlow<List<DriveRoutePoint>>(emptyList())

    override suspend fun insert(point: DriveRoutePoint): Long {
        val id = if (point.id == 0L) nextId++ else point.id
        val toInsert = point.copy(id = id)
        points.value = points.value + toInsert
        return id
    }

    override fun getBySession(sessionId: Long): Flow<List<DriveRoutePoint>> =
        points.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.timestamp } }

    override suspend fun countBySession(sessionId: Long): Int =
        points.value.count { it.sessionId == sessionId }

    override fun getSessionIdsWithPoints(): Flow<List<Long>> =
        points.map { list -> list.map { it.sessionId }.distinct() }

    override suspend fun deleteBySession(sessionId: Long) {
        points.value = points.value.filterNot { it.sessionId == sessionId }
    }
}
