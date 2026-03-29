package com.codrivelog.app.data.db

import androidx.room.*
import com.codrivelog.app.data.model.Supervisor
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [Supervisor] CRUD operations.
 */
@Dao
interface SupervisorDao {

    /**
     * Observe all supervisors ordered alphabetically by name.
     *
     * @return A cold [Flow] that re-emits whenever the table changes.
     */
    @Query("SELECT * FROM supervisors ORDER BY name ASC")
    fun getAllSupervisors(): Flow<List<Supervisor>>

    /**
     * Insert a new supervisor.
     *
     * @param supervisor The supervisor to persist.
     * @return The row ID of the newly inserted row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supervisor: Supervisor): Long

    /**
     * Delete a supervisor record.
     *
     * @param supervisor The supervisor to remove.
     */
    @Delete
    suspend fun delete(supervisor: Supervisor)
}
