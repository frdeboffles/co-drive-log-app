package com.codrivelog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Transaction
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession

/** Saves a timed drive with its route in one transaction. */
@Dao
abstract class TimedDriveDao {

    @Insert
    protected abstract suspend fun insertSession(session: DriveSession): Long

    @Insert
    protected abstract suspend fun insertRoutePoints(points: List<DriveRoutePoint>)

    /**
     * Inserts [session] and [routePoints] together: if any insert fails,
     * nothing is saved, so the drive cannot exist without its route and a
     * retry or a manual entry cannot duplicate it.
     *
     * @return The new session id.
     */
    @Transaction
    open suspend fun insertDriveWithRoute(session: DriveSession, routePoints: List<DriveRoutePoint>): Long {
        val sessionId = insertSession(session)
        insertRoutePoints(routePoints.map { it.copy(sessionId = sessionId) })
        return sessionId
    }
}
