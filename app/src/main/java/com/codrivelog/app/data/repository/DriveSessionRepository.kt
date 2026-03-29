package com.codrivelog.app.data.repository

import com.codrivelog.app.data.db.DriveSessionDao
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository acting as the single source of truth for [DriveSession] data.
 *
 * Abstracts the Room DAO from the ViewModel layer and is injectable via Hilt.
 *
 * @param dao The Room DAO used to access the database.
 */
@Singleton
class DriveSessionRepository @Inject constructor(
    private val dao: DriveSessionDao,
) {

    /**
     * Observe all drive sessions ordered by most-recent first.
     *
     * @return A cold [Flow] emitting the full list whenever the table changes.
     */
    fun getAllSessions(): Flow<List<DriveSession>> = dao.getAllSessions()

    /**
     * Retrieve a single session by its primary key.
     *
     * @param id The session primary key.
     * @return The matching [DriveSession], or `null` if not found.
     */
    suspend fun getSession(id: Long): DriveSession? = dao.getSession(id)

    /**
     * Persist a new drive session.
     *
     * @param session The session to save.
     * @return The row ID of the newly inserted record.
     */
    suspend fun saveSession(session: DriveSession): Long = dao.insert(session)

    /**
     * Update an existing drive session.
     *
     * @param session The updated session (matched by primary key).
     */
    suspend fun updateSession(session: DriveSession) = dao.update(session)

    /**
     * Permanently delete a drive session.
     *
     * @param session The session to delete.
     */
    suspend fun deleteSession(session: DriveSession) = dao.delete(session)
}
