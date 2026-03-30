package com.codrivelog.app.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit tests for [PdfExporter] that exercise the data-to-layout mapping
 * logic without invoking the Android [android.graphics.pdf.PdfDocument] API.
 *
 * Tests that require a real [android.graphics.pdf.PdfDocument] (e.g. full
 * page rendering) belong in instrumented tests; those are tracked separately.
 */
class PdfExporterTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun row(
        date: String = "06/15/2025",
        drivingTime: String = "1h 0m",
        nightDriving: String = "",
        supervisorInitials: String = "JD",
        comments: String = "",
        totalMinutes: Int = 60,
        nightMinutes: Int = 0,
    ) = DriveLogRow(
        date               = date,
        drivingTime        = drivingTime,
        nightDriving       = nightDriving,
        supervisorInitials = supervisorInitials,
        comments           = comments,
        totalMinutes       = totalMinutes,
        nightMinutes       = nightMinutes,
    )

    // ── Layout constants ─────────────────────────────────────────────────────

    @Test
    fun `page dimensions are US Letter portrait`() {
        assertEquals(612, PdfExporter.PAGE_WIDTH)
        assertEquals(792, PdfExporter.PAGE_HEIGHT)
    }

    @Test
    fun `column count matches DR 2324 columns`() {
        // DR 2324: Date | Driving Time | Night Driving | Supervisor Initials | Comments
        assertEquals(5, PdfExporter.COL_HEADERS.size)
    }

    @Test
    fun `column headers contain expected DR 2324 labels`() {
        val flat = PdfExporter.COL_HEADERS.joinToString(" ")
        assertTrue(flat.contains("Date", ignoreCase = true))
        assertTrue(flat.contains("Driving", ignoreCase = true))
        assertTrue(flat.contains("Night", ignoreCase = true))
        assertTrue(flat.contains("Supervisor", ignoreCase = true))
        assertTrue(flat.contains("Comments", ignoreCase = true))
    }

    // ── DriveLogRow.formatMinutes ────────────────────────────────────────────

    @Test
    fun `formatMinutes 0 minutes`() {
        assertEquals("0h 0m", DriveLogRow.formatMinutes(0))
    }

    @Test
    fun `formatMinutes exact hours`() {
        assertEquals("1h 0m", DriveLogRow.formatMinutes(60))
        assertEquals("2h 0m", DriveLogRow.formatMinutes(120))
    }

    @Test
    fun `formatMinutes hours and minutes`() {
        assertEquals("1h 30m", DriveLogRow.formatMinutes(90))
        assertEquals("0h 45m", DriveLogRow.formatMinutes(45))
        assertEquals("10h 5m", DriveLogRow.formatMinutes(605))
    }

    // ── DriveLogRow.from() ────────────────────────────────────────────────────

    @Test
    fun `DriveLogRow from produces correct date format`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 1, 5),
            startTime          = LocalDateTime.of(2025, 1, 5, 8, 0),
            endTime            = LocalDateTime.of(2025, 1, 5, 9, 30),
            totalMinutes       = 90,
            nightMinutes       = 0,
            supervisorName     = "Alice",
            supervisorInitials = "A",
        )
        val logRow = DriveLogRow.from(session)
        assertEquals("01/05/2025", logRow.date)
    }

    @Test
    fun `DriveLogRow from formats total minutes correctly`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 6, 1),
            startTime          = LocalDateTime.of(2025, 6, 1, 10, 0),
            endTime            = LocalDateTime.of(2025, 6, 1, 11, 45),
            totalMinutes       = 105,
            nightMinutes       = 15,
            supervisorName     = "Bob",
            supervisorInitials = "B",
        )
        val logRow = DriveLogRow.from(session)
        assertEquals("1h 45m", logRow.drivingTime)
        assertEquals("0h 15m", logRow.nightDriving)
    }

    @Test
    fun `DriveLogRow from uses empty string for zero night minutes`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 7, 4),
            startTime          = LocalDateTime.of(2025, 7, 4, 9, 0),
            endTime            = LocalDateTime.of(2025, 7, 4, 10, 0),
            totalMinutes       = 60,
            nightMinutes       = 0,
            supervisorName     = "Carol",
            supervisorInitials = "C",
        )
        val logRow = DriveLogRow.from(session)
        assertEquals("", logRow.nightDriving)
    }

    @Test
    fun `DriveLogRow from maps null comments to empty string`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 8, 10),
            startTime          = LocalDateTime.of(2025, 8, 10, 14, 0),
            endTime            = LocalDateTime.of(2025, 8, 10, 15, 0),
            totalMinutes       = 60,
            nightMinutes       = 0,
            supervisorName     = "Dave",
            supervisorInitials = "D",
            comments           = null,
        )
        val logRow = DriveLogRow.from(session)
        assertEquals("", logRow.comments)
    }

    @Test
    fun `DriveLogRow from preserves non-null comments`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 9, 1),
            startTime          = LocalDateTime.of(2025, 9, 1, 7, 0),
            endTime            = LocalDateTime.of(2025, 9, 1, 8, 30),
            totalMinutes       = 90,
            nightMinutes       = 0,
            supervisorName     = "Eve",
            supervisorInitials = "E",
            comments           = "Freeway practice",
        )
        val logRow = DriveLogRow.from(session)
        assertEquals("Freeway practice", logRow.comments)
    }

    // ── Grand-total math (mirrors what PdfExporter computes internally) ───────

    @Test
    fun `grand total minutes calculation is correct`() {
        val rows = listOf(
            row(totalMinutes = 60, nightMinutes = 0),
            row(totalMinutes = 90, nightMinutes = 30),
            row(totalMinutes = 30, nightMinutes = 15),
        )
        val totalMinutes = rows.sumOf { it.totalMinutes }
        val nightMinutes = rows.sumOf { it.nightMinutes }

        assertEquals(180, totalMinutes)
        assertEquals(45, nightMinutes)
        assertEquals("3h 0m",  DriveLogRow.formatMinutes(totalMinutes))
        assertEquals("0h 45m", DriveLogRow.formatMinutes(nightMinutes))
    }

    @Test
    fun `grand total for empty list is zero`() {
        val rows = emptyList<DriveLogRow>()
        assertEquals(0, rows.sumOf { it.totalMinutes })
        assertEquals("0h 0m", DriveLogRow.formatMinutes(0))
    }
}
