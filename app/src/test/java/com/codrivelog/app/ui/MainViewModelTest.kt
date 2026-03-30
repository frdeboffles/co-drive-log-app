package com.codrivelog.app.ui

import app.cash.turbine.test
import com.codrivelog.app.onboarding.OnboardingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val completeFlow = MutableStateFlow(false)
    private val nameFlow     = MutableStateFlow("")

    private val onboardingRepo = mockk<OnboardingRepository>(relaxed = true)

    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        every { onboardingRepo.isOnboardingComplete } returns completeFlow
        every { onboardingRepo.studentName }           returns nameFlow
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(onboardingRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- ready flag ----

    @Test
    fun `initial value has ready = false`() {
        assertFalse(viewModel.uiState.value.ready)
    }

    @Test
    fun `after first emission ready = true`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            // The stateIn initial value may merge with the first upstream emission.
            // Drain until we see ready = true.
            var state = awaitItem()
            while (!state.ready) state = awaitItem()
            assertTrue(state.ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- showOnboarding ----

    @Test
    fun `showOnboarding is true when onboarding not complete`() = runTest(testDispatcher) {
        completeFlow.value = false
        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.ready) state = awaitItem()
            assertTrue(state.showOnboarding)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showOnboarding is false when onboarding complete`() = runTest(testDispatcher) {
        completeFlow.value = true
        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.ready) state = awaitItem()
            assertFalse(state.showOnboarding)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- studentName ----

    @Test
    fun `studentName is reflected in uiState`() = runTest(testDispatcher) {
        nameFlow.value = "Alex Rider"
        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.ready) state = awaitItem()
            assertEquals("Alex Rider", state.studentName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- reactive update ----

    @Test
    fun `showOnboarding flips to false after onboarding completes`() = runTest(testDispatcher) {
        completeFlow.value = false
        nameFlow.value     = ""

        viewModel.uiState.test {
            // Wait until first real emission
            var state = awaitItem()
            while (!state.ready) state = awaitItem()
            assertTrue(state.showOnboarding)

            completeFlow.value = true
            nameFlow.value     = "Alex Rider"

            // combine may emit an intermediate item; drain until both values are reflected
            var after = awaitItem()
            while (after.showOnboarding || after.studentName != "Alex Rider") after = awaitItem()
            assertFalse(after.showOnboarding)
            assertEquals("Alex Rider", after.studentName)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
