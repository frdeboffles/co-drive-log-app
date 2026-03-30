package com.codrivelog.app.export

import com.codrivelog.app.data.model.DriveSession
import java.time.format.DateTimeFormatter

/**
 * Flat, export-oriented representation of a single drive session.
 *
 * Maps a [DriveSession] to the columns present on the Colorado DR 2324
 * Drive Time Log Sheet so that [PdfExporter] and [CsvExporter] share the
 * same data-mapping logic and are easy to unit-test without Room.
 *
 * @property date               Date string formatted as MM/dd/yyyy.
 * @property drivingTime        Total driving time formatted as "Xh Ym".
 * @property nightDriving       Night driving time formatted as "Xh Ym" (empty string if zero).
 * @property supervisorInitials Supervisor initials for the printed log column.
 * @property comments           Optional comments; empty string when null.
 * @property totalMinutes       Raw total minutes (for grand-total calculations).
 * @property nightMinutes       Raw night minutes (for grand-total calculations).
 */
data class DriveLogRow(
    val date: String,
    val drivingTime: String,
    val nightDriving: String,
    val supervisorInitials: String,
    val comments: String,
    val totalMinutes: Int,
    val nightMinutes: Int,
) {
    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy")

        /** Convert a [DriveSession] to a [DriveLogRow]. */
        fun from(session: DriveSession): DriveLogRow = DriveLogRow(
            date               = session.date.format(DATE_FMT),
            drivingTime        = formatMinutes(session.totalMinutes),
            nightDriving       = if (session.nightMinutes > 0)
                                     formatMinutes(session.nightMinutes)
                                 else "",
            supervisorInitials = session.supervisorInitials,
            comments           = session.comments.orEmpty(),
            totalMinutes       = session.totalMinutes,
            nightMinutes       = session.nightMinutes,
        )

        /**
         * Format a minute count as "Xh Ym".
         * Examples: 90 → "1h 30m", 45 → "0h 45m".
         */
        fun formatMinutes(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return "${h}h ${m}m"
        }
    }
}
