package com.codrivelog.app.data.repository

import com.codrivelog.app.data.db.DriveSessionDao
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing the single source of truth for [DriveSession] data.
 *
 * Wraps [DriveSessionDao] so ViewModels never depend directly on Room,
 * making them straightforward to test with a [FakeDriveSessionDao].
 *
 * @param dao The Room DAO for drive-session persistence.
 */
@Singleton
class DriveSessionRepository @Inject constructor(
    private val dao: DriveSessionDao,
) {

    /**
     * Observe all drive sessions ordered by date descending (most-recent first).
     *
     * @return A cold [Flow] that re-emits the full list whenever the table changes.
     */
    fun getAll(): Flow<List<DriveSession>> = dao.getAll()

    /**
     * Retrieve a single session by its primary key.
     *
     * @param id The session primary key.
     * @return The matching [DriveSession], or `null` if not found.
     */
    suspend fun getById(id: Long): DriveSession? = dao.getById(id)

    /**
     * Returns the cumulative total driving minutes across all sessions.
     */
    suspend fun getTotalDrivingMinutes(): Int = dao.getTotalDrivingMinutes()

    /**
     * Returns the cumulative night driving minutes across all sessions.
     */
    suspend fun getTotalNightMinutes(): Int = dao.getTotalNightMinutes()

    /**
     * Persist a new drive session.
     *
     * @param session The session to save.
     * @return The row ID of the newly inserted record.
     */
    suspend fun insert(session: DriveSession): Long = dao.insert(session)

    /**
     * Update an existing drive session matched by primary key.
     *
     * @param session The updated session.
     */
    suspend fun update(session: DriveSession) = dao.update(session)

    /**
     * Permanently delete a drive session.
     *
     * @param session The session to delete.
     */
    suspend fun delete(session: DriveSession) = dao.delete(session)

    /** Permanently delete all drive sessions. */
    suspend fun deleteAll() = dao.deleteAll()
}
