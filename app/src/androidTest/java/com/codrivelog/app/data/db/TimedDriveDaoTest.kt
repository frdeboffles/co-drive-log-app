package com.codrivelog.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TimedDriveDaoTest {

    private lateinit var db: CoDriveLogDatabase

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CoDriveLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun drive_and_route_are_saved_together_and_linked() = runTest {
        val id = db.timedDriveDao().insertDriveWithRoute(session(), listOf(point(0), point(0)))

        assertEquals(1, db.driveSessionDao().getAll().first().size)
        assertEquals(listOf(id, id), db.driveRoutePointDao().getBySession(id).first().map { it.sessionId })
    }

    @Test
    fun a_failed_route_insert_saves_nothing() = runTest {
        // Review finding 1: the drive must not stay without its route, or
        // adding it by hand afterwards would duplicate it.
        try {
            // Two points with the same id: the second insert fails.
            db.timedDriveDao().insertDriveWithRoute(session(), listOf(point(7), point(7)))
            fail("Expected the duplicate point to fail")
        } catch (expected: Exception) {
            // expected
        }

        assertTrue(db.driveSessionDao().getAll().first().isEmpty())
    }

    private fun session() = DriveSession(
        date = LocalDate.of(2026, 10, 10),
        startTime = LocalDateTime.of(2026, 10, 10, 17, 0),
        endTime = LocalDateTime.of(2026, 10, 10, 17, 30),
        totalMinutes = 30,
        nightMinutes = 0,
        supervisorName = "Jane Doe",
        supervisorInitials = "JD",
    )

    private fun point(id: Long) = DriveRoutePoint(
        id = id,
        sessionId = 0,
        timestamp = LocalDateTime.of(2026, 10, 10, 23, 5),
        latitude = 39.7392,
        longitude = -104.9903,
        accuracyMeters = 4f,
    )
}
