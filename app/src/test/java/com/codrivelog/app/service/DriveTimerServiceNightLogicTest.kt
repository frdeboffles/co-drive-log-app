package com.codrivelog.app.service

import com.codrivelog.app.util.NightMinutesCalculator
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
        assertTrue(NightMinutesCalculator.isNightUtc(nowUtc, sunTimes, previousDaySunTimes))
    }

    @Test
    fun `denver winter is day between sunrise and sunset in UTC`() {
        val date = LocalDate.of(2025, 12, 21)
        val sunTimes = SunCalculator.calculate(denverLat, denverLng, date)
        val previousDaySunTimes = SunCalculator.calculate(denverLat, denverLng, date.minusDays(1))

        val nowUtc = date.atTime(18, 0)
        assertFalse(NightMinutesCalculator.isNightUtc(nowUtc, sunTimes, previousDaySunTimes))
    }

    // The hour after sunset and the hour before sunrise were day under the
    // earlier one-hour buffer rule. They are night now.

    @Test
    fun `denver winter is night in the hour before sunrise`() {
        val date = LocalDate.of(2025, 12, 21)
        val nowUtc = date.atTime(13, 50) // sunrise ≈ 14:18 UTC
        assertTrue(NightMinutesCalculator.isNightUtc(nowUtc, sun(date), sun(date.minusDays(1))))
    }

    @Test
    fun `denver winter is night in the hour after sunset`() {
        val date = LocalDate.of(2025, 12, 21)
        val nowUtc = date.atTime(23, 50) // sunset ≈ 23:39 UTC
        assertTrue(NightMinutesCalculator.isNightUtc(nowUtc, sun(date), sun(date.minusDays(1))))
    }

    @Test
    fun `denver summer evening is day until sunset after UTC midnight`() {
        val date = LocalDate.of(2025, 6, 21)
        val nowUtc = date.atTime(1, 0) // 7:00 pm MDT; sunset ≈ 02:29 UTC
        assertFalse(NightMinutesCalculator.isNightUtc(nowUtc, sun(date), sun(date.minusDays(1))))
    }

    @Test
    fun `denver summer is night in the hour after sunset`() {
        val date = LocalDate.of(2025, 6, 21)
        val nowUtc = date.atTime(3, 0) // 9:00 pm MDT; sunset ≈ 02:29 UTC
        assertTrue(NightMinutesCalculator.isNightUtc(nowUtc, sun(date), sun(date.minusDays(1))))
    }

    private fun sun(date: LocalDate) = SunCalculator.calculate(denverLat, denverLng, date)
}
