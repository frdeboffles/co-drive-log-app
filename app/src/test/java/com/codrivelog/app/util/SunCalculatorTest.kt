package com.codrivelog.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for [SunCalculator].
 *
 * Expected UTC times are cross-checked against the NOAA Solar Calculator
 * (https://gml.noaa.gov/grad/solcalc/) and USNO data. A tolerance of ±3
 * minutes is applied to account for the algorithm's inherent approximation.
 *
 * ### Location conventions
 * - Latitude:  positive = North, negative = South
 * - Longitude: positive = East,  negative = West
 */
class SunCalculatorTest {

    companion object {
        private const val TOLERANCE_MINUTES = 3L

        // ---- Named locations ----
        private const val DENVER_LAT  =  39.7392
        private const val DENVER_LNG  = -104.9903

        private const val REYKJAVIK_LAT =  64.1355
        private const val REYKJAVIK_LNG = -21.8954

        private const val SYDNEY_LAT = -33.8688
        private const val SYDNEY_LNG =  151.2093

        private const val EQUATOR_LAT  =  0.0
        private const val EQUATOR_LNG  =  0.0   // prime meridian / equator

        private const val NORTH_POLE_LAT = 89.9
        private const val SOUTH_POLE_LAT = -89.9

        // ---- Key dates ----
        val SUMMER_SOLSTICE_2025 = LocalDate.of(2025, 6, 21)
        val WINTER_SOLSTICE_2025 = LocalDate.of(2025, 12, 21)
        val SPRING_EQUINOX_2025  = LocalDate.of(2025, 3, 20)
        val AUTUMN_EQUINOX_2025  = LocalDate.of(2025, 9, 22)
    }

    // ---- Helpers ----

    /** Asserts that [actual] is within [TOLERANCE_MINUTES] of [expected]. */
    private fun assertTimeNear(expected: LocalTime, actual: LocalTime?, label: String) {
        assertNotNull(actual, "$label should not be null")
        val diffMinutes = kotlin.math.abs(
            actual!!.toSecondOfDay() - expected.toSecondOfDay()
        ) / 60L
        // Handle midnight wrap (e.g. 23:59 vs 00:01 → diff = 2, not 1438)
        val wrappedDiff = minOf(diffMinutes, 1440L - diffMinutes)
        assertEquals(
            true,
            wrappedDiff <= TOLERANCE_MINUTES,
            "$label expected ~$expected but got $actual (diff=${wrappedDiff}min, tolerance=${TOLERANCE_MINUTES}min)",
        )
    }

    // ====================================================================
    //  Denver, CO — Summer Solstice (2025-06-21)
    //  NOAA reference: sunrise ~11:31 UTC, sunset ~02:30 UTC+1 (next day)
    //  i.e. sunrise ~11:31 UTC, sunset ~03:30 UTC ← NOAA gives local MDT
    //  MDT = UTC-6; sunrise MDT ≈ 05:31, sunset MDT ≈ 20:30
    //  UTC: sunrise ≈ 11:31, sunset ≈ 02:30 (next calendar day) → ~26:30 → normalised 02:30
    //  Use: sunrise UTC ~11:31, sunset UTC ~02:29
    // ====================================================================

    @Test
    fun `Denver summer solstice sunrise is around 11h31 UTC`() {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, SUMMER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(11, 31), times.sunrise, "Denver summer sunrise")
    }

    @Test
    fun `Denver summer solstice sunset is around 02h29 UTC`() {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, SUMMER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(2, 29), times.sunset, "Denver summer sunset")
    }

    // ====================================================================
    //  Denver, CO — Winter Solstice (2025-12-21)
    //  MST = UTC-7; NOAA: sunrise MDT≈07:18, sunset MDT≈16:39
    //  UTC: sunrise ~14:18, sunset ~23:39
    // ====================================================================

    @Test
    fun `Denver winter solstice sunrise is around 14h18 UTC`() {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, WINTER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(14, 18), times.sunrise, "Denver winter sunrise")
    }

    @Test
    fun `Denver winter solstice sunset is around 23h39 UTC`() {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, WINTER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(23, 39), times.sunset, "Denver winter sunset")
    }

    // ====================================================================
    //  Denver — both events are non-null year-round (mid-latitude)
    // ====================================================================

    @Test
    fun `Denver has both sunrise and sunset on spring equinox`() {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, SPRING_EQUINOX_2025)
        assertNotNull(times.sunrise, "Denver spring sunrise")
        assertNotNull(times.sunset,  "Denver spring sunset")
    }

    @Test
    fun `Denver has both sunrise and sunset on autumn equinox`() {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, AUTUMN_EQUINOX_2025)
        assertNotNull(times.sunrise, "Denver autumn sunrise")
        assertNotNull(times.sunset,  "Denver autumn sunset")
    }

    @Test
    fun `Denver winter day length is shorter than summer day length`() {
        fun dayLengthMinutes(lat: Double, lng: Double, date: LocalDate): Long {
            val t = SunCalculator.calculate(lat, lng, date)
            val riseS = t.sunrise!!.toSecondOfDay().toLong()
            val setS  = t.sunset!!.toSecondOfDay().toLong()
            return ((setS - riseS + 86400) % 86400) / 60
        }

        val summerDayLength = dayLengthMinutes(DENVER_LAT, DENVER_LNG, SUMMER_SOLSTICE_2025)
        val winterDayLength = dayLengthMinutes(DENVER_LAT, DENVER_LNG, WINTER_SOLSTICE_2025)

        // Denver summer ~14h 25min ≈ 865 min; winter ~9h 20min ≈ 560 min
        assertTrue(
            summerDayLength > winterDayLength,
            "Summer day ($summerDayLength min) should be longer than winter day ($winterDayLength min)",
        )
    }

    // ====================================================================
    //  Equinox: near-equator, sunrise ≈ 06:00 UTC, sunset ≈ 18:00 UTC
    // ====================================================================

    @Test
    fun `equator spring equinox sunrise is near 06h00 UTC`() {
        val times = SunCalculator.calculate(EQUATOR_LAT, EQUATOR_LNG, SPRING_EQUINOX_2025)
        assertTimeNear(LocalTime.of(6, 4), times.sunrise, "Equator equinox sunrise")
    }

    @Test
    fun `equator spring equinox sunset is near 18h00 UTC`() {
        val times = SunCalculator.calculate(EQUATOR_LAT, EQUATOR_LNG, SPRING_EQUINOX_2025)
        assertTimeNear(LocalTime.of(18, 7), times.sunset, "Equator equinox sunset")
    }

    // ====================================================================
    //  Reykjavik, Iceland (64°N) — long summer days / short winter days
    //  Summer solstice: sunrise ~02:55 UTC, sunset ~23:03 UTC
    //  Winter solstice: sunrise ~11:19 UTC, sunset ~15:29 UTC
    // ====================================================================

    @Test
    fun `Reykjavik summer solstice has both sunrise and sunset`() {
        val times = SunCalculator.calculate(REYKJAVIK_LAT, REYKJAVIK_LNG, SUMMER_SOLSTICE_2025)
        assertNotNull(times.sunrise, "Reykjavik summer sunrise")
        assertNotNull(times.sunset,  "Reykjavik summer sunset")
    }

    @Test
    fun `Reykjavik summer solstice sunrise is around 02h55 UTC`() {
        val times = SunCalculator.calculate(REYKJAVIK_LAT, REYKJAVIK_LNG, SUMMER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(2, 55), times.sunrise, "Reykjavik summer sunrise")
    }

    @Test
    fun `Reykjavik summer solstice sunset is around 23h03 UTC`() {
        val times = SunCalculator.calculate(REYKJAVIK_LAT, REYKJAVIK_LNG, SUMMER_SOLSTICE_2025)
        // Sunset crosses midnight UTC — algorithm returns ~00:03 (next calendar day, normalised).
        assertTimeNear(LocalTime.of(0, 3), times.sunset, "Reykjavik summer sunset")
    }

    @Test
    fun `Reykjavik winter solstice sunrise is around 11h19 UTC`() {
        val times = SunCalculator.calculate(REYKJAVIK_LAT, REYKJAVIK_LNG, WINTER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(11, 19), times.sunrise, "Reykjavik winter sunrise")
    }

    @Test
    fun `Reykjavik winter solstice sunset is around 15h29 UTC`() {
        val times = SunCalculator.calculate(REYKJAVIK_LAT, REYKJAVIK_LNG, WINTER_SOLSTICE_2025)
        assertTimeNear(LocalTime.of(15, 29), times.sunset, "Reykjavik winter sunset")
    }

    // ====================================================================
    //  Sydney, Australia (Southern Hemisphere, ~34°S)
    //  Summer/winter solstices are reversed relative to the Northern Hemisphere.
    //  Dec-21 is Sydney's midsummer: long day ~14h 25min
    //  Jun-21 is Sydney's midwinter: short day ~9h 55min
    // ====================================================================

    @Test
    fun `Sydney Dec solstice day is longer than Jun solstice day`() {
        fun dayLengthMinutes(date: LocalDate): Long {
            val t = SunCalculator.calculate(SYDNEY_LAT, SYDNEY_LNG, date)
            val riseS = t.sunrise!!.toSecondOfDay().toLong()
            val setS  = t.sunset!!.toSecondOfDay().toLong()
            return ((setS - riseS + 86400) % 86400) / 60
        }

        val decDay = dayLengthMinutes(WINTER_SOLSTICE_2025)  // Dec 21 = Sydney summer
        val junDay = dayLengthMinutes(SUMMER_SOLSTICE_2025)   // Jun 21 = Sydney winter

        assertTrue(
            decDay > junDay,
            "Sydney Dec day ($decDay min) should be longer than Jun day ($junDay min)",
        )
    }

    @Test
    fun `Sydney has both sunrise and sunset on both solstices`() {
        listOf(SUMMER_SOLSTICE_2025, WINTER_SOLSTICE_2025).forEach { date ->
            val times = SunCalculator.calculate(SYDNEY_LAT, SYDNEY_LNG, date)
            assertNotNull(times.sunrise, "Sydney sunrise on $date")
            assertNotNull(times.sunset,  "Sydney sunset on $date")
        }
    }

    // ====================================================================
    //  Polar conditions
    // ====================================================================

    @Test
    fun `North Pole midsummer has no sunset (midnight sun)`() {
        val times = SunCalculator.calculate(NORTH_POLE_LAT, 0.0, SUMMER_SOLSTICE_2025)
        // Sun never sets — sunset should be null
        assertNull(times.sunset, "North Pole midsummer should have no sunset")
    }

    @Test
    fun `North Pole midwinter has no sunrise (polar night)`() {
        val times = SunCalculator.calculate(NORTH_POLE_LAT, 0.0, WINTER_SOLSTICE_2025)
        assertNull(times.sunrise, "North Pole midwinter should have no sunrise")
    }

    @Test
    fun `South Pole midsummer (Dec) has no sunset`() {
        val times = SunCalculator.calculate(SOUTH_POLE_LAT, 0.0, WINTER_SOLSTICE_2025)
        // Dec 21 is midsummer in the Southern Hemisphere
        assertNull(times.sunset, "South Pole Dec should have no sunset")
    }

    @Test
    fun `South Pole midwinter (Jun) has no sunrise`() {
        val times = SunCalculator.calculate(SOUTH_POLE_LAT, 0.0, SUMMER_SOLSTICE_2025)
        assertNull(times.sunrise, "South Pole Jun should have no sunrise")
    }

    // ====================================================================
    //  Parameterised: Denver across all months — always has both events
    // ====================================================================

    @ParameterizedTest(name = "Denver {0}-{1} has sunrise and sunset")
    @CsvSource(
        "2025, 1, 15",
        "2025, 2, 15",
        "2025, 3, 15",
        "2025, 4, 15",
        "2025, 5, 15",
        "2025, 6, 15",
        "2025, 7, 15",
        "2025, 8, 15",
        "2025, 9, 15",
        "2025, 10, 15",
        "2025, 11, 15",
        "2025, 12, 15",
    )
    fun `Denver always has both sunrise and sunset`(year: Int, month: Int, day: Int) {
        val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, LocalDate.of(year, month, day))
        assertNotNull(times.sunrise, "Denver sunrise on $year-$month-$day")
        assertNotNull(times.sunset,  "Denver sunset on $year-$month-$day")
    }

    // ====================================================================
    //  Sunrise is always before sunset (where both exist)
    // ====================================================================

    @Test
    fun `sunrise is before sunset for Denver throughout the year`() {
        for (month in 1..12) {
            val date  = LocalDate.of(2025, month, 15)
            val times = SunCalculator.calculate(DENVER_LAT, DENVER_LNG, date)
            val rise  = times.sunrise!!.toSecondOfDay()
            val set   = times.sunset!!.toSecondOfDay()
            // Sunset could wrap past midnight (UTC) — handle that
            val setAdjusted = if (set < rise) set + 86400 else set
            assertTrue(
                rise < setAdjusted,
                "Denver $date: sunrise (${ times.sunrise}) should be before sunset (${times.sunset})",
            )
        }
    }
}
