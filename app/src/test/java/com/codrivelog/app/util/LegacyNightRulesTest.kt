package com.codrivelog.app.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The earlier rules must give exactly what earlier app versions stored. The
 * expected values are the ones the NightMinutesCalculatorTest of each
 * version asserted (Denver, UTC times):
 * - 1.0.0-1.0.2: `git show 5f0380f~1:app/src/test/.../NightMinutesCalculatorTest.kt`
 * - 1.0.3-1.1.0: `git show 5f0380f:app/src/test/.../NightMinutesCalculatorTest.kt`
 */
class LegacyNightRulesTest {

    private val lat = 39.7392
    private val lng = -104.9903
    private val winter = LocalDate.of(2025, 12, 21)
    private val summer = LocalDate.of(2025, 6, 21)

    @Test
    fun `rule before 1_0_3 reproduces that version's results`() {
        val rule = LegacyNightRules.BEFORE_1_0_3
        assertNear(780, compute(winter.atTime(1, 0), winter.atTime(14, 0), rule))
        assertNear(78, compute(winter.atTime(13, 0), winter.atTime(16, 0), rule))
        assertNear(51, compute(winter.atTime(23, 0), winter.plusDays(1).atTime(0, 30), rule))
        assertNear(877, compute(winter.atTime(23, 40), winter.plusDays(1).atTime(14, 17), rule), tolerance = 5)
        assertNear(0, compute(summer.atTime(12, 0), summer.atTime(23, 0), rule))
    }

    @Test
    fun `rule before 1_0_3 counted the summer evening before sunset as night`() {
        // The bug fixed in 1.0.3: 01:00-02:29 UTC (7:00-8:29 pm MDT) is daylight.
        assertNear(600, compute(summer.atTime(1, 0), summer.atTime(11, 0), LegacyNightRules.BEFORE_1_0_3))
    }

    @Test
    fun `buffered rule of 1_0_3 reproduces that version's results`() {
        val rule = LegacyNightRules.BUFFERED_1_0_3
        assertNear(738, compute(winter.atTime(1, 0), winter.atTime(14, 0), rule))
        assertNear(18, compute(winter.atTime(13, 0), winter.atTime(16, 0), rule))
        assertNear(0, compute(winter.atTime(23, 0), winter.plusDays(1).atTime(0, 30), rule))
        assertNear(759, compute(winter.atTime(23, 40), winter.plusDays(1).atTime(14, 17), rule), tolerance = 5)
        assertNear(422, compute(summer.atTime(1, 0), summer.atTime(11, 0), rule))
    }

    private fun compute(start: LocalDateTime, end: LocalDateTime, rule: NightMinutesCalculator.NightRule) =
        NightMinutesCalculator.computeNightMinutes(start, end, lat, lng, rule)

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 2) {
        assertTrue(kotlin.math.abs(actual - expected) <= tolerance, "expected ~$expected but got $actual")
    }
}
