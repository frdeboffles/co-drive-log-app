package com.codrivelog.app.data.repository

import com.codrivelog.app.data.db.DriveRoutePointDao
import com.codrivelog.app.data.model.DriveRoutePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRouteRepository @Inject constructor(
    private val dao: DriveRoutePointDao,
) {
    suspend fun insert(point: DriveRoutePoint): Long = dao.insert(point)

    fun getBySession(sessionId: Long): Flow<List<DriveRoutePoint>> = dao.getBySession(sessionId)

    suspend fun countBySession(sessionId: Long): Int = dao.countBySession(sessionId)

    fun getSessionIdsWithPoints(): Flow<Set<Long>> =
        dao.getSessionIdsWithPoints().map { list -> list.toSet() }

    suspend fun deleteBySession(sessionId: Long) = dao.deleteBySession(sessionId)
}
