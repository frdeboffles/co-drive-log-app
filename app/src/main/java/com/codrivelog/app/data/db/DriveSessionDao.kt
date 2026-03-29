package com.codrivelog.app.data.db

import androidx.room.*
import com.codrivelog.app.data.model.DriveSession
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [DriveSession] CRUD and aggregate query operations.
 *
 * Queries that return live data use [Flow] so the UI layer can react to changes
 * without polling. Mutation operations are `suspend` functions to be called
 * from a coroutine.
 */
@Dao
interface DriveSessionDao {

    // ---- Queries ----

    /**
     * Observe all drive sessions ordered by date descending (most-recent first).
     *
     * ISO-8601 `date` strings sort correctly lexicographically, so `ORDER BY date DESC`
     * is equivalent to chronological ordering.
     *
     * @return A cold [Flow] that re-emits the full list whenever the table changes.
     */
    @Query("SELECT * FROM drive_sessions ORDER BY date DESC, startTime DESC")
    fun getAll(): Flow<List<DriveSession>>

    /**
     * Returns the sum of [DriveSession.totalMinutes] across every session, or
     * `0` when the table is empty.
     */
    @Query("SELECT COALESCE(SUM(totalMinutes), 0) FROM drive_sessions")
    suspend fun getTotalDrivingMinutes(): Int

    /**
     * Returns the sum of [DriveSession.nightMinutes] across every session, or
     * `0` when the table is empty.
     */
    @Query("SELECT COALESCE(SUM(nightMinutes), 0) FROM drive_sessions")
    suspend fun getTotalNightMinutes(): Int

    /**
     * Retrieve a single session by its primary key.
     *
     * @param id The session primary key.
     * @return The matching [DriveSession], or `null` if not found.
     */
    @Query("SELECT * FROM drive_sessions WHERE id = :id")
    suspend fun getById(id: Long): DriveSession?

    // ---- Mutations ----

    /**
     * Insert a new session.  If a row with the same primary key already exists
     * it is replaced (useful for upsert semantics during sync).
     *
     * @param session The session to persist.
     * @return The rowid of the newly inserted row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: DriveSession): Long

    /**
     * Update an existing session record matched by primary key.
     *
     * @param session The session with updated values.
     */
    @Update
    suspend fun update(session: DriveSession)

    /**
     * Permanently delete a session record.
     *
     * @param session The session to remove.
     */
    @Delete
    suspend fun delete(session: DriveSession)
}
