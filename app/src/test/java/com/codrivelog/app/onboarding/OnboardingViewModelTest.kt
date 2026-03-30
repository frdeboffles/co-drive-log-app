package com.codrivelog.app.onboarding

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.repository.SupervisorRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.coJustRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val onboardingRepo = mockk<OnboardingRepository>(relaxed = true) {
        every { isOnboardingComplete } returns flowOf(false)
    }
    private lateinit var supervisorDao:  FakeSupervisorDao
    private lateinit var supervisorRepo: SupervisorRepository
    private lateinit var viewModel:      OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        supervisorDao  = FakeSupervisorDao()
        supervisorRepo = SupervisorRepository(supervisorDao)
        viewModel      = OnboardingViewModel(onboardingRepo, supervisorRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun `starts on page 0`() = runTest {
        assertEquals(0, viewModel.page.value)
    }

    @Test
    fun `onboardingComplete starts false`() = runTest {
        assertFalse(viewModel.onboardingComplete.value)
    }

    @Test
    fun `error starts null`() = runTest {
        assertNull(viewModel.error.value)
    }

    // ---- nextPage ----

    @Test
    fun `nextPage with blank name sets error and stays on page 0`() = runTest {
        viewModel.nextPage("  ", "P1234")
        assertEquals(0, viewModel.page.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `nextPage with valid name advances to page 1 and clears error`() = runTest {
        viewModel.nextPage("Alex Rider", "P1234")
        assertEquals(1, viewModel.page.value)
        assertNull(viewModel.error.value)
    }

    // ---- previousPage ----

    @Test
    fun `previousPage returns to page 0 and clears error`() = runTest {
        viewModel.nextPage("Alex Rider", "P1234")
        viewModel.previousPage()
        assertEquals(0, viewModel.page.value)
        assertNull(viewModel.error.value)
    }

    // ---- finish ----

    @Test
    fun `finish with blank supervisor name sets error`() = runTest {
        viewModel.finish("Alex Rider", "P1234", "  ", "JD")
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.onboardingComplete.value)
    }

    @Test
    fun `finish with blank initials sets error`() = runTest {
        viewModel.finish("Alex Rider", "P1234", "Jane Doe", "  ")
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.onboardingComplete.value)
    }

    @Test
    fun `finish with valid inputs sets onboardingComplete to true`() = runTest {
        coJustRun { onboardingRepo.completeOnboarding(any(), any()) }

        viewModel.finish("Alex Rider", "P1234", "Jane Doe", "JD")
        assertTrue(viewModel.onboardingComplete.value)
    }

    @Test
    fun `finish calls completeOnboarding with the student name`() = runTest {
        coJustRun { onboardingRepo.completeOnboarding(any(), any()) }

        viewModel.finish("Alex Rider", "P1234", "Jane Doe", "JD")
        coVerify { onboardingRepo.completeOnboarding("Alex Rider", "P1234") }
    }

    @Test
    fun `finish inserts supervisor into repository`() = runTest {
        coJustRun { onboardingRepo.completeOnboarding(any(), any()) }

        viewModel.finish("Alex Rider", "P1234", "Jane Doe", "jd")

        supervisorRepo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Jane Doe", list.first().name)
            assertEquals("JD",       list.first().initials)   // should be uppercased
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- clearError ----

    @Test
    fun `clearError nulls out the error`() = runTest {
        viewModel.nextPage("  ", "P1234") // trigger error
        assertNotNull(viewModel.error.value)
        viewModel.clearError()
        assertNull(viewModel.error.value)
    }
}
