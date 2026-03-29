package com.codrivelog.app.data.repository

import app.cash.turbine.test
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DriveSessionRepositoryTest {

    private lateinit var dao: FakeDriveSessionDao
    private lateinit var repository: DriveSessionRepository

    @BeforeEach
    fun setUp() {
        dao = FakeDriveSessionDao()
        repository = DriveSessionRepository(dao)
    }

    // ---- Helpers ----

    private fun session(
        totalMinutes: Int = 60,
        nightMinutes: Int = 0,
        date: LocalDate = LocalDate.of(2025, 6, 15),
        startTime: LocalDateTime = LocalDateTime.of(2025, 6, 15, 9, 0),
        endTime: LocalDateTime = LocalDateTime.of(2025, 6, 15, 10, 0),
        supervisorName: String = "Jane Doe",
        supervisorInitials: String = "JD",
        comments: String? = null,
        isManualEntry: Boolean = false,
    ) = DriveSession(
        date = date,
        startTime = startTime,
        endTime = endTime,
        totalMinutes = totalMinutes,
        nightMinutes = nightMinutes,
        supervisorName = supervisorName,
        supervisorInitials = supervisorInitials,
        comments = comments,
        isManualEntry = isManualEntry,
    )

    // ---- getAll ----

    @Test
    fun `getAll emits empty list when no sessions exist`() = runTest {
        repository.getAll().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll emits updated list after insert`() = runTest {
        repository.getAll().test {
            awaitItem() // initial empty emission

            repository.insert(session(totalMinutes = 45))

            val after = awaitItem()
            assertEquals(1, after.size)
            assertEquals(45, after.first().totalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll orders sessions by date descending then startTime descending`() = runTest {
        val older = session(
            date = LocalDate.of(2025, 5, 1),
            startTime = LocalDateTime.of(2025, 5, 1, 9, 0),
            endTime = LocalDateTime.of(2025, 5, 1, 10, 0),
            totalMinutes = 60,
        )
        val newer = session(
            date = LocalDate.of(2025, 6, 15),
            startTime = LocalDateTime.of(2025, 6, 15, 14, 0),
            endTime = LocalDateTime.of(2025, 6, 15, 15, 30),
            totalMinutes = 90,
        )
        repository.insert(older)
        repository.insert(newer)

        repository.getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals(LocalDate.of(2025, 6, 15), list[0].date)
            assertEquals(LocalDate.of(2025, 5, 1),  list[1].date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll emits updated list after delete`() = runTest {
        val id = repository.insert(session())

        repository.getAll().test {
            awaitItem() // current state with one item

            val inserted = repository.getById(id)!!
            repository.delete(inserted)

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- getById ----

    @Test
    fun `getById returns session when it exists`() = runTest {
        val id = repository.insert(session(supervisorName = "Alice"))
        val found = repository.getById(id)
        assertNotNull(found)
        assertEquals("Alice", found!!.supervisorName)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertNull(repository.getById(999L))
    }

    // ---- getTotalDrivingMinutes ----

    @Test
    fun `getTotalDrivingMinutes returns zero when empty`() = runTest {
        assertEquals(0, repository.getTotalDrivingMinutes())
    }

    @Test
    fun `getTotalDrivingMinutes sums all sessions`() = runTest {
        repository.insert(session(totalMinutes = 60))
        repository.insert(session(totalMinutes = 90))
        repository.insert(session(totalMinutes = 30))
        assertEquals(180, repository.getTotalDrivingMinutes())
    }

    @Test
    fun `getTotalDrivingMinutes updates after delete`() = runTest {
        val id = repository.insert(session(totalMinutes = 60))
        repository.insert(session(totalMinutes = 40))
        repository.delete(repository.getById(id)!!)
        assertEquals(40, repository.getTotalDrivingMinutes())
    }

    // ---- getTotalNightMinutes ----

    @Test
    fun `getTotalNightMinutes returns zero when empty`() = runTest {
        assertEquals(0, repository.getTotalNightMinutes())
    }

    @Test
    fun `getTotalNightMinutes sums nightMinutes only`() = runTest {
        repository.insert(session(totalMinutes = 90, nightMinutes = 30))
        repository.insert(session(totalMinutes = 60, nightMinutes = 0))
        repository.insert(session(totalMinutes = 45, nightMinutes = 45))
        assertEquals(75, repository.getTotalNightMinutes())
    }

    // ---- insert ----

    @Test
    fun `insert returns a positive id`() = runTest {
        val id = repository.insert(session())
        assertTrue(id > 0)
    }

    @Test
    fun `insert persists all fields`() = runTest {
        val id = repository.insert(
            session(
                totalMinutes = 75,
                nightMinutes = 20,
                supervisorName = "Bob Parent",
                supervisorInitials = "BP",
                comments = "Highway practice",
                isManualEntry = true,
            )
        )
        val found = repository.getById(id)!!
        assertEquals(75, found.totalMinutes)
        assertEquals(20, found.nightMinutes)
        assertEquals("Bob Parent", found.supervisorName)
        assertEquals("BP", found.supervisorInitials)
        assertEquals("Highway practice", found.comments)
        assertTrue(found.isManualEntry)
    }

    // ---- update ----

    @Test
    fun `update changes persisted values`() = runTest {
        val id = repository.insert(session(totalMinutes = 60, comments = null))
        val original = repository.getById(id)!!
        repository.update(original.copy(totalMinutes = 120, comments = "Updated"))
        val updated = repository.getById(id)!!
        assertEquals(120, updated.totalMinutes)
        assertEquals("Updated", updated.comments)
    }

    @Test
    fun `update does not affect other sessions`() = runTest {
        val id1 = repository.insert(session(totalMinutes = 60))
        val id2 = repository.insert(session(totalMinutes = 90))
        val first = repository.getById(id1)!!
        repository.update(first.copy(totalMinutes = 30))
        assertEquals(90, repository.getById(id2)!!.totalMinutes)
    }

    // ---- delete ----

    @Test
    fun `delete removes only the target session`() = runTest {
        val id1 = repository.insert(session(totalMinutes = 60))
        val id2 = repository.insert(session(totalMinutes = 90))
        repository.delete(repository.getById(id1)!!)
        assertNull(repository.getById(id1))
        assertNotNull(repository.getById(id2))
    }
}
