package com.codrivelog.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [ElapsedTimeFormatter].
 *
 * Covers zero, sub-minute, single-minute, multi-minute, single-hour,
 * multi-hour, and large values for both [ElapsedTimeFormatter.formatHms]
 * and [ElapsedTimeFormatter.formatHm].
 */
class ElapsedTimeFormatterTest {

    // ---- formatHms ----

    @Test
    fun `formatHms zero seconds`() =
        assertEquals("0:00:00", ElapsedTimeFormatter.formatHms(0L))

    @Test
    fun `formatHms one second`() =
        assertEquals("0:00:01", ElapsedTimeFormatter.formatHms(1L))

    @Test
    fun `formatHms fifty-nine seconds`() =
        assertEquals("0:00:59", ElapsedTimeFormatter.formatHms(59L))

    @Test
    fun `formatHms exactly one minute`() =
        assertEquals("0:01:00", ElapsedTimeFormatter.formatHms(60L))

    @Test
    fun `formatHms one minute five seconds`() =
        assertEquals("0:01:05", ElapsedTimeFormatter.formatHms(65L))

    @Test
    fun `formatHms fifty-nine minutes fifty-nine seconds`() =
        assertEquals("0:59:59", ElapsedTimeFormatter.formatHms(3599L))

    @Test
    fun `formatHms exactly one hour`() =
        assertEquals("1:00:00", ElapsedTimeFormatter.formatHms(3600L))

    @Test
    fun `formatHms one hour one minute one second`() =
        assertEquals("1:01:01", ElapsedTimeFormatter.formatHms(3661L))

    @Test
    fun `formatHms ten hours`() =
        assertEquals("10:00:00", ElapsedTimeFormatter.formatHms(36000L))

    @Test
    fun `formatHms fifty hours`() =
        assertEquals("50:00:00", ElapsedTimeFormatter.formatHms(180_000L))

    @ParameterizedTest
    @CsvSource(
        "0,    0:00:00",
        "59,   0:00:59",
        "60,   0:01:00",
        "3599, 0:59:59",
        "3600, 1:00:00",
        "3661, 1:01:01",
    )
    fun `formatHms parameterised`(seconds: Long, expected: String) =
        assertEquals(expected, ElapsedTimeFormatter.formatHms(seconds))

    @Test
    fun `formatHms negative seconds throws`() {
        assertThrows<IllegalArgumentException> {
            ElapsedTimeFormatter.formatHms(-1L)
        }
    }

    // ---- formatHm ----

    @Test
    fun `formatHm zero seconds`() =
        assertEquals("0:00", ElapsedTimeFormatter.formatHm(0L))

    @Test
    fun `formatHm fifty-nine seconds truncates to zero minutes`() =
        assertEquals("0:00", ElapsedTimeFormatter.formatHm(59L))

    @Test
    fun `formatHm exactly one minute`() =
        assertEquals("0:01", ElapsedTimeFormatter.formatHm(60L))

    @Test
    fun `formatHm fifty-nine minutes`() =
        assertEquals("0:59", ElapsedTimeFormatter.formatHm(3599L))

    @Test
    fun `formatHm exactly one hour`() =
        assertEquals("1:00", ElapsedTimeFormatter.formatHm(3600L))

    @Test
    fun `formatHm one hour thirty minutes`() =
        assertEquals("1:30", ElapsedTimeFormatter.formatHm(5400L))

    @Test
    fun `formatHm ten hours`() =
        assertEquals("10:00", ElapsedTimeFormatter.formatHm(36000L))

    @ParameterizedTest
    @CsvSource(
        "0,    0:00",
        "59,   0:00",
        "60,   0:01",
        "3599, 0:59",
        "3600, 1:00",
        "5400, 1:30",
    )
    fun `formatHm parameterised`(seconds: Long, expected: String) =
        assertEquals(expected, ElapsedTimeFormatter.formatHm(seconds))

    @Test
    fun `formatHm negative seconds throws`() {
        assertThrows<IllegalArgumentException> {
            ElapsedTimeFormatter.formatHm(-1L)
        }
    }
}
