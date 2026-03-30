package com.codrivelog.app.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class CsvExporterTest {

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

    private fun write(
        rows: List<DriveLogRow>,
        studentName: String = "",
    ): String {
        val out = ByteArrayOutputStream()
        CsvExporter.write(rows, studentName, out)
        return out.toString(Charsets.UTF_8.name())
    }

    // ── Header ───────────────────────────────────────────────────────────────

    @Test
    fun `output contains DR 2324 comment line`() {
        val csv = write(emptyList())
        assertTrue(csv.contains("DR 2324"), "Expected 'DR 2324' in header comment")
    }

    @Test
    fun `output contains column headers`() {
        val csv = write(emptyList())
        for (col in CsvExporter.HEADER) {
            assertTrue(csv.contains(col), "Expected column header '$col'")
        }
    }

    @Test
    fun `student name comment included when non-blank`() {
        val csv = write(emptyList(), studentName = "Taylor Smith")
        assertTrue(csv.contains("Taylor Smith"))
    }

    @Test
    fun `student name comment omitted when blank`() {
        val csv = write(emptyList(), studentName = "")
        assertTrue(!csv.contains("# Student:"))
    }

    // ── Data rows ────────────────────────────────────────────────────────────

    @Test
    fun `single row is written with correct fields`() {
        val csv = write(listOf(row(
            date               = "06/15/2025",
            drivingTime        = "1h 30m",
            nightDriving       = "0h 20m",
            supervisorInitials = "BP",
            comments           = "Highway practice",
        )))
        assertTrue(csv.contains("06/15/2025"))
        assertTrue(csv.contains("1h 30m"))
        assertTrue(csv.contains("0h 20m"))
        assertTrue(csv.contains("BP"))
        assertTrue(csv.contains("Highway practice"))
    }

    @Test
    fun `multiple rows are all present in output`() {
        val rows = listOf(
            row(date = "01/01/2025", totalMinutes = 45),
            row(date = "02/14/2025", totalMinutes = 60),
            row(date = "03/31/2025", totalMinutes = 90),
        )
        val csv = write(rows)
        assertTrue(csv.contains("01/01/2025"))
        assertTrue(csv.contains("02/14/2025"))
        assertTrue(csv.contains("03/31/2025"))
    }

    @Test
    fun `empty night driving field produces empty column`() {
        val csv = write(listOf(row(nightDriving = "", nightMinutes = 0)))
        // The night column should be present but empty between commas
        val lines = csv.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val dataLine = lines.drop(1).firstOrNull() // skip header row
        assertTrue(dataLine != null && dataLine.contains(",,"),
            "Expected empty night-driving column (,,): $dataLine")
    }

    // ── Grand-total row ──────────────────────────────────────────────────────

    @Test
    fun `grand total row is present`() {
        val csv = write(listOf(row(totalMinutes = 90, nightMinutes = 30)))
        assertTrue(csv.contains("GRAND TOTAL"))
    }

    @Test
    fun `grand total driving time sums all rows`() {
        val rows = listOf(
            row(totalMinutes = 60),
            row(totalMinutes = 90),
            row(totalMinutes = 30),
        )
        val csv = write(rows)
        // 60+90+30 = 180 min = 3h 0m
        assertTrue(csv.contains("3h 0m"), "Expected '3h 0m' grand total in:\n$csv")
    }

    @Test
    fun `grand total night driving time sums night rows`() {
        val rows = listOf(
            row(totalMinutes = 90, nightMinutes = 30),
            row(totalMinutes = 60, nightMinutes = 0),
            row(totalMinutes = 45, nightMinutes = 45),
        )
        val csv = write(rows)
        // 30+45 = 75 min = 1h 15m
        assertTrue(csv.contains("1h 15m"), "Expected '1h 15m' night grand total in:\n$csv")
    }

    @Test
    fun `grand total is correct for empty list`() {
        val csv = write(emptyList())
        // 0 minutes each
        assertTrue(csv.contains("0h 0m"))
    }

    // ── CSV escaping ─────────────────────────────────────────────────────────

    @Test
    fun `field containing comma is quoted`() {
        val line = CsvExporter.toCsvLine(listOf("Smith, Jr."))
        assertEquals("\"Smith, Jr.\"\n", line)
    }

    @Test
    fun `field containing double-quote is escaped`() {
        val line = CsvExporter.toCsvLine(listOf("He said \"hi\""))
        assertEquals("\"He said \"\"hi\"\"\"\n", line)
    }

    @Test
    fun `plain field is not quoted`() {
        val line = CsvExporter.toCsvLine(listOf("JD"))
        assertEquals("JD\n", line)
    }

    @Test
    fun `multiple fields separated by commas`() {
        val line = CsvExporter.toCsvLine(listOf("A", "B", "C"))
        assertEquals("A,B,C\n", line)
    }

    // ── DriveLogRow.from() mapping ────────────────────────────────────────────

    @Test
    fun `DriveLogRow from maps date to MM-dd-yyyy format`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 6, 15),
            startTime          = java.time.LocalDateTime.of(2025, 6, 15, 9, 0),
            endTime            = java.time.LocalDateTime.of(2025, 6, 15, 10, 30),
            totalMinutes       = 90,
            nightMinutes       = 0,
            supervisorName     = "Jane Doe",
            supervisorInitials = "JD",
            comments           = null,
        )
        val row = DriveLogRow.from(session)
        assertEquals("06/15/2025", row.date)
        assertEquals("1h 30m", row.drivingTime)
        assertEquals("", row.nightDriving)
        assertEquals("JD", row.supervisorInitials)
        assertEquals("", row.comments)
    }

    @Test
    fun `DriveLogRow from maps non-zero night minutes`() {
        val session = com.codrivelog.app.data.model.DriveSession(
            date               = LocalDate.of(2025, 3, 1),
            startTime          = java.time.LocalDateTime.of(2025, 3, 1, 20, 0),
            endTime            = java.time.LocalDateTime.of(2025, 3, 1, 21, 15),
            totalMinutes       = 75,
            nightMinutes       = 45,
            supervisorName     = "Bob Parent",
            supervisorInitials = "BP",
            comments           = "Night drive",
        )
        val row = DriveLogRow.from(session)
        assertEquals("0h 45m", row.nightDriving)
        assertEquals("Night drive", row.comments)
    }
}
