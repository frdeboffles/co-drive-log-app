package com.codrivelog.app.data.db

import androidx.room.*
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [DriveSession] CRUD operations.
 *
 * All query methods that return live data expose [Flow] so the UI layer can
 * collect updates reactively.
 */
@Dao
interface DriveSessionDao {

    /**
     * Observe all drive sessions ordered by most-recent first.
     *
     * @return A cold [Flow] that re-emits whenever the table changes.
     */
    @Query("SELECT * FROM drive_sessions ORDER BY epochMillis DESC")
    fun getAllSessions(): Flow<List<DriveSession>>

    /**
     * Retrieve a single session by its primary key.
     *
     * @param id The session's primary key.
     */
    @Query("SELECT * FROM drive_sessions WHERE id = :id")
    suspend fun getSession(id: Long): DriveSession?

    /**
     * Insert a new session.
     *
     * @param session The session to persist.
     * @return The row ID of the newly inserted row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DriveSession): Long

    /**
     * Update an existing session record.
     *
     * @param session The session to update (matched by primary key).
     */
    @Update
    suspend fun update(session: DriveSession)

    /**
     * Delete a session record.
     *
     * @param session The session to remove.
     */
    @Delete
    suspend fun delete(session: DriveSession)

    /**
     * Returns the sum of [DriveSession.totalMinutes] across all sessions, or 0
     * if the table is empty.
     */
    @Query("SELECT COALESCE(SUM(totalMinutes), 0) FROM drive_sessions")
    suspend fun totalMinutes(): Int

    /**
     * Returns the sum of [DriveSession.nightMinutes] across all sessions, or 0
     * if the table is empty.
     */
    @Query("SELECT COALESCE(SUM(nightMinutes), 0) FROM drive_sessions")
    suspend fun totalNightMinutes(): Int
}
