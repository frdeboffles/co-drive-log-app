package com.codrivelog.app.data.db

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codrivelog.app.BuildConfig
import com.codrivelog.app.backup.DatabaseBackup
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.util.NightRuleRecalculation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
    val MIGRATION_2_3: Migration = migration2To3(saveBackup = null)

    /**
     * [MIGRATION_2_3], first handing every row, as stored before the
     * migration, to [saveBackup]. The rows are marked as old-rule data, so
     * the backup file imports like a 1.1.0 backup.
     *
     * A failed backup does not stop the migration: stopping would leave the
     * database unopenable, and the migration deletes no rows.
     */
    fun migration2To3(saveBackup: ((DatabaseBackup) -> Unit)?): Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (saveBackup != null) {
                runCatching { saveBackup(readVersion2Backup(db)) }
                    .onFailure { e -> Log.w(TAG, "Backup before the night rule migration failed", e) }
            }
            addNightColumns(db)
            classifyNightMinutes(db, NightRuleRecalculation.candidateZones())
        }
    }

    /** Every row of a version 2 database, as a backup of old-rule data without a profile. */
    internal fun readVersion2Backup(db: SupportSQLiteDatabase): DatabaseBackup {
        val supervisors = db.query("SELECT id, name, initials FROM supervisors ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(Supervisor(c.getLong(0), c.getString(1), c.getString(2))) }
        }
        val sessions = db.query(
            """
            SELECT id, date, startTime, endTime, totalMinutes, nightMinutes,
                   supervisorName, supervisorInitials, comments, isManualEntry
            FROM drive_sessions ORDER BY id
            """.trimIndent()
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DriveSession(
                            id                 = c.getLong(0),
                            date               = LocalDate.parse(c.getString(1)),
                            startTime          = LocalDateTime.parse(c.getString(2)),
                            endTime            = LocalDateTime.parse(c.getString(3)),
                            totalMinutes       = c.getInt(4),
                            nightMinutes       = c.getInt(5),
                            supervisorName     = c.getString(6),
                            supervisorInitials = c.getString(7),
                            comments           = if (c.isNull(8)) null else c.getString(8),
                            isManualEntry      = c.getInt(9) != 0,
                        )
                    )
                }
            }
        }
        val routePoints = db.query(
            "SELECT id, sessionId, timestamp, latitude, longitude, accuracyMeters FROM drive_route_points ORDER BY id"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DriveRoutePoint(
                            id             = c.getLong(0),
                            sessionId      = c.getLong(1),
                            timestamp      = LocalDateTime.parse(c.getString(2)),
                            latitude       = c.getDouble(3),
                            longitude      = c.getDouble(4),
                            accuracyMeters = c.getFloat(5),
                        )
                    )
                }
            }
        }
        return DatabaseBackup(
            createdAt       = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            appVersion      = "before ${BuildConfig.VERSION_NAME}",
            profile         = null,
            data            = DatabaseSnapshot(supervisors, sessions, routePoints),
            legacyNightRule = true,
        )
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
