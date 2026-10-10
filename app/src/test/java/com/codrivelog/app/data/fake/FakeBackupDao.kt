package com.codrivelog.app.data.fake

import com.codrivelog.app.data.db.BackupDao
import com.codrivelog.app.data.db.DatabaseSnapshot
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor

/**
 * In-memory fake of [BackupDao] for unit tests.
 *
 * [replaceAll] mimics the Room transaction: the tables change only when
 * the callback and the whole replacement succeed. [onTransactionStart]
 * runs first, so a test can change state at the moment the transaction
 * begins.
 */
class FakeBackupDao(
    var supervisors: List<Supervisor> = emptyList(),
    var sessions: List<DriveSession> = emptyList(),
    var routePoints: List<DriveRoutePoint> = emptyList(),
) : BackupDao() {

    var replaceCount = 0
        private set

    var onTransactionStart: suspend () -> Unit = {}

    override suspend fun getAllSupervisors() = supervisors
    override suspend fun getAllSessions() = sessions
    override suspend fun getAllRoutePoints() = routePoints

    override suspend fun countSupervisors() = supervisors.size
    override suspend fun countSessions() = sessions.size
    override suspend fun countRoutePoints() = routePoints.size

    override suspend fun deleteAllRoutePoints() { routePoints = emptyList() }
    override suspend fun deleteAllSessions() { sessions = emptyList() }
    override suspend fun deleteAllSupervisors() { supervisors = emptyList() }

    override suspend fun insertSupervisors(supervisors: List<Supervisor>) { this.supervisors += supervisors }
    override suspend fun insertSessions(sessions: List<DriveSession>) { this.sessions += sessions }
    override suspend fun insertRoutePoints(routePoints: List<DriveRoutePoint>) { this.routePoints += routePoints }

    override suspend fun replaceAll(
        snapshot: DatabaseSnapshot,
        beforeReplace: suspend (current: DatabaseSnapshot) -> Unit,
    ) {
        onTransactionStart()
        beforeReplace(snapshot())
        replaceCount++
        supervisors = snapshot.supervisors
        sessions = snapshot.sessions
        routePoints = snapshot.routePoints
    }
}
