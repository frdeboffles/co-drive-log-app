package com.codrivelog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.onboarding.OnboardingRepository
import com.codrivelog.app.ui.dashboard.CircularHoursCard
import com.codrivelog.app.ui.dashboard.DashboardScreen
import com.codrivelog.app.ui.dashboard.DashboardUiState
import com.codrivelog.app.ui.dashboard.DashboardViewModel
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Compose UI tests for dashboard-related composables.
 *
 * These tests use the stateless composable variants so they work without
 * a Hilt component graph or a real database.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---- CircularHoursCard ----

    @Test
    fun circularHoursCard_showsLabel() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                CircularHoursCard(
                    label   = "Total Hours",
                    icon    = Icons.Default.DirectionsCar,
                    current = 10f,
                    goal    = 50f,
                )
            }
        }
        composeTestRule.onNodeWithText("Total Hours").assertIsDisplayed()
    }

    @Test
    fun circularHoursCard_showsProgressFraction() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                CircularHoursCard(
                    label   = "Night Hours",
                    icon    = Icons.Default.NightsStay,
                    current = 4f,
                    goal    = 10f,
                )
            }
        }
        // The progress_fraction string: "4.0 / 10 hrs"
        composeTestRule.onNodeWithText("4.0 / 10 hrs").assertIsDisplayed()
    }

    @Test
    fun circularHoursCard_zeroProgress_displaysZeroFraction() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                CircularHoursCard(
                    label   = "Total Hours",
                    icon    = Icons.Default.DirectionsCar,
                    current = 0f,
                    goal    = 50f,
                )
            }
        }
        composeTestRule.onNodeWithText("0.0 / 50 hrs").assertIsDisplayed()
    }

    // ---- RecentDrivesCard (via DashboardScreen) ----

    @Test
    fun dashboard_noRecentDrives_showsEmptyLabel() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                DashboardScreen(
                    onAddEntry    = {},
                    onExport      = {},
                    onViewHistory = {},
                    onSupervisors = {},
                    timerWidget   = {},    // skip Hilt lookup in tests
                    viewModel     = makeFakeDashboardViewModel(DashboardUiState()),
                )
            }
        }
        composeTestRule.onNodeWithText("No drives recorded yet").assertIsDisplayed()
    }

    @Test
    fun dashboard_withRecentDrives_showsSupervisorName() {
        val sessions = listOf(
            DriveSession(
                id                 = 1,
                date               = LocalDate.of(2025, 6, 15),
                startTime          = LocalDateTime.of(2025, 6, 15, 14, 0),
                endTime            = LocalDateTime.of(2025, 6, 15, 16, 0),
                totalMinutes       = 120,
                nightMinutes       = 0,
                supervisorName     = "Jane Doe",
                supervisorInitials = "JD",
            )
        )
        composeTestRule.setContent {
            CoDriveLogTheme {
                DashboardScreen(
                    onAddEntry    = {},
                    onExport      = {},
                    onViewHistory = {},
                    onSupervisors = {},
                    timerWidget   = {},    // skip Hilt lookup in tests
                    viewModel     = makeFakeDashboardViewModel(
                        DashboardUiState(
                            totalHours   = 2f,
                            nightHours   = 0f,
                            recentDrives = sessions,
                        )
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Jane Doe").assertIsDisplayed()
    }

    @Test
    fun dashboard_fabClick_triggersOnAddEntry() {
        var clicked = false
        composeTestRule.setContent {
            CoDriveLogTheme {
                DashboardScreen(
                    onAddEntry    = { clicked = true },
                    onExport      = {},
                    onViewHistory = {},
                    onSupervisors = {},
                    timerWidget   = {},    // skip Hilt lookup in tests
                    viewModel     = makeFakeDashboardViewModel(DashboardUiState()),
                )
            }
        }
        composeTestRule.onNodeWithText("Add Entry").performClick()
        assert(clicked) { "Expected onAddEntry callback to be invoked" }
    }

    // ---- Helpers ----

    /**
     * Creates a [DashboardViewModel] backed by a [FakeDriveSessionDao]
     * pre-seeded with the sessions from [initialState].
     */
    private fun makeFakeDashboardViewModel(
        initialState: DashboardUiState,
    ): DashboardViewModel = DashboardViewModel(
        repository = DriveSessionRepository(FakeDriveSessionDao(initial = initialState.recentDrives)),
        onboardingRepository = mockk<OnboardingRepository> {
            every { studentName } returns flowOf("")
            every { permitNumber } returns flowOf("")
        },
    )
}
