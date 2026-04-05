package com.codrivelog.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Unit tests for [NightMinutesCalculator].
 *
 * ### Strategy
 * Because [SunCalculator] is an `object` (no injection seam), tests rely on
 * known UTC sunrise/sunset times for specific real-world locations and dates
 * (cross-checked against NOAA data) and assert at a tolerance of ±2 minutes.
 *
 * Location constants match those used in [SunCalculatorTest].
 *
 * ### Key test scenarios
 * 1. Entirely daytime drive → 0 night minutes
 * 2. Entirely night-time drive → all minutes are night
 * 3. Drive crossing sunset+1h boundary (day → night transition)
 * 4. Drive crossing sunrise-1h boundary (night → day transition)
 * 5. Drive that spans exactly sunrise and sunset (bookend night windows)
 * 6. Drive spanning midnight (multi-day calculation)
 * 7. Zero-duration drive → 0 night minutes
 * 8. Polar night location → all minutes are night
 * 9. [buildNightWindows] / [overlapSeconds] internals
 */
class NightMinutesCalculatorTest {

    companion object {
        // Denver, CO (UTC −6 MDT / −7 MST)
        private const val LAT  =  39.7392
        private const val LNG  = -104.9903

        // 2025-06-21 (summer solstice)
        // NOAA UTC: sunrise ≈ 11:31, sunset ≈ 02:29 next day (i.e. 26:29 → 02:29 UTC)
        // This means on June 21 UTC the night window is [00:00, 11:31) and no post-sunset
        // window within the same calendar day (sunset wraps to June 22).
        private val SOLSTICE_DATE  = LocalDate.of(2025, 6, 21)

        // 2025-12-21 (winter solstice)
        // NOAA UTC: sunrise ≈ 14:18, sunset ≈ 23:39
        private val WINTER_DATE    = LocalDate.of(2025, 12, 21)

        private const val TOLERANCE_MINUTES = 2
    }

    // ---- overlapSeconds internal ----

    @Test
    fun `overlapSeconds non-overlapping intervals returns 0`() {
        val base = LocalDate.of(2025, 6, 1)
        val a1   = base.atTime(8, 0)
        val a2   = base.atTime(9, 0)
        val b1   = base.atTime(10, 0)
        val b2   = base.atTime(11, 0)
        assertEquals(0L, NightMinutesCalculator.overlapSeconds(a1, a2, b1, b2))
    }

    @Test
    fun `overlapSeconds adjacent intervals returns 0`() {
        val base = LocalDate.of(2025, 6, 1)
        val a1   = base.atTime(8, 0)
        val a2   = base.atTime(9, 0)
        assertEquals(0L, NightMinutesCalculator.overlapSeconds(a1, a2, a2, base.atTime(10, 0)))
    }

    @Test
    fun `overlapSeconds partial overlap`() {
        val base  = LocalDate.of(2025, 6, 1)
        val a1    = base.atTime(8, 0)
        val a2    = base.atTime(10, 0)
        val b1    = base.atTime(9, 0)
        val b2    = base.atTime(11, 0)
        assertEquals(3600L, NightMinutesCalculator.overlapSeconds(a1, a2, b1, b2))
    }

    @Test
    fun `overlapSeconds a fully inside b`() {
        val base  = LocalDate.of(2025, 6, 1)
        val a1    = base.atTime(9, 0)
        val a2    = base.atTime(10, 0)
        val b1    = base.atTime(8, 0)
        val b2    = base.atTime(11, 0)
        assertEquals(3600L, NightMinutesCalculator.overlapSeconds(a1, a2, b1, b2))
    }

    // ---- buildNightWindows internal ----

    @Test
    fun `buildNightWindows polar night (both null) returns full day window`() {
        val date = LocalDate.of(2025, 6, 21)
        val midnight     = date.atStartOfDay()
        val nextMidnight = date.plusDays(1).atStartOfDay()
        val windows = NightMinutesCalculator.buildNightWindows(
            midnight     = midnight,
            nextMidnight = nextMidnight,
            previousSunset = null,
            sunrise      = null,
            sunset       = null,
        )
        assertEquals(1, windows.size)
        assertEquals(midnight     to nextMidnight, windows[0])
    }

    @Test
    fun `buildNightWindows midnight sun (sunset null) returns empty list`() {
        val date = LocalDate.of(2025, 6, 21)
        val windows = NightMinutesCalculator.buildNightWindows(
            midnight     = date.atStartOfDay(),
            nextMidnight = date.plusDays(1).atStartOfDay(),
            previousSunset = null,
            sunrise      = date.atTime(LocalTime.of(2, 0)),
            sunset       = null,
        )
        assertTrue(windows.isEmpty(), "Midnight sun should produce no night windows")
    }

    @Test
    fun `buildNightWindows normal day has two windows`() {
        val date         = LocalDate.of(2025, 12, 21)
        val midnight     = date.atStartOfDay()
        val nextMidnight = date.plusDays(1).atStartOfDay()
        val sunrise      = date.atTime(LocalTime.of(13, 18)) // sunrise-1h
        val sunset       = date.plusDays(1).atTime(LocalTime.of(0, 39)) // sunset+1h wraps
        val windows      = NightMinutesCalculator.buildNightWindows(
            midnight     = midnight,
            nextMidnight = nextMidnight,
            previousSunset = date.atTime(LocalTime.of(0, 39)),
            sunrise      = sunrise,
            sunset       = sunset,
        )
        assertEquals(1, windows.size, "Adjusted winter UTC day has one night window")
        assertEquals(date.atTime(LocalTime.of(0, 39)) to sunrise, windows[0])
    }

    // ---- computeNightMinutesForSession: zero-duration ----

    @Test
    fun `zero-duration session returns 0 night minutes`() {
        val now = WINTER_DATE.atTime(20, 0)  // post-sunset → night, but zero duration
        assertEquals(0, NightMinutesCalculator.computeNightMinutesForSession(
            start        = now,
            end          = now,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        ))
    }

    // ---- computeNightMinutesForSession: invalid args ----

    @Test
    fun `end before start throws IllegalArgumentException`() {
        val base = WINTER_DATE.atTime(10, 0)
        assertThrows<IllegalArgumentException> {
            NightMinutesCalculator.computeNightMinutesForSession(
                start        = base,
                end          = base.minusMinutes(1),
                latitudeDeg  = LAT,
                longitudeDeg = LNG,
            )
        }
    }

    // ---- Winter-solstice Denver: UTC sunrise ~14:18, sunset ~23:39 ----
    // Colorado-adjusted night windows: [00:39, 13:18) and [00:39 next day, ...)

    @Test
    fun `drive entirely during daytime has 0 night minutes (winter)`() {
        val start = WINTER_DATE.atTime(15, 0)   // 15:00 UTC — after sunrise
        val end   = WINTER_DATE.atTime(23, 0)   // 23:00 UTC — before sunset
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        assertEquals(0, result, "Daytime drive should have 0 night minutes")
    }

    @Test
    fun `drive entirely before sunrise is fully night (winter)`() {
        val start  = WINTER_DATE.atTime(1, 0)    // 01:00 UTC
        val end    = WINTER_DATE.atTime(14, 0)   // 14:00 UTC — before sunrise ~14:18
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        // 01:00 → 14:00; adjusted sunrise boundary is ~13:18 and night starts ~00:39.
        assertNear(738, result, "Only adjusted-night portion should count")
    }

    @Test
    fun `drive crossing sunrise accumulates only pre-sunrise portion (winter)`() {
        // 13:00 → 16:00 UTC. Adjusted sunrise boundary ≈ 13:18. Night = 13:00→13:18 = 18 min.
        val start  = WINTER_DATE.atTime(13, 0)
        val end    = WINTER_DATE.atTime(16, 0)
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        assertNear(18, result, "Only adjusted pre-sunrise portion should be night")
    }

    @Test
    fun `drive crossing sunset accumulates only post-sunset portion (winter)`() {
        // 23:00 → 00:30 next day. Adjusted sunset boundary ≈ 00:39 next day.
        // Entire interval is before the night boundary.
        val start  = WINTER_DATE.atTime(23, 0)
        val end    = WINTER_DATE.plusDays(1).atTime(0, 30)
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        assertNear(0, result, "Before adjusted post-sunset boundary should be day")
    }

    @Test
    fun `drive spanning full night window (winter solstice)`() {
        // 23:40 UTC Dec 21 → 14:17 UTC Dec 22.
        // Adjusted Dec 22 night window is roughly 00:39→13:18 (~759 min).
        val start = WINTER_DATE.atTime(23, 40)
        val end   = WINTER_DATE.plusDays(1).atTime(14, 17)
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        assertNear(759, result, "Drive spanning adjusted full night should accumulate night minutes", tolerance = 5)
    }

    // ---- Summer solstice Denver: UTC sunrise ≈ 11:31, sunset wraps to next day ≈ 02:29 UTC ----
    // Colorado-adjusted UTC night window is approximately [03:29, 10:31)

    @Test
    fun `drive entirely during long summer day has 0 night minutes`() {
        val start  = SOLSTICE_DATE.atTime(12, 0)  // after sunrise
        val end    = SOLSTICE_DATE.atTime(23, 0)  // still daytime (sunset is next day UTC)
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        assertEquals(0, result, "Drive during long summer day should have 0 night minutes")
    }

    @Test
    fun `drive before sunrise on summer solstice is fully night`() {
        val start  = SOLSTICE_DATE.atTime(1, 0)
        val end    = SOLSTICE_DATE.atTime(11, 0)   // before sunrise ~11:31
        val result = NightMinutesCalculator.computeNightMinutesForSession(
            start        = start,
            end          = end,
            latitudeDeg  = LAT,
            longitudeDeg = LNG,
        )
        assertNear(422, result, "Only adjusted-night portion should count in summer")
    }

    // ---- Helpers ----

    private fun assertNear(expected: Int, actual: Int, message: String, tolerance: Int = TOLERANCE_MINUTES) {
        assertTrue(
            kotlin.math.abs(actual - expected) <= tolerance,
            "$message: expected ~$expected min but got $actual min (tolerance ±$tolerance min)",
        )
    }
}
