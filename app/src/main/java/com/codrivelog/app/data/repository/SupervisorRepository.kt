package com.codrivelog.app.data.repository

import com.codrivelog.app.data.db.SupervisorDao
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing the single source of truth for [Supervisor] data.
 *
 * Wraps [SupervisorDao] so ViewModels never depend directly on Room,
 * making them straightforward to test with a [FakeSupervisorDao].
 *
 * @param dao The Room DAO for supervisor persistence.
 */
@Singleton
class SupervisorRepository @Inject constructor(
    private val dao: SupervisorDao,
) {

    /**
     * Observe all supervisors ordered alphabetically by name.
     *
     * @return A cold [Flow] that re-emits whenever the supervisors table changes.
     */
    fun getAll(): Flow<List<Supervisor>> = dao.getAll()

    /**
     * Retrieve a single supervisor by primary key.
     *
     * @param id The supervisor primary key.
     * @return The matching [Supervisor], or `null` if not found.
     */
    suspend fun getById(id: Long): Supervisor? = dao.getById(id)

    /**
     * Persist a new supervisor. Replaces an existing row with the same id.
     *
     * @param supervisor The supervisor to save.
     * @return The row ID of the newly inserted record.
     */
    suspend fun insert(supervisor: Supervisor): Long = dao.insert(supervisor)

    /**
     * Permanently delete a supervisor.
     *
     * @param supervisor The supervisor to delete.
     */
    suspend fun delete(supervisor: Supervisor) = dao.delete(supervisor)
}
