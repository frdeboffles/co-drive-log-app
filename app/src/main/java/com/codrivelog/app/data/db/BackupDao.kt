package com.codrivelog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor

/**
 * All rows of the database, read in one transaction so the three tables
 * are consistent with each other.
 */
data class DatabaseSnapshot(
    val supervisors: List<Supervisor>,
    val sessions: List<DriveSession>,
    val routePoints: List<DriveRoutePoint>,
)

/**
 * Whole-database access for the full backup export and import.
 *
 * Abstract class rather than interface so that [snapshot] and [replaceAll]
 * can carry a body and still run inside a Room transaction.
 */
@Dao
abstract class BackupDao {

    // ---- Queries ----

    @Query("SELECT * FROM supervisors ORDER BY id ASC")
    abstract suspend fun getAllSupervisors(): List<Supervisor>

    @Query("SELECT * FROM drive_sessions ORDER BY id ASC")
    abstract suspend fun getAllSessions(): List<DriveSession>

    @Query("SELECT * FROM drive_route_points ORDER BY id ASC")
    abstract suspend fun getAllRoutePoints(): List<DriveRoutePoint>

    @Query("SELECT COUNT(*) FROM supervisors")
    abstract suspend fun countSupervisors(): Int

    @Query("SELECT COUNT(*) FROM drive_sessions")
    abstract suspend fun countSessions(): Int

    @Query("SELECT COUNT(*) FROM drive_route_points")
    abstract suspend fun countRoutePoints(): Int

    @Transaction
    open suspend fun snapshot(): DatabaseSnapshot =
        DatabaseSnapshot(
            supervisors = getAllSupervisors(),
            sessions    = getAllSessions(),
            routePoints = getAllRoutePoints(),
        )

    // ---- Mutations ----

    @Query("DELETE FROM drive_route_points")
    abstract suspend fun deleteAllRoutePoints()

    @Query("DELETE FROM drive_sessions")
    abstract suspend fun deleteAllSessions()

    @Query("DELETE FROM supervisors")
    abstract suspend fun deleteAllSupervisors()

    @Insert
    abstract suspend fun insertSupervisors(supervisors: List<Supervisor>)

    @Insert
    abstract suspend fun insertSessions(sessions: List<DriveSession>)

    @Insert
    abstract suspend fun insertRoutePoints(routePoints: List<DriveRoutePoint>)

    /**
     * Replaces every row in the database with [snapshot].
     *
     * Runs in one transaction. [beforeReplace] receives the current rows and
     * runs inside that transaction, so no other write can land between what
     * it sees and the replacement: a drive saved meanwhile waits and is
     * inserted after the import. If [beforeReplace] or any insert throws,
     * the existing data is kept unchanged. Row ids are preserved so route
     * points stay linked to their sessions.
     */
    @Transaction
    open suspend fun replaceAll(
        snapshot: DatabaseSnapshot,
        beforeReplace: suspend (current: DatabaseSnapshot) -> Unit,
    ) {
        beforeReplace(snapshot())
        deleteAllRoutePoints()
        deleteAllSessions()
        deleteAllSupervisors()
        insertSupervisors(snapshot.supervisors)
        insertSessions(snapshot.sessions)
        insertRoutePoints(snapshot.routePoints)
    }
}
