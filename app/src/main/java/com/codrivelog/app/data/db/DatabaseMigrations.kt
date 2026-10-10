package com.codrivelog.app.data.db

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.util.NightRuleRecalculation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object DatabaseMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS drive_route_points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    timestamp TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    accuracyMeters REAL NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES drive_sessions(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_drive_route_points_sessionId_timestamp
                ON drive_route_points(sessionId, timestamp)
                """.trimIndent()
            )
        }
    }

    /**
     * Version 3 records where night minutes come from (`nightSource`,
     * `nightLatitude`, `nightLongitude`) and the zone of the local times
     * (`timeZone`), and recalculates stored night minutes with the
     * sunset-to-sunrise rule, which replaced a rule with a one-hour buffer.
     *
     * Only drives whose stored value is recognized as a sun calculation are
     * recalculated; the others keep their value (see [com.codrivelog.app.util.NightSourceClassifier]).
     * Room runs this once, in a transaction, before the app reads any data.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addNightColumns(db)
            classifyNightMinutes(db, NightRuleRecalculation.candidateZones())
        }
    }

    internal fun addNightColumns(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN nightSource TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN nightLatitude REAL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN nightLongitude REAL")
        db.execSQL("ALTER TABLE drive_sessions ADD COLUMN timeZone TEXT")
    }

    internal fun classifyNightMinutes(database: SupportSQLiteDatabase, zones: List<ZoneId>) {
        // One row per drive: point count and latest point, not every point.
        val routes = HashMap<Long, NightRuleRecalculation.RouteSummary>()
        database.query(
            """
            SELECT p.sessionId, c.n, p.timestamp, p.latitude, p.longitude
            FROM drive_route_points p
            JOIN (
                SELECT sessionId, COUNT(*) AS n, MAX(timestamp) AS ts
                FROM drive_route_points GROUP BY sessionId
            ) c ON c.sessionId = p.sessionId AND c.ts = p.timestamp
            """.trimIndent()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                // A summary that cannot be read is left out: the drive then
                // looks like it had no route points, which only makes it less
                // likely to be recalculated.
                runCatching {
                    NightRuleRecalculation.RouteSummary(
                        count         = cursor.getInt(1),
                        lastTimestamp = LocalDateTime.parse(cursor.getString(2)),
                        lastLocation  = NightRuleRecalculation.Location(cursor.getDouble(3), cursor.getDouble(4)),
                    )
                }.getOrNull()?.let { routes.putIfAbsent(cursor.getLong(0), it) }
            }
        }

        val sessions = mutableListOf<DriveSession>()
        database.query(
            "SELECT id, date, startTime, endTime, totalMinutes, nightMinutes, isManualEntry FROM drive_sessions"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                // A row that cannot be read keeps its value and the UNKNOWN
                // default: a thrown exception here would leave the database
                // unopenable.
                runCatching {
                    DriveSession(
                        id                 = id,
                        date               = LocalDate.parse(cursor.getString(1)),
                        startTime          = LocalDateTime.parse(cursor.getString(2)),
                        endTime            = LocalDateTime.parse(cursor.getString(3)),
                        totalMinutes       = cursor.getInt(4),
                        nightMinutes       = cursor.getInt(5),
                        supervisorName     = "",
                        supervisorInitials = "",
                        isManualEntry      = cursor.getInt(6) != 0,
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Drive $id could not be read; night minutes kept", e)
                }.getOrNull()?.let(sessions::add)
            }
        }

        val result = NightRuleRecalculation.recalculateSessions(sessions, routes, zones)

        result.sessions.forEach { session ->
            database.execSQL(
                """
                UPDATE drive_sessions
                SET totalMinutes = ?, nightMinutes = ?, nightSource = ?, nightLatitude = ?, nightLongitude = ?, timeZone = ?
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any?>(
                    session.totalMinutes,
                    session.nightMinutes,
                    session.nightSource.name,
                    session.nightLatitude,
                    session.nightLongitude,
                    session.timeZone,
                    session.id,
                ),
            )
        }
        Log.i(TAG, "Night rule migration: ${result.summary}; kept drives ${result.keptIds}")
    }

    private const val TAG = "DatabaseMigrations"
}
