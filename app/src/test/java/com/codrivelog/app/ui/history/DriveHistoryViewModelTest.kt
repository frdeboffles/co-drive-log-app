package com.codrivelog.app.ui.history

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeDriveRoutePointDao
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.util.DriveMinutes
import com.codrivelog.app.util.NightRuleRecalculation
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
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class DriveHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dao:       FakeDriveSessionDao
    private lateinit var repo:      DriveSessionRepository
    private lateinit var supervisorRepo: SupervisorRepository
    private lateinit var routeRepo: DriveRouteRepository
    private lateinit var routeDao: FakeDriveRoutePointDao
    private lateinit var viewModel: DriveHistoryViewModel
    private lateinit var originalTimeZone: TimeZone

    @BeforeEach
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
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
        TimeZone.setDefault(originalTimeZone)
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

    @Test
    fun `update keeps daytime edited session at zero night minutes`() = runTest {
        val original = DriveSession(
            id = 20L,
            date = LocalDate.of(2025, 6, 10),
            startTime = LocalDateTime.of(2025, 6, 10, 10, 50),
            endTime = LocalDateTime.of(2025, 6, 10, 10, 51),
            totalMinutes = 1,
            nightMinutes = 0,
            supervisorName = "Jane Doe",
            supervisorInitials = "JD",
            comments = null,
            isManualEntry = false,
        )
        dao.insert(original)

        viewModel.update(
            session = original,
            date = LocalDate.of(2025, 6, 10),
            startTime = LocalTime.of(10, 15),
            endTime = LocalTime.of(10, 51),
            supervisorName = "Jane Doe",
            supervisorInitials = "JD",
            comments = "",
        )

        val updated = dao.getById(20L)
        assertEquals(36, updated?.totalMinutes)
        assertEquals(0, updated?.nightMinutes)
    }

    // ---- Night minutes on edit (review finding 2) ----

    @Test
    fun `editing only the supervisor keeps the night minutes and source`() = runTest {
        val original = winterEveningDrive(nightMinutes = 45, source = NightSource.MANUAL)
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(17, 0), LocalTime.of(18, 0), "Pat Parent", "pp", "")

        val updated = dao.getById(original.id)!!
        assertEquals("PP", updated.supervisorInitials)
        assertEquals(45, updated.nightMinutes)
        assertEquals(NightSource.MANUAL, updated.nightSource)
    }

    @Test
    fun `editing the times of a sun drive recalculates at its stored location`() = runTest {
        val grandJunction = NightRuleRecalculation.Location(39.0639, -108.5506)
        val original = winterEveningDrive(nightMinutes = 46, source = NightSource.SUN, location = grandJunction)
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(16, 30), LocalTime.of(18, 0), "Jane Doe", "JD", "")

        val updated = dao.getById(original.id)!!
        val expected = nightAt(
            LocalDateTime.of(2025, 12, 21, 16, 30), LocalDateTime.of(2025, 12, 21, 18, 0),
            grandJunction, ZoneId.of("America/Denver"),
        )
        assertEquals(expected, updated.nightMinutes)
        assertEquals(NightSource.SUN, updated.nightSource)
        assertEquals(grandJunction.latitude, updated.nightLatitude)
    }

    @Test
    fun `editing the times of a manual-switch drive keeps its value capped to the new duration`() = runTest {
        val original = winterEveningDrive(nightMinutes = 45, source = NightSource.MANUAL)
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(17, 0), LocalTime.of(17, 30), "Jane Doe", "JD", "")

        val updated = dao.getById(original.id)!!
        assertEquals(30, updated.nightMinutes)
        assertEquals(NightSource.MANUAL, updated.nightSource)
    }

    @Test
    fun `editing the times of an unknown-source drive recalculates at Denver`() = runTest {
        val original = winterEveningDrive(nightMinutes = 37, source = NightSource.UNKNOWN)
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(17, 0), LocalTime.of(17, 45), "Jane Doe", "JD", "")

        val updated = dao.getById(original.id)!!
        assertEquals(45, updated.nightMinutes)
        assertEquals(NightSource.SUN, updated.nightSource)
        assertEquals(NightRuleRecalculation.DEFAULT_LOCATION.latitude, updated.nightLatitude)
    }

    @Test
    fun `editing the times of an unknown-source drive with a route uses its route location`() = runTest {
        // Review finding 3: the real location must not be replaced by Denver.
        val grandJunction = NightRuleRecalculation.Location(39.0639, -108.5506)
        val original = winterEveningDrive(nightMinutes = 37, source = NightSource.UNKNOWN)
        dao.insert(original)
        routeDao.insert(
            DriveRoutePoint(
                sessionId = original.id,
                timestamp = LocalDateTime.of(2025, 12, 22, 1, 0),
                latitude = grandJunction.latitude,
                longitude = grandJunction.longitude,
                accuracyMeters = 50f,
            )
        )

        viewModel.update(original, original.date, LocalTime.of(16, 30), LocalTime.of(18, 0), "Jane Doe", "JD", "")

        val updated = dao.getById(original.id)!!
        assertEquals(NightSource.SUN, updated.nightSource)
        assertEquals(grandJunction.latitude, updated.nightLatitude)
        assertEquals(
            nightAt(
                LocalDateTime.of(2025, 12, 21, 16, 30), LocalDateTime.of(2025, 12, 21, 18, 0),
                grandJunction, ZoneId.of("America/Denver"),
            ),
            updated.nightMinutes,
        )
    }

    @Test
    fun `confirming the same times keeps seconds, total and night of a timed drive`() = runTest {
        // Review finding 5: the picker drops seconds; the same minute is no change.
        val original = winterEveningDrive(nightMinutes = 45, source = NightSource.MANUAL).copy(
            startTime    = LocalDateTime.of(2025, 12, 21, 17, 0, 42),
            endTime      = LocalDateTime.of(2025, 12, 21, 18, 0, 13),
            totalMinutes = 59,
        )
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(17, 0), LocalTime.of(18, 0), "Jane Doe", "JD", "new comment")

        val updated = dao.getById(original.id)!!
        assertEquals(original.copy(comments = "new comment"), updated)
    }

    @Test
    fun `a comment edit keeps a drive that ends at an earlier clock time across fall-back`() = runTest {
        // Review finding 1: 1:50 MDT to 1:10 MST, 20 minutes; the edit must
        // not rebuild the end on the next day.
        val original = winterEveningDrive(nightMinutes = 20, source = NightSource.SUN).copy(
            date         = LocalDate.of(2025, 11, 2),
            startTime    = LocalDateTime.of(2025, 11, 2, 1, 50, 5),
            endTime      = LocalDateTime.of(2025, 11, 2, 1, 10, 9),
            totalMinutes = 20,
        )
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(1, 50), LocalTime.of(1, 10), "Jane Doe", "JD", "note")

        assertEquals(original.copy(comments = "note"), dao.getById(original.id))
    }

    @Test
    fun `a comment edit keeps a drive shorter than one minute`() = runTest {
        val original = winterEveningDrive(nightMinutes = 0, source = NightSource.SUN).copy(
            startTime    = LocalDateTime.of(2025, 12, 21, 10, 0, 10),
            endTime      = LocalDateTime.of(2025, 12, 21, 10, 0, 40),
            totalMinutes = 0,
        )
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(10, 0), LocalTime.of(10, 0), "Jane Doe", "JD", "note")

        assertEquals(original.copy(comments = "note"), dao.getById(original.id))
    }

    @Test
    fun `a comment save with the stored times and their seconds changes nothing else`() = runTest {
        // Review finding 1: the dialog used to pass the stored times with
        // seconds; that must not count as a time change.
        val original = winterEveningDrive(nightMinutes = 45, source = NightSource.UNKNOWN).copy(
            startTime    = LocalDateTime.of(2025, 12, 21, 17, 0, 42),
            endTime      = LocalDateTime.of(2025, 12, 21, 18, 0, 13),
            totalMinutes = 59,
        )
        dao.insert(original)

        viewModel.update(
            original, original.date,
            original.startTime.toLocalTime(), original.endTime.toLocalTime(),
            "Jane Doe", "JD", "note",
        )

        assertEquals(original.copy(comments = "note"), dao.getById(original.id))
    }

    @Test
    fun `an edit whose times cannot be resolved is refused`() = runTest {
        // Review finding 2: moving a fall-back drive (1:50 MDT to 1:10 MST) to
        // an ordinary date leaves an end before its start: keep the row.
        val original = winterEveningDrive(nightMinutes = 20, source = NightSource.SUN, location = NightRuleRecalculation.DEFAULT_LOCATION).copy(
            date         = LocalDate.of(2025, 11, 2),
            startTime    = LocalDateTime.of(2025, 11, 2, 1, 50),
            endTime      = LocalDateTime.of(2025, 11, 2, 1, 10),
            totalMinutes = 20,
        )
        dao.insert(original)

        viewModel.update(original, LocalDate.of(2025, 11, 5), LocalTime.of(1, 50), LocalTime.of(1, 10), "Jane Doe", "JD", "note")

        assertEquals(original, dao.getById(original.id))
    }

    @Test
    fun `changing only the date moves a short drive without stretching it`() = runTest {
        // Review finding 2: a drive under one minute must not become 24 hours.
        val original = winterEveningDrive(nightMinutes = 0, source = NightSource.SUN, location = NightRuleRecalculation.DEFAULT_LOCATION).copy(
            startTime    = LocalDateTime.of(2025, 12, 21, 10, 0, 10),
            endTime      = LocalDateTime.of(2025, 12, 21, 10, 0, 40),
            totalMinutes = 0,
        )
        dao.insert(original)

        viewModel.update(original, LocalDate.of(2025, 12, 23), LocalTime.of(10, 0), LocalTime.of(10, 0), "Jane Doe", "JD", "")

        val updated = dao.getById(original.id)!!
        assertEquals(LocalDateTime.of(2025, 12, 23, 10, 0, 10), updated.startTime)
        assertEquals(LocalDateTime.of(2025, 12, 23, 10, 0, 40), updated.endTime)
        assertEquals(0, updated.totalMinutes)
    }

    @Test
    fun `editing the times of an unknown drive with no location recalculates it`() = runTest {
        // Review finding 1: a drive the migration could not attribute is
        // recalculated by a time edit, as in 1.1.0.
        val original = winterEveningDrive(nightMinutes = 0, source = NightSource.UNKNOWN).copy(
            startTime = LocalDateTime.of(2025, 12, 21, 15, 30),
            endTime   = LocalDateTime.of(2025, 12, 21, 16, 30),
        )
        dao.insert(original)

        viewModel.update(original, original.date, LocalTime.of(15, 30), LocalTime.of(19, 30), "Jane Doe", "JD", "")

        val updated = dao.getById(original.id)!!
        assertTrue(updated.nightMinutes in 170..172, "about 2 h 51 min after the 4:39 pm sunset, got ${updated.nightMinutes}")
        assertEquals(NightSource.SUN, updated.nightSource)
    }

    private fun winterEveningDrive(
        nightMinutes: Int,
        source: NightSource,
        location: NightRuleRecalculation.Location? = null,
    ) = DriveSession(
        id                 = 30L,
        date               = LocalDate.of(2025, 12, 21),
        startTime          = LocalDateTime.of(2025, 12, 21, 17, 0),
        endTime            = LocalDateTime.of(2025, 12, 21, 18, 0),
        totalMinutes       = 60,
        nightMinutes       = nightMinutes,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
        isManualEntry      = false,
        nightSource        = source,
        nightLatitude      = location?.latitude,
        nightLongitude     = location?.longitude,
        timeZone           = "America/Denver",
    )

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

    private fun nightAt(start: LocalDateTime, end: LocalDateTime, location: NightRuleRecalculation.Location, zone: ZoneId): Int? =
        DriveMinutes.fromLocal(start, end, zone, location)?.night
}
