package com.codrivelog.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Room integration tests for [DriveSessionDao].
 *
 * Each test runs against an in-memory database so there is no persistent state
 * between tests.  The database is built with `allowMainThreadQueries()` so we
 * do not need to dispatch coroutines to a background thread inside the test.
 */
@RunWith(AndroidJUnit4::class)
class DriveSessionDaoTest {

    private lateinit var db: CoDriveLogDatabase
    private lateinit var dao: DriveSessionDao

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CoDriveLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.driveSessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ---- Helpers ----

    private fun session(
        date: LocalDate = LocalDate.of(2025, 6, 15),
        startTime: LocalDateTime = LocalDateTime.of(2025, 6, 15, 9, 0),
        endTime: LocalDateTime = LocalDateTime.of(2025, 6, 15, 10, 30),
        totalMinutes: Int = 90,
        nightMinutes: Int = 0,
        supervisorName: String = "Jane Doe",
        supervisorInitials: String = "JD",
        comments: String? = null,
        isManualEntry: Boolean = false,
    ) = DriveSession(
        date = date,
        startTime = startTime,
        endTime = endTime,
        totalMinutes = totalMinutes,
        nightMinutes = nightMinutes,
        supervisorName = supervisorName,
        supervisorInitials = supervisorInitials,
        comments = comments,
        isManualEntry = isManualEntry,
    )

    // ---- insert / getById ----

    @Test
    fun insertAndGetById_returnsInsertedSession() = runTest {
        val id = dao.insert(session(supervisorName = "Alice"))
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Alice", retrieved!!.supervisorName)
    }

    @Test
    fun getById_unknownId_returnsNull() = runTest {
        assertNull(dao.getById(999L))
    }

    // ---- getAll ordering ----

    @Test
    fun getAll_orderedByDateDescThenStartTimeDesc() = runTest {
        val older = session(
            date = LocalDate.of(2025, 5, 1),
            startTime = LocalDateTime.of(2025, 5, 1, 8, 0),
            endTime = LocalDateTime.of(2025, 5, 1, 9, 0),
            totalMinutes = 60,
        )
        val newer = session(
            date = LocalDate.of(2025, 6, 15),
            startTime = LocalDateTime.of(2025, 6, 15, 14, 0),
            endTime = LocalDateTime.of(2025, 6, 15, 15, 30),
            totalMinutes = 90,
        )
        dao.insert(older)
        dao.insert(newer)

        val sessions = dao.getAll().first()

        assertEquals(2, sessions.size)
        // Most-recent date should be first
        assertEquals(LocalDate.of(2025, 6, 15), sessions[0].date)
        assertEquals(LocalDate.of(2025, 5, 1), sessions[1].date)
    }

    @Test
    fun getAll_sameDate_orderedByStartTimeDesc() = runTest {
        val morning = session(
            startTime = LocalDateTime.of(2025, 6, 15, 8, 0),
            endTime = LocalDateTime.of(2025, 6, 15, 9, 0),
            totalMinutes = 60,
        )
        val afternoon = session(
            startTime = LocalDateTime.of(2025, 6, 15, 15, 0),
            endTime = LocalDateTime.of(2025, 6, 15, 16, 0),
            totalMinutes = 60,
        )
        dao.insert(morning)
        dao.insert(afternoon)

        val sessions = dao.getAll().first()
        assertEquals(2, sessions.size)
        // Later start time first
        assertEquals(LocalDateTime.of(2025, 6, 15, 15, 0), sessions[0].startTime)
        assertEquals(LocalDateTime.of(2025, 6, 15, 8, 0), sessions[1].startTime)
    }

    @Test
    fun getAll_emptyTable_returnsEmptyList() = runTest {
        assertTrue(dao.getAll().first().isEmpty())
    }

    // ---- update ----

    @Test
    fun update_changesPersistedValues() = runTest {
        val id = dao.insert(session(totalMinutes = 60, comments = null))
        val original = dao.getById(id)!!
        val updated = original.copy(totalMinutes = 120, comments = "Highway practice")
        dao.update(updated)

        val retrieved = dao.getById(id)!!
        assertEquals(120, retrieved.totalMinutes)
        assertEquals("Highway practice", retrieved.comments)
    }

    // ---- delete ----

    @Test
    fun delete_removesSessionFromTable() = runTest {
        val id = dao.insert(session())
        val inserted = dao.getById(id)!!
        dao.delete(inserted)
        assertNull(dao.getById(id))
    }

    @Test
    fun delete_onlyRemovesTargetRow() = runTest {
        val id1 = dao.insert(session(totalMinutes = 30))
        val id2 = dao.insert(session(totalMinutes = 60))
        val first = dao.getById(id1)!!
        dao.delete(first)

        assertNull(dao.getById(id1))
        assertNotNull(dao.getById(id2))
    }

    // ---- getTotalDrivingMinutes ----

    @Test
    fun getTotalDrivingMinutes_emptyTable_returnsZero() = runTest {
        assertEquals(0, dao.getTotalDrivingMinutes())
    }

    @Test
    fun getTotalDrivingMinutes_sumsAllSessions() = runTest {
        dao.insert(session(totalMinutes = 60))
        dao.insert(session(totalMinutes = 90))
        dao.insert(session(totalMinutes = 30))
        assertEquals(180, dao.getTotalDrivingMinutes())
    }

    @Test
    fun getTotalDrivingMinutes_afterDelete_updatesCorrectly() = runTest {
        val id = dao.insert(session(totalMinutes = 60))
        dao.insert(session(totalMinutes = 40))
        dao.delete(dao.getById(id)!!)
        assertEquals(40, dao.getTotalDrivingMinutes())
    }

    // ---- getTotalNightMinutes ----

    @Test
    fun getTotalNightMinutes_emptyTable_returnsZero() = runTest {
        assertEquals(0, dao.getTotalNightMinutes())
    }

    @Test
    fun getTotalNightMinutes_sumsNightMinutesOnly() = runTest {
        dao.insert(session(totalMinutes = 90, nightMinutes = 30))
        dao.insert(session(totalMinutes = 60, nightMinutes = 0))
        dao.insert(session(totalMinutes = 45, nightMinutes = 45))
        assertEquals(75, dao.getTotalNightMinutes())
    }

    // ---- TypeConverter round-trip ----

    @Test
    fun localDateTypeConverter_roundTrip() = runTest {
        val expectedDate = LocalDate.of(2024, 12, 31)
        val id = dao.insert(session(date = expectedDate))
        val retrieved = dao.getById(id)!!
        assertEquals(expectedDate, retrieved.date)
    }

    @Test
    fun localDateTimeTypeConverter_roundTrip() = runTest {
        val start = LocalDateTime.of(2024, 12, 31, 23, 59, 0)
        val end   = LocalDateTime.of(2025, 1, 1, 0, 30, 0)
        val id = dao.insert(session(startTime = start, endTime = end))
        val retrieved = dao.getById(id)!!
        assertEquals(start, retrieved.startTime)
        assertEquals(end,   retrieved.endTime)
    }

    // ---- nullable comments ----

    @Test
    fun insert_nullComments_roundTrips() = runTest {
        val id = dao.insert(session(comments = null))
        assertNull(dao.getById(id)!!.comments)
    }

    @Test
    fun insert_nonNullComments_roundTrips() = runTest {
        val id = dao.insert(session(comments = "Night highway run"))
        assertEquals("Night highway run", dao.getById(id)!!.comments)
    }

    // ---- isManualEntry ----

    @Test
    fun insert_isManualEntry_true_roundTrips() = runTest {
        val id = dao.insert(session(isManualEntry = true))
        assertTrue(dao.getById(id)!!.isManualEntry)
    }

    // ---- Flow emissions ----

    @Test
    fun getAll_emitsUpdatedListAfterInsert() = runTest {
        // Initial emission
        val initial = dao.getAll().first()
        assertTrue(initial.isEmpty())

        dao.insert(session(totalMinutes = 45))

        val afterInsert = dao.getAll().first()
        assertEquals(1, afterInsert.size)
    }
}
