package com.codrivelog.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [SupervisorDao].
 *
 * Uses an in-memory database to keep tests isolated and side-effect-free.
 */
@RunWith(AndroidJUnit4::class)
class SupervisorDaoTest {

    private lateinit var db: CoDriveLogDatabase
    private lateinit var dao: SupervisorDao

    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CoDriveLogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.supervisorDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ---- Helpers ----

    private fun supervisor(name: String, initials: String) =
        Supervisor(name = name, initials = initials)

    // ---- insert / getById ----

    @Test
    fun insertAndGetById_returnsInsertedSupervisor() = runTest {
        val id = dao.insert(supervisor("Jane Doe", "JD"))
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Jane Doe", retrieved!!.name)
        assertEquals("JD", retrieved.initials)
    }

    @Test
    fun getById_unknownId_returnsNull() = runTest {
        assertNull(dao.getById(999L))
    }

    // ---- getAll ordering ----

    @Test
    fun getAll_emptyTable_returnsEmptyList() = runTest {
        assertTrue(dao.getAll().first().isEmpty())
    }

    @Test
    fun getAll_orderedAlphabeticallyByName() = runTest {
        dao.insert(supervisor("Zara Smith", "ZS"))
        dao.insert(supervisor("Alice Brown", "AB"))
        dao.insert(supervisor("Mike Jones", "MJ"))

        val supervisors = dao.getAll().first()

        assertEquals(3, supervisors.size)
        assertEquals("Alice Brown", supervisors[0].name)
        assertEquals("Mike Jones", supervisors[1].name)
        assertEquals("Zara Smith", supervisors[2].name)
    }

    @Test
    fun getAll_singleRecord_returnsSingleItem() = runTest {
        dao.insert(supervisor("Bob Parent", "BP"))
        val list = dao.getAll().first()
        assertEquals(1, list.size)
        assertEquals("Bob Parent", list[0].name)
    }

    // ---- delete ----

    @Test
    fun delete_removesTargetSupervisor() = runTest {
        val id = dao.insert(supervisor("Jane Doe", "JD"))
        val inserted = dao.getById(id)!!
        dao.delete(inserted)
        assertNull(dao.getById(id))
    }

    @Test
    fun delete_onlyRemovesTargetRow() = runTest {
        val id1 = dao.insert(supervisor("Alice", "A"))
        val id2 = dao.insert(supervisor("Bob", "B"))
        dao.delete(dao.getById(id1)!!)

        assertNull(dao.getById(id1))
        assertNotNull(dao.getById(id2))
        assertEquals(1, dao.getAll().first().size)
    }

    @Test
    fun delete_allSupervisors_tableBecomesEmpty() = runTest {
        val id1 = dao.insert(supervisor("Alice", "A"))
        val id2 = dao.insert(supervisor("Bob", "B"))
        dao.delete(dao.getById(id1)!!)
        dao.delete(dao.getById(id2)!!)
        assertTrue(dao.getAll().first().isEmpty())
    }

    // ---- upsert / replace ----

    @Test
    fun insert_withSameId_replacesExistingRecord() = runTest {
        val id = dao.insert(supervisor("Old Name", "ON"))
        val replacement = Supervisor(id = id, name = "New Name", initials = "NN")
        dao.insert(replacement)

        val retrieved = dao.getById(id)!!
        assertEquals("New Name", retrieved.name)
        assertEquals("NN", retrieved.initials)
        // Table should still have only one row
        assertEquals(1, dao.getAll().first().size)
    }

    // ---- Flow emissions ----

    @Test
    fun getAll_emitsUpdatedListAfterInsert() = runTest {
        assertTrue(dao.getAll().first().isEmpty())

        dao.insert(supervisor("Carol", "C"))

        val after = dao.getAll().first()
        assertEquals(1, after.size)
        assertEquals("Carol", after[0].name)
    }

    @Test
    fun getAll_emitsUpdatedListAfterDelete() = runTest {
        val id = dao.insert(supervisor("Dana", "D"))
        assertEquals(1, dao.getAll().first().size)

        dao.delete(dao.getById(id)!!)

        assertTrue(dao.getAll().first().isEmpty())
    }

    // ---- field persistence ----

    @Test
    fun initialsField_persistsCorrectly() = runTest {
        val id = dao.insert(supervisor("Frank Garcia", "FG"))
        assertEquals("FG", dao.getById(id)!!.initials)
    }

    @Test
    fun multipleInserts_eachGetUniqueId() = runTest {
        val id1 = dao.insert(supervisor("A", "A"))
        val id2 = dao.insert(supervisor("B", "B"))
        val id3 = dao.insert(supervisor("C", "C"))
        assertTrue(setOf(id1, id2, id3).size == 3)
    }
}
