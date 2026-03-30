package com.codrivelog.app.ui.export

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dao:       FakeDriveSessionDao
    private lateinit var repo:      DriveSessionRepository
    private lateinit var supervisorRepo: SupervisorRepository
    private lateinit var viewModel: ExportViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao       = FakeDriveSessionDao()
        repo      = DriveSessionRepository(dao)
        supervisorRepo = SupervisorRepository(FakeSupervisorDao())
        viewModel = ExportViewModel(repo, supervisorRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun `initial state has zero counts`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.sessionCount)
            assertEquals(0f, state.totalHours)
            assertEquals(0f, state.nightHours)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Reactive updates ----

    @Test
    fun `session count increments when sessions are inserted`() = runTest {
        viewModel.uiState.test {
            awaitItem()                                         // initial zero

            dao.insert(makeSession(totalMinutes = 60, nightMinutes = 30))
            assertEquals(1, awaitItem().sessionCount)

            dao.insert(makeSession(totalMinutes = 60, nightMinutes = 30))
            assertEquals(2, awaitItem().sessionCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `total hours aggregates all sessions`() = runTest {
        // 60 min + 90 min = 150 min = 2.5 hrs
        dao.insert(makeSession(totalMinutes = 60,  nightMinutes = 0))
        dao.insert(makeSession(totalMinutes = 90,  nightMinutes = 0))

        viewModel.uiState.test {
            assertEquals(2.5f, awaitItem().totalHours, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `night hours aggregates all night minutes`() = runTest {
        // 30 min + 60 min = 90 min = 1.5 hrs
        dao.insert(makeSession(totalMinutes = 60, nightMinutes = 30))
        dao.insert(makeSession(totalMinutes = 90, nightMinutes = 60))

        viewModel.uiState.test {
            assertEquals(1.5f, awaitItem().nightHours, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ui state exposes supervisors for PDF signature picker`() = runTest {
        supervisorRepo.insert(Supervisor(name = "Jane Doe", initials = "JD"))
        supervisorRepo.insert(Supervisor(name = "Alex Roe", initials = "AR"))

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.supervisors.size)
            assertEquals("Alex Roe", state.supervisors[0].name)
            assertEquals("Jane Doe", state.supervisors[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Helpers ----

    private var nextId = 1L
    private fun makeSession(totalMinutes: Int, nightMinutes: Int): DriveSession {
        val date = LocalDate.of(2025, 6, 21)
        return DriveSession(
            id                 = nextId++,
            date               = date,
            startTime          = LocalDateTime.of(date, LocalTime.of(9, 0)),
            endTime            = LocalDateTime.of(date, LocalTime.of(9, 0)).plusMinutes(totalMinutes.toLong()),
            totalMinutes       = totalMinutes,
            nightMinutes       = nightMinutes,
            supervisorName     = "Jane Doe",
            supervisorInitials = "JD",
            isManualEntry      = false,
        )
    }
}
