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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DriveRoutePointDaoTest {

    private lateinit var db: CoDriveLogDatabase
    private lateinit var sessionDao: DriveSessionDao
    private lateinit var routeDao: DriveRoutePointDao

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CoDriveLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.driveSessionDao()
        routeDao = db.driveRoutePointDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun insertSession(): Long {
        val date = LocalDate.of(2026, 3, 30)
        return sessionDao.insert(
            DriveSession(
                date = date,
                startTime = LocalDateTime.of(2026, 3, 30, 10, 0),
                endTime = LocalDateTime.of(2026, 3, 30, 10, 30),
                totalMinutes = 30,
                nightMinutes = 0,
                supervisorName = "Jane Doe",
                supervisorInitials = "JD",
                isManualEntry = false,
            )
        )
    }

    @Test
    fun insert_and_query_by_session_returns_points_in_timestamp_order() = runTest {
        val sessionId = insertSession()

        routeDao.insert(
            DriveRoutePoint(
                sessionId = sessionId,
                timestamp = LocalDateTime.of(2026, 3, 30, 10, 2),
                latitude = 39.7392,
                longitude = -104.9903,
                accuracyMeters = 15f,
            )
        )
        routeDao.insert(
            DriveRoutePoint(
                sessionId = sessionId,
                timestamp = LocalDateTime.of(2026, 3, 30, 10, 1),
                latitude = 39.7394,
                longitude = -104.9901,
                accuracyMeters = 12f,
            )
        )

        val points = routeDao.getBySession(sessionId).first()
        assertEquals(2, points.size)
        assertTrue(points[0].timestamp.isBefore(points[1].timestamp))
    }

    @Test
    fun count_by_session_returns_expected_count() = runTest {
        val sessionId = insertSession()
        routeDao.insert(
            DriveRoutePoint(
                sessionId = sessionId,
                timestamp = LocalDateTime.of(2026, 3, 30, 10, 0),
                latitude = 39.7392,
                longitude = -104.9903,
                accuracyMeters = 10f,
            )
        )
        routeDao.insert(
            DriveRoutePoint(
                sessionId = sessionId,
                timestamp = LocalDateTime.of(2026, 3, 30, 10, 1),
                latitude = 39.7393,
                longitude = -104.9902,
                accuracyMeters = 14f,
            )
        )

        assertEquals(2, routeDao.countBySession(sessionId))
    }

    @Test
    fun deleting_session_cascades_route_points() = runTest {
        val sessionId = insertSession()
        routeDao.insert(
            DriveRoutePoint(
                sessionId = sessionId,
                timestamp = LocalDateTime.of(2026, 3, 30, 10, 0),
                latitude = 39.7392,
                longitude = -104.9903,
                accuracyMeters = 10f,
            )
        )

        val session = sessionDao.getById(sessionId)!!
        sessionDao.delete(session)

        assertEquals(0, routeDao.countBySession(sessionId))
        assertTrue(routeDao.getBySession(sessionId).first().isEmpty())
    }

    @Test
    fun getSessionIdsWithPoints_returns_only_sessions_that_have_points() = runTest {
        val sessionA = insertSession()
        val sessionB = insertSession()

        routeDao.insert(
            DriveRoutePoint(
                sessionId = sessionA,
                timestamp = LocalDateTime.of(2026, 3, 30, 10, 0),
                latitude = 39.7392,
                longitude = -104.9903,
                accuracyMeters = 10f,
            )
        )

        val ids = routeDao.getSessionIdsWithPoints().first().toSet()
        assertTrue(ids.contains(sessionA))
        assertTrue(!ids.contains(sessionB))
    }
}
