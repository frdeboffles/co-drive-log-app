package com.codrivelog.core.dr2324

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class Dr2324MapperTest {

    private fun session(i: Int, minutes: Int = 60, night: Int = 0): DriveSession =
        DriveSession(
            date = LocalDate.of(2026, 1, 1).plusDays(i.toLong()),
            verifierInitials = "AB",
            totalMinutes = minutes,
            nightMinutes = night,
            comments = "session $i",
        )

    @Test
    fun `no sessions still returns one empty page`() {
        val doc = Dr2324Mapper.map(
            studentProfile = StudentProfile("Test Student", "P123"),
            sessions = emptyList(),
        )

        assertEquals(1, doc.pages.size)
        assertTrue(doc.pages.first().rows.isEmpty())
        assertEquals(0, doc.grandTotalMinutes)
        assertEquals(0, doc.grandNightMinutes)
    }

    @Test
    fun `exactly rows per page creates one page`() {
        val sessions = (0 until Dr2324Mapper.ROWS_PER_PAGE).map { session(it, 30, 10) }
        val doc = Dr2324Mapper.map(StudentProfile("A"), sessions)

        assertEquals(1, doc.pages.size)
        assertEquals(Dr2324Mapper.ROWS_PER_PAGE, doc.pages.first().rows.size)
        assertEquals(Dr2324Mapper.ROWS_PER_PAGE * 30, doc.grandTotalMinutes)
        assertEquals(Dr2324Mapper.ROWS_PER_PAGE * 10, doc.grandNightMinutes)
    }

    @Test
    fun `rows per page plus one creates two pages`() {
        val total = Dr2324Mapper.ROWS_PER_PAGE + 1
        val sessions = (0 until total).map { session(it, 45, 15) }
        val doc = Dr2324Mapper.map(StudentProfile("A"), sessions)

        assertEquals(2, doc.pages.size)
        assertEquals(Dr2324Mapper.ROWS_PER_PAGE, doc.pages[0].rows.size)
        assertEquals(1, doc.pages[1].rows.size)
        assertEquals(total * 45, doc.grandTotalMinutes)
        assertEquals(total * 15, doc.grandNightMinutes)
    }

    @Test
    fun `large totals are summed correctly`() {
        val sessions = listOf(
            session(0, minutes = 6000, night = 1200),
            session(1, minutes = 7000, night = 1400),
            session(2, minutes = 8000, night = 1600),
        )
        val doc = Dr2324Mapper.map(StudentProfile("A"), sessions)

        assertEquals(21000, doc.grandTotalMinutes)
        assertEquals(4200, doc.grandNightMinutes)
        assertEquals("350h 0m", Dr2324Formatters.formatTime(doc.grandTotalMinutes))
        assertEquals("70h 0m", Dr2324Formatters.formatTime(doc.grandNightMinutes))
    }

    @Test
    fun `sessions on the same day are aggregated into one row`() {
        val date = LocalDate.of(2026, 3, 4)
        val sessions = listOf(
            DriveSession(date = date, verifierInitials = "CGD", totalMinutes = 60, nightMinutes = 0, comments = "A"),
            DriveSession(date = date, verifierInitials = "FRD", totalMinutes = 30, nightMinutes = 9, comments = "B"),
        )

        val doc = Dr2324Mapper.map(StudentProfile("A"), sessions)
        val row = doc.pages.single().rows.single()

        assertEquals("03/04/2026", row.date)
        assertEquals("CGD/FRD", row.verifierInitials)
        assertEquals("1h 30m", row.drivingTime)
        assertEquals("0h 9m", row.nightDrivingTime)
        assertEquals("A | B", row.comments)
        assertEquals(90, row.totalMinutes)
        assertEquals(9, row.nightMinutes)
    }

    @Test
    fun `aggregated row with no night minutes shows zero padded night text`() {
        val date = LocalDate.of(2026, 3, 4)
        val sessions = listOf(
            DriveSession(date = date, verifierInitials = "CGD", totalMinutes = 33, nightMinutes = 0, comments = "seeded"),
            DriveSession(date = date, verifierInitials = "CGD", totalMinutes = 27, nightMinutes = 0, comments = "entry"),
        )

        val doc = Dr2324Mapper.map(StudentProfile("A"), sessions)
        val row = doc.pages.single().rows.single()

        assertEquals("1h 0m", row.drivingTime)
        assertEquals("0h 00m", row.nightDrivingTime)
        assertEquals(0, row.nightMinutes)
    }
}
