package com.codrivelog.app.ui.dashboard

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveSessionRepository
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<DriveSessionRepository>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Convenience builder so tests don't need to repeat every field. */
    private fun session(totalMinutes: Int, nightMinutes: Int) = DriveSession(
        date = LocalDate.of(2025, 6, 15),
        startTime = LocalDateTime.of(2025, 6, 15, 9, 0),
        endTime = LocalDateTime.of(2025, 6, 15, 10, 30),
        totalMinutes = totalMinutes,
        nightMinutes = nightMinutes,
        supervisorName = "Jane Doe",
        supervisorInitials = "JD",
    )

    @Test
    fun `uiState totalHours sums session minutes correctly`() = runTest {
        every { repository.getAll() } returns flowOf(
            listOf(
                session(totalMinutes = 60, nightMinutes = 0),
                session(totalMinutes = 90, nightMinutes = 30),
            )
        )

        val viewModel = DashboardViewModel(repository)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2.5f, state.totalHours, 0.001f)
            assertEquals(0.5f, state.nightHours,  0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState is zeroed when no sessions exist`() = runTest {
        every { repository.getAll() } returns flowOf(emptyList())

        val viewModel = DashboardViewModel(repository)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0f, state.totalHours, 0.001f)
            assertEquals(0f, state.nightHours,  0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
