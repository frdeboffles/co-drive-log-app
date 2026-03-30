package com.codrivelog.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Persisted record of a single supervised driving session.
 *
 * Corresponds to one row in the Colorado DR 2324 driving log. [LocalDate] and
 * [LocalDateTime] fields are stored as ISO-8601 strings via [com.codrivelog.app.data.db.DateTimeConverters].
 *
 * @property id                  Auto-generated primary key.
 * @property date                Calendar date of the drive (year/month/day only).
 * @property startTime           Date-and-time when the drive started.
 * @property endTime             Date-and-time when the drive ended.
 * @property totalMinutes        Total duration of the session in whole minutes.
 * @property nightMinutes        Minutes driven during night (after sunset / before sunrise).
 * @property supervisorName      Full name of the supervising adult.
 * @property supervisorInitials  Initials of the supervising adult for the printed log.
 * @property comments            Optional free-text notes; `null` when not provided.
 * @property isManualEntry       `true` when the entry was typed in manually (retroactive),
 *                               `false` when recorded by the live drive timer.
 */
@Entity(tableName = "drive_sessions")
data class DriveSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val totalMinutes: Int,
    val nightMinutes: Int,
    val supervisorName: String,
    val supervisorInitials: String,
    val comments: String? = null,
    val isManualEntry: Boolean = false,
)
