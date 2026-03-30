package com.codrivelog.app.ui.supervisor

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.repository.SupervisorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dao:       FakeSupervisorDao
    private lateinit var repo:      SupervisorRepository
    private lateinit var viewModel: SupervisorViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao       = FakeSupervisorDao()
        repo      = SupervisorRepository(dao)
        viewModel = SupervisorViewModel(repo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun `initial state has empty supervisor list`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitItem().supervisors.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- add ----

    @Test
    fun `add with valid inputs returns true and inserts supervisor`() = runTest {
        viewModel.uiState.test {
            awaitItem() // empty initial

            val result = viewModel.add("Jane Doe", "JD")

            assertTrue(result)
            val list = awaitItem().supervisors
            assertEquals(1, list.size)
            assertEquals("Jane Doe", list.first().name)
            assertEquals("JD",       list.first().initials)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add with blank name returns false and does not insert`() = runTest {
        val result = viewModel.add("  ", "JD")
        assertFalse(result)
        viewModel.uiState.test {
            assertTrue(awaitItem().supervisors.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add with blank initials returns false and does not insert`() = runTest {
        val result = viewModel.add("Jane Doe", "  ")
        assertFalse(result)
        viewModel.uiState.test {
            assertTrue(awaitItem().supervisors.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add trims whitespace from name and initials`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.add("  Jane Doe  ", "  JD  ")
            val s = awaitItem().supervisors.first()
            assertEquals("Jane Doe", s.name)
            assertEquals("JD",       s.initials)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- delete ----

    @Test
    fun `delete removes the supervisor from uiState`() = runTest {
        viewModel.uiState.test {
            awaitItem()                           // empty initial
            viewModel.add("Jane Doe", "JD")
            val after = awaitItem().supervisors
            assertEquals(1, after.size)

            viewModel.delete(after.first())
            assertTrue(awaitItem().supervisors.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Alphabetical order ----

    @Test
    fun `supervisors are returned alphabetically`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.add("Zara Smith", "ZS")
            awaitItem()
            viewModel.add("Alice Brown", "AB")
            val list = awaitItem().supervisors
            assertEquals("Alice Brown", list[0].name)
            assertEquals("Zara Smith",  list[1].name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
