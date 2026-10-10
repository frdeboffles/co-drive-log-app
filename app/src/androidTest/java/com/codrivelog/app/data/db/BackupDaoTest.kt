package com.codrivelog.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {

    private lateinit var db: CoDriveLogDatabase
    private lateinit var backupDao: BackupDao

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CoDriveLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupDao = db.backupDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun replaceAll_replaces_every_table_and_keeps_ids() = runTest {
        db.supervisorDao().insert(Supervisor(name = "Old", initials = "OL"))
        val oldSession = db.driveSessionDao().insert(session(id = 0))
        db.driveRoutePointDao().insert(point(id = 0, sessionId = oldSession))

        val incoming = DatabaseSnapshot(
            supervisors = listOf(Supervisor(7, "Jane Doe", "JD")),
            sessions    = listOf(session(id = 40), session(id = 41)),
            routePoints = listOf(point(id = 100, sessionId = 41), point(id = 101, sessionId = 41)),
        )

        backupDao.replaceAll(incoming) {}

        assertEquals(incoming, backupDao.snapshot())
    }

    @Test
    fun new_rows_after_replaceAll_get_ids_above_the_imported_ones() = runTest {
        backupDao.replaceAll(
            DatabaseSnapshot(emptyList(), listOf(session(id = 40)), emptyList()),
        ) {}

        val newId = db.driveSessionDao().insert(session(id = 0))

        assertEquals(41L, newId)
    }

    @Test
    fun replaceAll_keeps_existing_data_when_an_insert_fails() = runTest {
        val existingSession = db.driveSessionDao().insert(session(id = 0))
        val before = backupDao.snapshot()

        // The route point refers to a session that is not in the snapshot:
        // the foreign key fails and the whole transaction must roll back.
        val broken = DatabaseSnapshot(
            supervisors = listOf(Supervisor(1, "New", "NW")),
            sessions    = listOf(session(id = 50)),
            routePoints = listOf(point(id = 1, sessionId = 999)),
        )
        try {
            backupDao.replaceAll(broken) {}
            fail("Expected a foreign key failure")
        } catch (expected: SQLiteConstraintException) {
            // expected
        }

        assertEquals(before, backupDao.snapshot())
        assertEquals(existingSession, backupDao.getAllSessions().single().id)
    }

    @Test
    fun replaceAll_passes_the_current_rows_to_the_callback() = runTest {
        val existingId = db.driveSessionDao().insert(session(id = 0))
        var seen: DatabaseSnapshot? = null

        backupDao.replaceAll(DatabaseSnapshot(emptyList(), emptyList(), emptyList())) { current ->
            seen = current
        }

        assertEquals(listOf(existingId), seen?.sessions?.map { it.id })
        assertEquals(0, backupDao.countSessions())
    }

    @Test
    fun replaceAll_keeps_existing_data_when_the_callback_throws() = runTest {
        db.driveSessionDao().insert(session(id = 0))
        val before = backupDao.snapshot()

        try {
            backupDao.replaceAll(DatabaseSnapshot(emptyList(), listOf(session(id = 50)), emptyList())) {
                throw IllegalStateException("drive in progress")
            }
            fail("Expected the callback exception")
        } catch (expected: IllegalStateException) {
            // expected
        }

        assertEquals(before, backupDao.snapshot())
    }

    private fun session(id: Long) = DriveSession(
        id                 = id,
        date               = LocalDate.of(2026, 3, 30),
        startTime          = LocalDateTime.of(2026, 3, 30, 10, 0),
        endTime            = LocalDateTime.of(2026, 3, 30, 10, 30),
        totalMinutes       = 30,
        nightMinutes       = 0,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
        comments           = "Test",
    )

    private fun point(id: Long, sessionId: Long) = DriveRoutePoint(
        id             = id,
        sessionId      = sessionId,
        timestamp      = LocalDateTime.of(2026, 3, 30, 10, 2),
        latitude       = 39.7392,
        longitude      = -104.9903,
        accuracyMeters = 15f,
    )
}
