package com.codrivelog.app.data.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.backup.BackupJson
import com.codrivelog.app.backup.DatabaseBackup
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.util.NightRuleRecalculation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class NightRuleMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CoDriveLogDatabase::class.java,
    )

    private val zones = listOf(ZoneId.of("America/Denver"))

    /** Same work as [DatabaseMigrations.MIGRATION_2_3], with a fixed zone so the result does not depend on the device. */
    private val migrationInDenver = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            DatabaseMigrations.addNightColumns(db)
            DatabaseMigrations.classifyNightMinutes(db, zones)
        }
    }

    // 5-6 pm MST on 2025-12-21, sunset ≈ 4:39 pm: 21 night minutes under the
    // 1.0.3 one-hour buffer, 60 under sunset to sunrise.
    private val sessions = listOf(
        // Manual entry: always recalculated.
        session(1, nightMinutes = 21, isManualEntry = true),
        // Timed, no route points, 45 minutes no calculation reproduces: kept.
        session(2, nightMinutes = 45),
        // Timed with GPS (two fixes): recalculated; the final point gives the offset.
        session(3, nightMinutes = 21),
        // Only the final fix, 45 minutes no calculation reproduces: kept.
        session(4, nightMinutes = 45),
        // 1.1.0 timer drive across the fall-back hour, 1:50 MDT to 1:10 MST:
        // the old timer stored a 0 total. Review finding 1: it is 20 minutes.
        session(
            5, nightMinutes = 0, totalMinutes = 0,
            start = LocalDateTime.of(2025, 11, 2, 1, 50, 0, 123_000_000),
            end = LocalDateTime.of(2025, 11, 2, 1, 10, 0, 456_000_000),
        ),
    )

    private val points = listOf(
        point(10, sessionId = 3, timestamp = LocalDateTime.of(2025, 12, 22, 0, 0, 1)),
        point(11, sessionId = 3, timestamp = LocalDateTime.of(2025, 12, 22, 1, 0)),
        point(12, sessionId = 4, timestamp = LocalDateTime.of(2025, 12, 22, 1, 0)),
        point(13, sessionId = 5, timestamp = LocalDateTime.of(2025, 11, 2, 7, 52, 0, 789_000_000)),
        point(14, sessionId = 5, timestamp = LocalDateTime.of(2025, 11, 2, 8, 10, 0, 456_000_000)),
    )

    @Test
    fun migration_recalculates_sun_values_and_keeps_the_others() {
        val db = migrate()

        assertEquals(
            mapOf(
                1L to Row(60, 60, "SUN", 39.7392, "America/Denver"),
                2L to Row(60, 45, "UNKNOWN", null, null),
                3L to Row(60, 60, "SUN", 39.7392, "America/Denver"),
                4L to Row(60, 45, "UNKNOWN", null, null),
                5L to Row(20, 20, "SUN", 39.7392, "America/Denver"),
            ),
            rowsById(db),
        )
    }

    @Test
    fun migration_and_backup_import_give_the_same_row_for_the_same_drive() {
        val migrated = rowsById(migrate())

        // The import path: NightRuleRecalculation on the drives as a 1.1.0 backup holds them.
        val imported = NightRuleRecalculation.recalculateSessions(sessions, NightRuleRecalculation.RouteSummary.of(points), zones)
            .sessions.associate { it.id to Row(it.totalMinutes, it.nightMinutes, it.nightSource.name, it.nightLatitude, it.timeZone) }

        assertEquals(imported, migrated)
        migrated.values.forEach { assertTrue("night ${it.night} > total ${it.total}", it.night <= it.total) }
    }

    @Test
    fun an_unreadable_row_is_kept_and_the_migration_does_not_fail() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            insert(db, session(1, nightMinutes = 21, isManualEntry = true))
            db.execSQL(
                """
                INSERT INTO drive_sessions (id, date, startTime, endTime, totalMinutes, nightMinutes,
                    supervisorName, supervisorInitials, comments, isManualEntry)
                VALUES (2, '2025-12-21', 'not-a-date', '2025-12-21T18:00:00', 60, 7, 'Jane Doe', 'JD', NULL, 1)
                """.trimIndent()
            )
        }

        val rows = rowsById(helper.runMigrationsAndValidate(DB_NAME, 3, true, migrationInDenver))

        assertEquals(Row(60, 60, "SUN", 39.7392, "America/Denver"), rows[1L])
        assertEquals(Row(60, 7, "UNKNOWN", null, null), rows[2L])
    }

    @Test
    fun the_migration_backs_up_every_row_before_changing_it() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            sessions.forEach { insert(db, it) }
            points.forEach { insert(db, it) }
            db.execSQL("INSERT INTO supervisors (id, name, initials) VALUES (7, 'Jane Doe', 'JD')")
        }
        var backup: DatabaseBackup? = null

        helper.runMigrationsAndValidate(DB_NAME, 3, true, DatabaseMigrations.migration2To3 { backup = it })

        val saved = backup!!
        assertEquals(sessions, saved.data.sessions)
        assertEquals(points, saved.data.routePoints)
        assertEquals(listOf(Supervisor(7, "Jane Doe", "JD")), saved.data.supervisors)
        assertTrue(saved.legacyNightRule)

        // The file it writes can be read back by Import backup, as old-rule data.
        val bytes = ByteArrayOutputStream().also { BackupJson.write(saved, it) }.toByteArray()
        val reread = BackupJson.read { ByteArrayInputStream(bytes) }
        assertEquals(sessions, reread.data.sessions)
        assertTrue(reread.legacyNightRule)
    }

    @Test
    fun a_failed_backup_does_not_stop_the_migration() {
        helper.createDatabase(DB_NAME, 2).use { db -> insert(db, session(1, nightMinutes = 21, isManualEntry = true)) }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 3, true,
            DatabaseMigrations.migration2To3 { throw IllegalStateException("Downloads unavailable") },
        )

        assertEquals(Row(60, 60, "SUN", 39.7392, "America/Denver"), rowsById(db)[1L])
    }

    @Test
    fun real_migration_produces_a_valid_version_3_schema() {
        helper.createDatabase(DB_NAME, 2).use { db -> insert(db, session(1, nightMinutes = 21, isManualEntry = true)) }

        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, DatabaseMigrations.MIGRATION_2_3)

        assertEquals(setOf(1L), rowsById(db).keys)
    }

    // ---- Helpers ----

    private data class Row(val total: Int, val night: Int, val source: String, val latitude: Double?, val zone: String?)

    private fun migrate(): SupportSQLiteDatabase {
        helper.createDatabase(DB_NAME, 2).use { db ->
            sessions.forEach { insert(db, it) }
            points.forEach { insert(db, it) }
        }
        return helper.runMigrationsAndValidate(DB_NAME, 3, true, migrationInDenver)
    }

    private fun session(
        id: Long,
        nightMinutes: Int,
        isManualEntry: Boolean = false,
        totalMinutes: Int = 60,
        start: LocalDateTime = LocalDateTime.of(2025, 12, 21, 17, 0),
        end: LocalDateTime = LocalDateTime.of(2025, 12, 21, 18, 0),
    ) = DriveSession(
        id = id, date = start.toLocalDate(), startTime = start, endTime = end,
        totalMinutes = totalMinutes, nightMinutes = nightMinutes,
        supervisorName = "Jane Doe", supervisorInitials = "JD", isManualEntry = isManualEntry,
    )

    private fun point(id: Long, sessionId: Long, timestamp: LocalDateTime) = DriveRoutePoint(
        id = id, sessionId = sessionId, timestamp = timestamp,
        latitude = 39.7392, longitude = -104.9903, accuracyMeters = 5f,
    )

    private fun insert(db: SupportSQLiteDatabase, s: DriveSession) {
        db.execSQL(
            """
            INSERT INTO drive_sessions
                (id, date, startTime, endTime, totalMinutes, nightMinutes,
                 supervisorName, supervisorInitials, comments, isManualEntry)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
            """.trimIndent(),
            arrayOf<Any>(
                s.id, s.date.toString(), s.startTime.toString(), s.endTime.toString(),
                s.totalMinutes, s.nightMinutes, s.supervisorName, s.supervisorInitials,
                if (s.isManualEntry) 1 else 0,
            ),
        )
    }

    private fun insert(db: SupportSQLiteDatabase, p: DriveRoutePoint) {
        db.execSQL(
            "INSERT INTO drive_route_points (id, sessionId, timestamp, latitude, longitude, accuracyMeters) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(p.id, p.sessionId, p.timestamp.toString(), p.latitude, p.longitude, p.accuracyMeters),
        )
    }

    private fun rowsById(db: SupportSQLiteDatabase): Map<Long, Row> =
        db.query("SELECT id, totalMinutes, nightMinutes, nightSource, nightLatitude, timeZone FROM drive_sessions ORDER BY id").use { c ->
            buildMap {
                while (c.moveToNext()) {
                    put(c.getLong(0), Row(c.getInt(1), c.getInt(2), c.getString(3), if (c.isNull(4)) null else c.getDouble(4), c.getString(5)))
                }
            }
        }

    private companion object {
        const val DB_NAME = "night-rule-migration-test"
    }
}
