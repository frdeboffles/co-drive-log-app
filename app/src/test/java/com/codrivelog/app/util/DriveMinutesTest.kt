package com.codrivelog.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DriveMinutesTest {

    private val denver = ZoneId.of("America/Denver")
    private val location = NightRuleRecalculation.DEFAULT_LOCATION

    @Test
    fun `an hour after sunset is all night`() {
        // 2025-12-21 in Denver: sunset ≈ 4:39 pm MST.
        val m = DriveMinutes.fromLocal(at(2025, 12, 21, 17, 0), at(2025, 12, 21, 18, 0), denver, location)

        assertEquals(DriveMinutes.Minutes(total = 60, night = 60), m)
    }

    @Test
    fun `without a location only the total is computed`() {
        val m = DriveMinutes.fromLocal(at(2025, 12, 21, 17, 0), at(2025, 12, 21, 18, 0), denver, location = null)

        assertEquals(DriveMinutes.Minutes(total = 60, night = null), m)
    }

    @Test
    fun `a typed drive through the repeated fall-back hour counts total and night alike`() {
        // Review finding 2: 0:30 MDT to 2:30 MST on 2025-11-02 is 3 hours of
        // real time. Total and night both come from it.
        val m = DriveMinutes.fromLocal(at(2025, 11, 2, 0, 30), at(2025, 11, 2, 2, 30), denver, location)

        assertEquals(DriveMinutes.Minutes(total = 180, night = 180), m)
    }

    @Test
    fun `a drive that ends at an earlier clock time on the fall-back night is short`() {
        // 1:50 MDT to 1:10 MST: 20 minutes.
        val m = DriveMinutes.fromLocal(at(2025, 11, 2, 1, 50), at(2025, 11, 2, 1, 10), denver, location)

        assertEquals(DriveMinutes.Minutes(total = 20, night = 20), m)
    }

    @Test
    fun `a time in the spring-forward gap is the moment of the change`() {
        // On 2026-03-08 the clock jumps from 2:00 to 3:00 MDT; 2:30 means 3:00
        // MDT. The total grows with the end time and never gains the hour.
        assertEquals(10, DriveMinutes.fromLocal(at(2026, 3, 8, 2, 30), at(2026, 3, 8, 3, 10), denver, null)?.total)
        assertEquals(40, DriveMinutes.fromLocal(at(2026, 3, 8, 2, 30), at(2026, 3, 8, 3, 40), denver, null)?.total)
        assertEquals(180, DriveMinutes.fromLocal(at(2026, 3, 8, 2, 30), at(2026, 3, 8, 6, 0), denver, null)?.total)
        // An end in the gap: 1:30 MST to the change at 3:00 MDT is 30 minutes.
        assertEquals(30, DriveMinutes.fromLocal(at(2026, 3, 8, 1, 30), at(2026, 3, 8, 2, 30), denver, null)?.total)
    }

    @Test
    fun `a drive across the spring-forward change counts real time`() {
        // 01:30 MST to 03:30 MDT is one hour.
        assertEquals(60, DriveMinutes.fromLocal(at(2026, 3, 8, 1, 30), at(2026, 3, 8, 3, 30), denver, null)?.total)
    }

    // ---- typedInterval: the one decision for an end before the start ----

    @Test
    fun `typed end after start is the same day`() {
        assertEquals(at(2025, 12, 21, 17, 0) to at(2025, 12, 21, 18, 0),
            DriveMinutes.typedInterval(LocalDate.of(2025, 12, 21), LocalTime.of(17, 0), LocalTime.of(18, 0), denver))
    }

    @Test
    fun `typed end before start crosses midnight`() {
        assertEquals(at(2025, 12, 21, 23, 0) to at(2025, 12, 22, 1, 0),
            DriveMinutes.typedInterval(LocalDate.of(2025, 12, 21), LocalTime.of(23, 0), LocalTime.of(1, 0), denver))
    }

    @Test
    fun `typed end before start inside the repeated fall-back hour is the same night`() {
        // Review finding 2: 01:50 to 01:10 on 2025-11-02 is 20 minutes.
        val interval = DriveMinutes.typedInterval(LocalDate.of(2025, 11, 2), LocalTime.of(1, 50), LocalTime.of(1, 10), denver)

        assertEquals(at(2025, 11, 2, 1, 50) to at(2025, 11, 2, 1, 10), interval)
        assertEquals(20, DriveMinutes.fromLocal(interval.first, interval.second, denver, null)?.total)
    }

    @Test
    fun `typed end before start outside the repeated hour crosses midnight even on fall-back day`() {
        assertEquals(at(2025, 11, 2, 1, 50) to at(2025, 11, 3, 0, 30),
            DriveMinutes.typedInterval(LocalDate.of(2025, 11, 2), LocalTime.of(1, 50), LocalTime.of(0, 30), denver))
    }

    @Test
    fun `equal typed times are a 24-hour drive, as before`() {
        assertEquals(at(2025, 12, 21, 10, 0) to at(2025, 12, 22, 10, 0),
            DriveMinutes.typedInterval(LocalDate.of(2025, 12, 21), LocalTime.of(10, 0), LocalTime.of(10, 0), denver))
    }

    @Test
    fun `an end before the start outside the fall-back hour is not a drive`() {
        assertNull(DriveMinutes.fromLocal(at(2025, 12, 21, 18, 0), at(2025, 12, 21, 17, 0), denver, location))
    }

    @Test
    fun `the zone of the local times changes the result`() {
        // The same local times read as UTC fall in the Denver morning daylight.
        val m = DriveMinutes.fromLocal(at(2025, 12, 21, 17, 0), at(2025, 12, 21, 18, 0), ZoneId.of("UTC"), location)

        assertEquals(0, m?.night)
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi)
}
