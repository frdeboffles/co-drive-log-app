package com.codrivelog.app.ui.dashboard

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveSessionRepository

class DashboardViewModelTest {

    private val repository = mockk<DriveSessionRepository>()

    @Test
    fun `uiState totalHours sums session minutes correctly`() = runTest {
        every { repository.getAllSessions() } returns flowOf(
            listOf(
                DriveSession(epochMillis = 0L, totalMinutes = 60,  nightMinutes = 0,  supervisorId = 1),
                DriveSession(epochMillis = 0L, totalMinutes = 90,  nightMinutes = 30, supervisorId = 1),
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
        every { repository.getAllSessions() } returns flowOf(emptyList())

        val viewModel = DashboardViewModel(repository)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(0f, state.totalHours, 0.001f)
            assertEquals(0f, state.nightHours,  0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
