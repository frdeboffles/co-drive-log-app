package com.codrivelog.app.data.repository

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SupervisorRepositoryTest {

    private lateinit var dao: FakeSupervisorDao
    private lateinit var repository: SupervisorRepository

    @BeforeEach
    fun setUp() {
        dao = FakeSupervisorDao()
        repository = SupervisorRepository(dao)
    }

    // ---- Helpers ----

    private fun supervisor(name: String, initials: String) =
        Supervisor(name = name, initials = initials)

    // ---- getAll ----

    @Test
    fun `getAll emits empty list when no supervisors exist`() = runTest {
        repository.getAll().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll emits updated list after insert`() = runTest {
        repository.getAll().test {
            awaitItem() // initial empty emission

            repository.insert(supervisor("Jane Doe", "JD"))

            val after = awaitItem()
            assertEquals(1, after.size)
            assertEquals("Jane Doe", after.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll orders supervisors alphabetically by name`() = runTest {
        repository.insert(supervisor("Zara", "Z"))
        repository.insert(supervisor("Alice", "A"))
        repository.insert(supervisor("Mike", "M"))

        repository.getAll().test {
            val list = awaitItem()
            assertEquals(listOf("Alice", "Mike", "Zara"), list.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll emits updated list after delete`() = runTest {
        val id = repository.insert(supervisor("Jane", "J"))

        repository.getAll().test {
            awaitItem() // current state

            repository.delete(repository.getById(id)!!)

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- getById ----

    @Test
    fun `getById returns supervisor when it exists`() = runTest {
        val id = repository.insert(supervisor("Bob Parent", "BP"))
        val found = repository.getById(id)
        assertNotNull(found)
        assertEquals("Bob Parent", found!!.name)
        assertEquals("BP", found.initials)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertNull(repository.getById(999L))
    }

    // ---- insert ----

    @Test
    fun `insert returns a positive id`() = runTest {
        val id = repository.insert(supervisor("Jane", "J"))
        assertTrue(id > 0)
    }

    @Test
    fun `insert multiple supervisors assigns unique ids`() = runTest {
        val id1 = repository.insert(supervisor("A", "A"))
        val id2 = repository.insert(supervisor("B", "B"))
        val id3 = repository.insert(supervisor("C", "C"))
        assertEquals(3, setOf(id1, id2, id3).size)
    }

    @Test
    fun `insert with explicit id replaces existing record`() = runTest {
        val id = repository.insert(supervisor("Old Name", "ON"))
        repository.insert(Supervisor(id = id, name = "New Name", initials = "NN"))

        val found = repository.getById(id)!!
        assertEquals("New Name", found.name)
        assertEquals("NN", found.initials)
        dao.getAll().test { assertEquals(1, awaitItem().size); cancel() }
    }

    @Test
    fun `insert persists initials correctly`() = runTest {
        val id = repository.insert(supervisor("Frank Garcia", "FG"))
        assertEquals("FG", repository.getById(id)!!.initials)
    }

    // ---- delete ----

    @Test
    fun `delete removes only the target supervisor`() = runTest {
        val id1 = repository.insert(supervisor("Alice", "A"))
        val id2 = repository.insert(supervisor("Bob", "B"))
        repository.delete(repository.getById(id1)!!)
        assertNull(repository.getById(id1))
        assertNotNull(repository.getById(id2))
    }

    @Test
    fun `delete all supervisors leaves empty list`() = runTest {
        val id1 = repository.insert(supervisor("A", "A"))
        val id2 = repository.insert(supervisor("B", "B"))
        repository.delete(repository.getById(id1)!!)
        repository.delete(repository.getById(id2)!!)

        repository.getAll().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
