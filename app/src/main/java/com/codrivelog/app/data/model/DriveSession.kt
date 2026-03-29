package com.codrivelog.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted record of a single supervised driving session.
 *
 * Corresponds to one row in the Colorado DR 2324 driving log.
 *
 * @property id               Auto-generated primary key.
 * @property epochMillis      Unix timestamp (ms) when the session started.
 * @property totalMinutes     Total duration of the session in minutes.
 * @property nightMinutes     Minutes driven during night (after sunset / before sunrise).
 * @property supervisorId     Foreign key reference to the [Supervisor] who accompanied the teen.
 * @property comments         Optional free-text notes for this session.
 */
@Entity(tableName = "drive_sessions")
data class DriveSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val epochMillis: Long,
    val totalMinutes: Int,
    val nightMinutes: Int,
    val supervisorId: Long,
    val comments: String = "",
)
