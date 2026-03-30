package com.codrivelog.core.dr2324

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class StudentProfile(
    val studentName: String,
    val permitNumber: String = "",
    val signatureName: String = "",
    val signatureDate: String = "",
)

data class DriveSession(
    val date: LocalDate,
    val verifierInitials: String,
    val totalMinutes: Int,
    val nightMinutes: Int,
    val comments: String = "",
)

data class Dr2324Row(
    val date: String,
    val verifierInitials: String,
    val drivingTime: String,
    val nightDrivingTime: String,
    val comments: String,
    val totalMinutes: Int,
    val nightMinutes: Int,
)

data class Dr2324Page(
    val rows: List<Dr2324Row>,
    val pageTotalMinutes: Int,
    val pageNightMinutes: Int,
)

data class Dr2324Document(
    val studentProfile: StudentProfile,
    val pages: List<Dr2324Page>,
    val grandTotalMinutes: Int,
    val grandNightMinutes: Int,
)

object Dr2324Formatters {
    private val DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    fun formatDate(date: LocalDate): String = date.format(DATE_FMT)

    fun formatTime(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return "${h}h ${m}m"
    }
}
