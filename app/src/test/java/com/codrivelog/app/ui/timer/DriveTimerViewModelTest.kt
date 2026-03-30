package com.codrivelog.app.ui.timer

import app.cash.turbine.test
import com.codrivelog.app.service.DriveTimerRepository
import com.codrivelog.app.service.TimerState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Unit tests for [DriveTimerViewModel].
 *
 * Uses MockK to stub [DriveTimerRepository.timerState] and a fake
 * [android.content.Context] stub for service-launch calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveTimerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val timerStateFlow = MutableStateFlow<TimerState>(TimerState.Idle)

    private val timerRepository = mockk<DriveTimerRepository>(relaxed = true) {
        every { timerState } returns timerStateFlow
    }

    // A relaxed Context mock — we only verify that startForegroundService /
    // startService are called with the right action strings.
    private val context = mockk<android.content.Context>(relaxed = true) {
        every { packageName } returns "com.codrivelog.app"
        every { applicationContext } returns this
    }

    private lateinit var viewModel: DriveTimerViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DriveTimerViewModel(timerRepository, context)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Idle state mapping ----

    @Test
    fun `initial state is Idle`() = runTest {
        viewModel.uiState.test {
            assertEquals(DriveTimerUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Saving state maps to Idle`() = runTest {
        timerStateFlow.value = TimerState.Saving
        viewModel.uiState.test {
            assertEquals(DriveTimerUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Running state mapping ----

    @Test
    fun `Running state maps to Active with formatted elapsed time`() = runTest {
        timerStateFlow.value = runningState(elapsedSeconds = 3661, nightSeconds = 600)
        viewModel.uiState.test {
            val state = awaitItem() as DriveTimerUiState.Active
            assertEquals("1:01:01", state.elapsedFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Running state maps night seconds to H-MM format`() = runTest {
        timerStateFlow.value = runningState(nightSeconds = 5400)
        viewModel.uiState.test {
            val state = awaitItem() as DriveTimerUiState.Active
            assertEquals("1:30", state.nightFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Active currentlyNight reflects service state`() = runTest {
        timerStateFlow.value = runningState(currentlyNight = true)
        viewModel.uiState.test {
            val state = awaitItem() as DriveTimerUiState.Active
            assertTrue(state.currentlyNight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Active hasGpsFix true when latitude is non-null`() = runTest {
        timerStateFlow.value = runningState(latitude = 39.7, longitude = -104.9)
        viewModel.uiState.test {
            val state = awaitItem() as DriveTimerUiState.Active
            assertTrue(state.hasGpsFix)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Active hasGpsFix false when latitude is null`() = runTest {
        timerStateFlow.value = runningState(latitude = null, longitude = null)
        viewModel.uiState.test {
            val state = awaitItem() as DriveTimerUiState.Active
            assertFalse(state.hasGpsFix)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- State transitions ----

    @Test
    fun `transitions from Idle to Active when service emits Running`() = runTest {
        viewModel.uiState.test {
            assertEquals(DriveTimerUiState.Idle, awaitItem())   // initial

            timerStateFlow.value = runningState()
            assertTrue(awaitItem() is DriveTimerUiState.Active) // transition

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transitions from Active back to Idle when service emits Idle`() = runTest {
        timerStateFlow.value = runningState()
        viewModel.uiState.test {
            assertTrue(awaitItem() is DriveTimerUiState.Active)

            timerStateFlow.value = TimerState.Idle
            assertEquals(DriveTimerUiState.Idle, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- startDrive / stopDrive ----

    @Test
    fun `startDrive calls startForegroundService`() {
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setAction(any()) } returns mockk(relaxed = true)
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        viewModel.startDrive("Jane Doe", "JD")
        verify { context.startForegroundService(any()) }

        unmockkConstructor(android.content.Intent::class)
    }

    @Test
    fun `stopDrive calls startService`() {
        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setAction(any()) } returns mockk(relaxed = true)

        viewModel.stopDrive()
        verify { context.startService(any()) }

        unmockkConstructor(android.content.Intent::class)
    }

    // ---- Helpers ----

    private fun runningState(
        elapsedSeconds: Long    = 0L,
        nightSeconds:   Long    = 0L,
        currentlyNight: Boolean = false,
        latitude:       Double? = null,
        longitude:      Double? = null,
    ) = TimerState.Running(
        startTime      = LocalDateTime.of(2025, 6, 21, 10, 0),
        elapsedSeconds = elapsedSeconds,
        nightSeconds   = nightSeconds,
        currentlyNight = currentlyNight,
        latitude       = latitude,
        longitude      = longitude,
    )
}
