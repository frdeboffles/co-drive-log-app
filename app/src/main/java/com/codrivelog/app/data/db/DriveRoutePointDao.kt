package com.codrivelog.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codrivelog.app.data.model.DriveRoutePoint
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveRoutePointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: DriveRoutePoint): Long

    @Query("SELECT * FROM drive_route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: Long): Flow<List<DriveRoutePoint>>

    @Query("SELECT COUNT(*) FROM drive_route_points WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    @Query("DELETE FROM drive_route_points WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
