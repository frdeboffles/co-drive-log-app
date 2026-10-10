package com.codrivelog.app.util

import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.util.NightRuleRecalculation.Location
import com.codrivelog.app.util.NightRuleRecalculation.RouteSummary
import com.codrivelog.app.util.NightRuleRecalculation.toUtc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class NightSourceClassifierTest {

    private val denver = ZoneId.of("America/Denver")
    private val utc = ZoneId.of("UTC")
    private val zones = listOf(denver)
    private val denverLocation = NightRuleRecalculation.DEFAULT_LOCATION
    private val grandJunction = Location(39.0639, -108.5506)

    // 2025-12-21, 5-6 pm MST; sunset in Denver ≈ 4:39 pm.
    private val winterStart = LocalDateTime.of(2025, 12, 21, 17, 0)
    private val winterEnd = LocalDateTime.of(2025, 12, 21, 18, 0)

    // ---- Manual entries: always the sun at Denver ----

    @Test
    fun `manual entry with a 1_0_3 buffered value is recalculated`() {
        val stored = legacyUtc(LegacyNightRules.BUFFERED_1_0_3, denverLocation)
        assertEquals(21, stored)

        assertSun(classify(manualEntry(stored)), denverLocation, expectedMinutes = 60)
    }

    @Test
    fun `manual entry with a pre-1_0_3 value can go down`() {
        // 2025-06-20, 7-8 pm MDT: daylight, but the pre-1.0.3 rule counted it as night.
        val start = LocalDateTime.of(2025, 6, 20, 19, 0)
        val end = LocalDateTime.of(2025, 6, 20, 20, 0)
        val stored = compute(start.toUtc(denver), end.toUtc(denver), denverLocation, LegacyNightRules.BEFORE_1_0_3)
        assertEquals(60, stored)

        assertSun(classify(manualEntry(stored, start, end)), denverLocation, expectedMinutes = 0)
    }

    @Test
    fun `manual entry is recalculated even when no earlier computation matches`() {
        // Manual entries were always calculated from the sun at Denver.
        assertSun(classify(manualEntry(stored = 37)), denverLocation, expectedMinutes = 60)
    }

    // ---- Timed drives with a fix during the drive: certain ----

    @Test
    fun `GPS drive is recalculated at its last route point`() {
        val stored = legacyUtc(LegacyNightRules.BUFFERED_1_0_3, grandJunction)

        val c = classify(timedDrive(stored), route(count = 2, at = winterEnd, location = grandJunction))

        assertSun(c, grandJunction, expectedMinutes = current(grandJunction))
    }

    @Test
    fun `GPS drive is recalculated even when no earlier computation matches`() {
        val c = classify(timedDrive(stored = 37), route(count = 3, at = winterEnd, location = grandJunction))

        assertSun(c, grandJunction, expectedMinutes = current(grandJunction))
    }

    @Test
    fun `drive whose only route point is the start fix is a GPS drive`() {
        // The timer stamps the start fix with its own now() call, a few
        // milliseconds after the start time.
        val startFix = winterStart.plusSeconds(1).plusNanos(234_567_000)

        val c = classify(timedDrive(stored = 46), route(count = 1, at = startFix, location = grandJunction))

        assertSun(c, grandJunction, expectedMinutes = current(grandJunction))
    }

    @Test
    fun `the final route point gives the exact UTC offset`() {
        // The final point is the end time in UTC: no zone guess needed, even
        // when the guessed zones are wrong.
        val c = NightSourceClassifier.classify(
            timedDrive(stored = 0),
            route(count = 2, at = winterEnd, location = grandJunction),
            listOf(utc),
        )

        assertEquals(ZoneOffset.ofHours(-7), c.zone)
    }

    @Test
    fun `the exact offset is stored as the region zone that has it`() {
        // Review finding 3: a later edit into summer must use daylight saving.
        val c = NightSourceClassifier.classify(
            timedDrive(stored = 0),
            route(count = 2, at = winterEnd, location = grandJunction),
            listOf(denver, utc),
        )

        assertEquals(denver, c.zone)
        assertEquals(current(grandJunction), c.nightMinutes)
    }

    // ---- Timed drives with only the final route point: uncertain ----

    @Test
    fun `manual-switch drive with only the final route point keeps its value`() {
        val c = classify(timedDrive(stored = 45), route(count = 1, at = winterEnd, location = denverLocation))

        assertKept(c)
    }

    @Test
    fun `coarse-fix drive away from Denver is recognized at its final point`() {
        // Review finding 1: every fix was too coarse to record, but the timer
        // used them for the sun calculation; only the forced final point exists.
        val stored = legacyUtc(LegacyNightRules.BUFFERED_1_0_3, grandJunction)

        val c = classify(timedDrive(stored), route(count = 1, at = winterEnd, location = grandJunction))

        assertSun(c, grandJunction, expectedMinutes = current(grandJunction))
    }

    // ---- Timed drives without route points: uncertain ----

    @Test
    fun `timed drive without GPS edited at Denver is recalculated`() {
        val stored = legacyUtc(LegacyNightRules.BUFFERED_1_0_3, denverLocation)

        assertSun(classify(timedDrive(stored)), denverLocation, expectedMinutes = 60)
    }

    @Test
    fun `timed drive without GPS and 0 night minutes keeps them`() {
        assertKept(classify(timedDrive(stored = 0)))
    }

    @Test
    fun `timed drive without GPS whose value matches nothing keeps it`() {
        assertKept(classify(timedDrive(stored = 45)))
    }

    @Test
    fun `a switch value is never lowered by a match`() {
        // Review finding 2: June, 5-6 am MDT with the switch on stores 60. The
        // pre-1.0.3 rule on local times also gives 60 at Denver (the date is
        // within that variant's range), but the current rule gives about 31
        // (sunrise ≈ 5:31 am). Keep the 60.
        val start = LocalDateTime.of(2025, 6, 20, 5, 0)
        val end = LocalDateTime.of(2025, 6, 20, 6, 0)
        assertEquals(60, compute(start, end, denverLocation, LegacyNightRules.BEFORE_1_0_3))

        assertKept(classify(timedDrive(stored = 60, start = start, end = end)))
    }

    @Test
    fun `a pre-1_0_3 value on a drive dated after 1_0_3 is still recognized`() {
        // Review finding 4: a user who kept 1.0.2 installed into May stored
        // pre-1.0.3 values on May drives. 2026-05-15, 5:30-6:30 am MDT,
        // sunrise ≈ 5:45 am: the old rule counted the whole hour as night.
        val start = LocalDateTime.of(2026, 5, 15, 5, 30)
        val end = LocalDateTime.of(2026, 5, 15, 6, 30)
        val stored = compute(start.toUtc(denver), end.toUtc(denver), denverLocation, LegacyNightRules.BEFORE_1_0_3)

        assertSun(classify(manualEntry(stored, start, end)), denverLocation,
            expectedMinutes = nightAt(start, end, denverLocation, denver)!!)
        assertEquals(denver, classify(manualEntry(stored, start, end)).zone)
    }

    @Test
    fun `old GPS drive across the fall-back hour gets total and night from real time`() {
        // Review finding 4: 1:50 MDT to 1:10 MST on 2025-11-02. The old timer
        // stored a 0 total; it is 20 minutes, all at night.
        val start = LocalDateTime.of(2025, 11, 2, 1, 50, 0, 123_000_000)
        val end = LocalDateTime.of(2025, 11, 2, 1, 10, 0, 456_000_000)
        val drive = timedDrive(stored = 0, start = start, end = end).copy(totalMinutes = 0)
        val route = RouteSummary(2, LocalDateTime.of(2025, 11, 2, 8, 10, 0, 456_000_000), denverLocation)

        val c = classify(drive, route)

        assertTrue(c.recognized)
        assertEquals(DriveMinutes.Minutes(total = 20, night = 20), c.minutes)
        assertEquals(20, c.applyTo(drive).totalMinutes)
    }

    // ---- Zones ----

    @Test
    fun `a drive is matched in the zone it was recorded in`() {
        val stored = legacyUtc(LegacyNightRules.BUFFERED_1_0_3, denverLocation)

        val c = NightSourceClassifier.classify(timedDrive(stored), null, listOf(utc, denver))

        assertSun(c, denverLocation, expectedMinutes = 60)
        assertEquals(denver, c.zone)
    }

    @Test
    fun `an uncertain drive is not recalculated in a zone that does not reproduce its value`() {
        val stored = legacyUtc(LegacyNightRules.BUFFERED_1_0_3, denverLocation)

        assertKept(NightSourceClassifier.classify(timedDrive(stored), null, listOf(utc)))
    }

    @Test
    fun `classifying a thousand uncertain drives stays fast`() {
        // Review finding 6: no sun-time cache is needed. Uncertain drives
        // that match nothing take the longest path: every candidate location,
        // zone and earlier computation.
        val drives = (0 until 1000).map { i ->
            timedDrive(stored = 45, start = winterStart.plusDays(i.toLong()), end = winterEnd.plusDays(i.toLong()))
        }
        val zones = listOf(denver, utc)

        val started = System.nanoTime()
        drives.forEach { NightSourceClassifier.classify(it, null, zones) }
        val millis = (System.nanoTime() - started) / 1_000_000

        assertTrue(millis < 2_000, "1000 drives took $millis ms")
    }

    // ---- Final point detection ----

    @Test
    fun `finalPointOffset ignores a point recorded during the drive`() {
        val periodic = route(count = 1, at = winterEnd.minusSeconds(37), location = denverLocation)

        assertNull(NightSourceClassifier.finalPointOffset(timedDrive(0), periodic))
    }

    // ---- Helpers ----

    private fun classify(session: DriveSession, route: RouteSummary? = null) =
        NightSourceClassifier.classify(session, route, zones)

    private fun assertSun(c: NightSourceClassifier.Classification, location: Location, expectedMinutes: Int) {
        assertTrue(c.recognized, "should be recognized")
        assertEquals(NightSource.SUN, c.source)
        assertEquals(location, c.location)
        assertEquals(expectedMinutes, c.nightMinutes)
    }

    private fun assertKept(c: NightSourceClassifier.Classification) {
        assertFalse(c.recognized, "should not be recognized")
        assertEquals(NightSource.UNKNOWN, c.source)
        assertNull(c.nightMinutes)
    }

    private fun compute(start: LocalDateTime, end: LocalDateTime, location: Location, rule: NightMinutesCalculator.NightRule) =
        NightMinutesCalculator.computeNightMinutes(start, end, location.latitude, location.longitude, rule)

    private fun legacyUtc(rule: NightMinutesCalculator.NightRule, location: Location) =
        compute(winterStart.toUtc(denver), winterEnd.toUtc(denver), location, rule)

    private fun current(location: Location) =
        nightAt(winterStart, winterEnd, location, denver)!!

    private fun manualEntry(stored: Int, start: LocalDateTime = winterStart, end: LocalDateTime = winterEnd) =
        session(stored, isManualEntry = true, start = start, end = end)

    private fun timedDrive(stored: Int, start: LocalDateTime = winterStart, end: LocalDateTime = winterEnd) =
        session(stored, isManualEntry = false, start = start, end = end)

    private fun session(stored: Int, isManualEntry: Boolean, start: LocalDateTime, end: LocalDateTime) = DriveSession(
        id                 = 1,
        date               = start.toLocalDate(),
        startTime          = start,
        endTime            = end,
        totalMinutes       = 60,
        nightMinutes       = stored,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
        isManualEntry      = isManualEntry,
    )

    /** Route summary whose latest point is stamped like the timer does: local time in UTC. */
    private fun route(count: Int, at: LocalDateTime, location: Location) =
        RouteSummary(count, at.toUtc(denver), location)

    private fun nightAt(start: LocalDateTime, end: LocalDateTime, location: Location, zone: ZoneId): Int? =
        DriveMinutes.fromLocal(start, end, zone, location)?.night
}
