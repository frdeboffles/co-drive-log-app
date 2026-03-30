package com.codrivelog.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * A sampled GPS route point captured during an active (timer-based) drive.
 *
 * Route points are linked to a parent [DriveSession]. Deleting a session
 * cascades and removes all related route points automatically.
 */
@Entity(
    tableName = "drive_route_points",
    foreignKeys = [
        ForeignKey(
            entity = DriveSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["sessionId", "timestamp"]),
    ],
)
data class DriveRoutePoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: LocalDateTime,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)
