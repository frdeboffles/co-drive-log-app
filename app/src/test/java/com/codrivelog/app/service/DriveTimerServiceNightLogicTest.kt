package com.codrivelog.app.service

import com.codrivelog.app.util.SunCalculator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DriveTimerServiceNightLogicTest {

    private val denverLat = 39.7392
    private val denverLng = -104.9903

    @Test
    fun `denver winter is night before sunrise in UTC`() {
        val date = LocalDate.of(2025, 12, 21)
        val sunTimes = SunCalculator.calculate(denverLat, denverLng, date)
        val previousDaySunTimes = SunCalculator.calculate(denverLat, denverLng, date.minusDays(1))

        val nowUtc = date.atTime(13, 0)
        assertTrue(isNightUtc(nowUtc, sunTimes, previousDaySunTimes))
    }

    @Test
    fun `denver winter is day between sunrise and sunset in UTC`() {
        val date = LocalDate.of(2025, 12, 21)
        val sunTimes = SunCalculator.calculate(denverLat, denverLng, date)
        val previousDaySunTimes = SunCalculator.calculate(denverLat, denverLng, date.minusDays(1))

        val nowUtc = date.atTime(18, 0)
        assertFalse(isNightUtc(nowUtc, sunTimes, previousDaySunTimes))
    }
}
