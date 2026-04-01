package com.codrivelog.app.ui.history

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeDriveRoutePointDao
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DriveHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dao:       FakeDriveSessionDao
    private lateinit var repo:      DriveSessionRepository
    private lateinit var supervisorRepo: SupervisorRepository
    private lateinit var routeRepo: DriveRouteRepository
    private lateinit var routeDao: FakeDriveRoutePointDao
    private lateinit var viewModel: DriveHistoryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao       = FakeDriveSessionDao()
        repo      = DriveSessionRepository(dao)
        supervisorRepo = SupervisorRepository(FakeSupervisorDao())
        routeDao = FakeDriveRoutePointDao()
        routeRepo = DriveRouteRepository(routeDao)
        viewModel = DriveHistoryViewModel(repo, supervisorRepo, routeRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun `initial state has empty session list`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitItem().sessions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Reactive updates ----

    @Test
    fun `inserting a session appears in uiState`() = runTest {
        viewModel.uiState.test {
            awaitItem() // empty initial

            dao.insert(makeSession(id = 1L, date = LocalDate.of(2025, 6, 21)))

            val next = awaitItem()
            assertEquals(1, next.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sessions are ordered most-recent first`() = runTest {
        dao.insert(makeSession(id = 1L, date = LocalDate.of(2025, 6, 1)))
        dao.insert(makeSession(id = 2L, date = LocalDate.of(2025, 6, 21)))

        viewModel.uiState.test {
            val list = awaitItem().sessions
            assertEquals(LocalDate.of(2025, 6, 21), list[0].date)
            assertEquals(LocalDate.of(2025, 6, 1),  list[1].date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Delete ----

    @Test
    fun `delete removes session from uiState`() = runTest {
        val session = makeSession(id = 1L, date = LocalDate.of(2025, 6, 21))
        dao.insert(session)

        viewModel.uiState.test {
            // Collect until we see the non-empty state, then delete.
            var state = awaitItem()
            while (state.sessions.isEmpty()) state = awaitItem()
            assertEquals(1, state.sessions.size)

            viewModel.delete(session)
            assertTrue(awaitItem().sessions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ui state exposes session ids that have route points`() = runTest {
        val session = makeSession(id = 1L, date = LocalDate.of(2025, 6, 21))
        dao.insert(session)
        routeDao.insert(
            DriveRoutePoint(
                sessionId = 1L,
                timestamp = LocalDateTime.of(2025, 6, 21, 9, 5),
                latitude = 39.7392,
                longitude = -104.9903,
                accuracyMeters = 12f,
            )
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.sessionIdsWithRoute.contains(1L))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `buildGoogleMapsDirectionsUrl returns null when fewer than two points`() = runTest {
        val session = makeSession(id = 10L, date = LocalDate.of(2025, 6, 22))
        dao.insert(session)

        routeDao.insert(
            DriveRoutePoint(
                sessionId = 10L,
                timestamp = LocalDateTime.of(2025, 6, 22, 9, 0),
                latitude = 39.7392,
                longitude = -104.9903,
                accuracyMeters = 8f,
            )
        )

        val url = viewModel.buildGoogleMapsDirectionsUrl(10L)
        assertNull(url)
    }

    @Test
    fun `buildGoogleMapsDirectionsUrl includes origin and destination`() = runTest {
        val session = makeSession(id = 11L, date = LocalDate.of(2025, 6, 23))
        dao.insert(session)

        routeDao.insert(
            DriveRoutePoint(
                sessionId = 11L,
                timestamp = LocalDateTime.of(2025, 6, 23, 9, 0),
                latitude = 39.7000,
                longitude = -104.9000,
                accuracyMeters = 10f,
            )
        )
        routeDao.insert(
            DriveRoutePoint(
                sessionId = 11L,
                timestamp = LocalDateTime.of(2025, 6, 23, 9, 1),
                latitude = 39.7100,
                longitude = -104.9100,
                accuracyMeters = 11f,
            )
        )
        routeDao.insert(
            DriveRoutePoint(
                sessionId = 11L,
                timestamp = LocalDateTime.of(2025, 6, 23, 9, 2),
                latitude = 39.7200,
                longitude = -104.9200,
                accuracyMeters = 9f,
            )
        )

        val url = viewModel.buildGoogleMapsDirectionsUrl(11L)
        assertTrue(url != null)
        assertTrue(url!!.contains("origin=39.700000,-104.900000"))
        assertTrue(url.contains("destination=39.720000,-104.920000"))
        assertTrue(url.contains("travelmode=driving"))
    }

    @Test
    fun `getRoutePath returns points sorted by timestamp`() = runTest {
        val session = makeSession(id = 12L, date = LocalDate.of(2025, 6, 24))
        dao.insert(session)

        routeDao.insert(
            DriveRoutePoint(
                sessionId = 12L,
                timestamp = LocalDateTime.of(2025, 6, 24, 9, 2),
                latitude = 39.7200,
                longitude = -104.9200,
                accuracyMeters = 9f,
            )
        )
        routeDao.insert(
            DriveRoutePoint(
                sessionId = 12L,
                timestamp = LocalDateTime.of(2025, 6, 24, 9, 0),
                latitude = 39.7000,
                longitude = -104.9000,
                accuracyMeters = 10f,
            )
        )

        val route = viewModel.getRoutePath(12L)
        assertEquals(2, route.size)
        assertEquals(39.7000, route[0].latitude)
        assertEquals(-104.9000, route[0].longitude)
        assertEquals(39.7200, route[1].latitude)
        assertEquals(-104.9200, route[1].longitude)
    }

    // ---- Helpers ----

    private fun makeSession(id: Long, date: LocalDate) = DriveSession(
        id                 = id,
        date               = date,
        startTime          = LocalDateTime.of(date, java.time.LocalTime.of(9, 0)),
        endTime            = LocalDateTime.of(date, java.time.LocalTime.of(10, 0)),
        totalMinutes       = 60,
        nightMinutes       = 0,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
        isManualEntry      = false,
    )
}
