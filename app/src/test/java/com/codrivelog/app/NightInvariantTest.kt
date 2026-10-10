package com.codrivelog.app

import com.codrivelog.app.data.fake.FakeDriveRoutePointDao
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.data.repository.DriveRouteRepository
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.service.timedDriveMinutes
import com.codrivelog.app.ui.entry.ManualEntryViewModel
import com.codrivelog.app.ui.history.DriveHistoryViewModel
import com.codrivelog.app.util.NightRuleRecalculation
import com.codrivelog.app.util.NightSourceClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * One rule on every path that stores a drive: night minutes never exceed
 * total minutes, and the total is the real duration of the typed times.
 * The cases are the ones daylight saving and midnight make hard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NightInvariantTest {

    data class Case(val name: String, val date: LocalDate, val start: LocalTime, val end: LocalTime, val realMinutes: Int) {
        override fun toString() = name
    }

    companion object {
        private val denver = ZoneId.of("America/Denver")

        @JvmStatic
        fun cases() = listOf(
            Case("winter evening", LocalDate.of(2025, 12, 21), LocalTime.of(16, 0), LocalTime.of(18, 0), 120),
            Case("summer morning", LocalDate.of(2025, 6, 21), LocalTime.of(5, 0), LocalTime.of(6, 30), 90),
            Case("across midnight", LocalDate.of(2025, 12, 21), LocalTime.of(23, 0), LocalTime.of(1, 0), 120),
            Case("fall-back, ends earlier on the clock", LocalDate.of(2025, 11, 2), LocalTime.of(1, 50), LocalTime.of(1, 10), 20),
            Case("through the repeated hour", LocalDate.of(2025, 11, 2), LocalTime.of(0, 30), LocalTime.of(2, 30), 180),
            Case("start in the spring gap, short", LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), LocalTime.of(3, 10), 10),
            Case("start in the spring gap, longer", LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), LocalTime.of(3, 40), 40),
            Case("start in the spring gap, long", LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), LocalTime.of(6, 0), 180),
            Case("across the spring change", LocalDate.of(2026, 3, 8), LocalTime.of(1, 30), LocalTime.of(3, 30), 60),
        )
    }

    private lateinit var originalZone: TimeZone
    private lateinit var sessionDao: FakeDriveSessionDao
    private lateinit var routeDao: FakeDriveRoutePointDao

    @BeforeEach
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sessionDao = FakeDriveSessionDao()
        routeDao = FakeDriveRoutePointDao()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(originalZone)
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `manual entry`(case: Case) = runTest {
        val vm = ManualEntryViewModel(DriveSessionRepository(sessionDao), SupervisorRepository(FakeSupervisorDao()))
        vm.save(case.date, case.start, case.end, "Jane Doe", "JD", "")

        assertDrive(case, sessionDao.getAll().first().single())
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `history edit`(case: Case) = runTest {
        val original = DriveSession(
            id = 1, date = case.date.minusDays(3),
            startTime = case.date.minusDays(3).atTime(9, 0), endTime = case.date.minusDays(3).atTime(9, 30),
            totalMinutes = 30, nightMinutes = 0, supervisorName = "Jane Doe", supervisorInitials = "JD",
            nightSource = NightSource.UNKNOWN,
        )
        sessionDao.insert(original)
        val vm = DriveHistoryViewModel(DriveSessionRepository(sessionDao), SupervisorRepository(FakeSupervisorDao()), DriveRouteRepository(routeDao))

        vm.update(original, case.date, case.start, case.end, "Jane Doe", "JD", "")

        assertDrive(case, sessionDao.getById(1)!!)
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `migration and backup import`(case: Case) {
        // Both use NightRuleRecalculation.recalculateSessions; a stored 1.1.0
        // manual entry is always recalculated, total included.
        val (start, end) = com.codrivelog.app.util.DriveMinutes.typedInterval(case.date, case.start, case.end, denver)
        val stored = DriveSession(
            id = 1, date = case.date, startTime = start, endTime = end,
            totalMinutes = 0, nightMinutes = 0, supervisorName = "Jane Doe", supervisorInitials = "JD",
            isManualEntry = true,
        )

        val migrated = NightRuleRecalculation.recalculateSessions(listOf(stored), emptyMap(), listOf(denver)).sessions.single()

        assertDrive(case, migrated)
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun `timer`(case: Case) {
        val (start, end) = com.codrivelog.app.util.DriveMinutes.typedInterval(case.date, case.start, case.end, denver)
        val startInstant = start.atZone(denver).withEarlierOffsetAtOverlap().toInstant()
        val endInstant = startInstant.plusSeconds(case.realMinutes * 60L)

        val (total, night) = timedDriveMinutes(startInstant, endInstant, NightRuleRecalculation.DEFAULT_LOCATION, 0)

        assertEquals(case.realMinutes, total)
        assertTrue(night <= total, "night $night > total $total")
    }

    private fun assertDrive(case: Case, drive: DriveSession) {
        assertEquals(case.realMinutes, drive.totalMinutes, "total")
        assertTrue(drive.nightMinutes <= drive.totalMinutes, "night ${drive.nightMinutes} > total ${drive.totalMinutes}")
    }
}
