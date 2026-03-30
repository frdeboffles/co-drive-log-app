package com.codrivelog.app.ui.entry

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.fake.FakeSupervisorDao
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ManualEntryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var driveDao:       FakeDriveSessionDao
    private lateinit var supervisorDao:  FakeSupervisorDao
    private lateinit var driveRepo:      DriveSessionRepository
    private lateinit var supervisorRepo: SupervisorRepository
    private lateinit var viewModel:      ManualEntryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        driveDao       = FakeDriveSessionDao()
        supervisorDao  = FakeSupervisorDao()
        driveRepo      = DriveSessionRepository(driveDao)
        supervisorRepo = SupervisorRepository(supervisorDao)
        viewModel      = ManualEntryViewModel(driveRepo, supervisorRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Validation errors ----

    @Test
    fun `blank supervisor name produces ValidationError on SUPERVISOR_NAME`() = runTest {
        viewModel.save(
            date               = LocalDate.now(),
            startTime          = LocalTime.of(10, 0),
            endTime            = LocalTime.of(11, 0),
            supervisorName     = "  ",
            supervisorInitials = "JD",
            comments           = "",
        )
        val state = viewModel.saveState.value
        assertEquals(SaveState.ValidationError(Field.SUPERVISOR_NAME), state)
    }

    @Test
    fun `blank initials produces ValidationError on SUPERVISOR_INITIALS`() = runTest {
        viewModel.save(
            date               = LocalDate.now(),
            startTime          = LocalTime.of(10, 0),
            endTime            = LocalTime.of(11, 0),
            supervisorName     = "Jane Doe",
            supervisorInitials = "",
            comments           = "",
        )
        assertEquals(SaveState.ValidationError(Field.SUPERVISOR_INITIALS), viewModel.saveState.value)
    }

    @Test
    fun `end time equal to start time is treated as midnight-crossing and saved`() = runTest {
        // When endTime == startTime the VM treats it as a 24-hour midnight-crossing drive.
        val t = LocalTime.of(10, 0)
        viewModel.saveState.test {
            assertEquals(SaveState.Idle, awaitItem())
            viewModel.save(
                date               = LocalDate.of(2025, 6, 21),
                startTime          = t,
                endTime            = t,
                supervisorName     = "Jane Doe",
                supervisorInitials = "JD",
                comments           = "",
            )
            assertEquals(SaveState.Saving, awaitItem())
            assertEquals(SaveState.Saved,  awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `end time before start time is treated as midnight-crossing drive and saved`() = runTest {
        viewModel.saveState.test {
            assertEquals(SaveState.Idle, awaitItem())   // initial

            viewModel.save(
                date               = LocalDate.of(2025, 6, 21),
                startTime          = LocalTime.of(23, 0),
                endTime            = LocalTime.of(1, 0),  // next day
                supervisorName     = "Jane Doe",
                supervisorInitials = "JD",
                comments           = "",
            )

            assertEquals(SaveState.Saving, awaitItem())
            assertEquals(SaveState.Saved,  awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Happy path ----

    @Test
    fun `valid save transitions through Saving to Saved`() = runTest {
        viewModel.saveState.test {
            assertEquals(SaveState.Idle, awaitItem())

            viewModel.save(
                date               = LocalDate.of(2025, 6, 21),
                startTime          = LocalTime.of(9, 0),
                endTime            = LocalTime.of(10, 30),
                supervisorName     = "Jane Doe",
                supervisorInitials = "JD",
                comments           = "Highway driving",
            )

            assertEquals(SaveState.Saving, awaitItem())
            assertEquals(SaveState.Saved,  awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved session appears in repository`() = runTest {
        viewModel.save(
            date               = LocalDate.of(2025, 6, 21),
            startTime          = LocalTime.of(9, 0),
            endTime            = LocalTime.of(10, 30),
            supervisorName     = "Jane Doe",
            supervisorInitials = "JD",
            comments           = "",
        )
        val sessions = driveDao.getAll()
        sessions.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertTrue(list.first().isManualEntry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved session has correct total minutes`() = runTest {
        viewModel.save(
            date               = LocalDate.of(2025, 6, 21),
            startTime          = LocalTime.of(9, 0),
            endTime            = LocalTime.of(10, 30),
            supervisorName     = "Jane Doe",
            supervisorInitials = "JD",
            comments           = "",
        )
        val sessions = driveDao.getAll()
        sessions.test {
            assertEquals(90, awaitItem().first().totalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- resetState ----

    @Test
    fun `resetState returns to Idle after Saved`() = runTest {
        viewModel.save(
            date               = LocalDate.of(2025, 6, 21),
            startTime          = LocalTime.of(9, 0),
            endTime            = LocalTime.of(10, 0),
            supervisorName     = "Jane Doe",
            supervisorInitials = "JD",
            comments           = "",
        )
        viewModel.resetState()
        assertEquals(SaveState.Idle, viewModel.saveState.value)
    }
}
