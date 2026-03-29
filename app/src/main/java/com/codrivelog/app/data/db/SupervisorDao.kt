package com.codrivelog.app.data.db

import androidx.room.*
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [Supervisor] CRUD operations.
 *
 * Queries returning live data expose [Flow]; mutations are `suspend` functions.
 */
@Dao
interface SupervisorDao {

    /**
     * Observe all supervisors ordered alphabetically by name.
     *
     * @return A cold [Flow] that re-emits whenever the supervisors table changes.
     */
    @Query("SELECT * FROM supervisors ORDER BY name ASC")
    fun getAll(): Flow<List<Supervisor>>

    /**
     * Retrieve a single supervisor by primary key.
     *
     * @param id The supervisor's primary key.
     * @return The matching [Supervisor], or `null` if not found.
     */
    @Query("SELECT * FROM supervisors WHERE id = :id")
    suspend fun getById(id: Long): Supervisor?

    /**
     * Insert a new supervisor.  Existing rows with the same primary key are replaced.
     *
     * @param supervisor The supervisor to persist.
     * @return The rowid of the newly inserted row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supervisor: Supervisor): Long

    /**
     * Permanently delete a supervisor record.
     *
     * @param supervisor The supervisor to remove.
     */
    @Delete
    suspend fun delete(supervisor: Supervisor)
}
