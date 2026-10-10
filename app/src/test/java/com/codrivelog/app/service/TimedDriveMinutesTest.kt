package com.codrivelog.app.service

import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.util.NightRuleRecalculation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class TimedDriveMinutesTest {

    @Test
    fun `a drive across the fall-back hour counts its real duration`() {
        // Review finding 2: 0:30 MDT to 1:45 MST on 2025-11-02 is 135 minutes
        // of real time, all at night. Local times would have given 75.
        val start = Instant.parse("2025-11-02T06:30:00Z") // 0:30 MDT
        val end = Instant.parse("2025-11-02T08:45:00Z")   // 1:45 MST

        val (total, night) = timedDriveMinutes(start, end, NightRuleRecalculation.DEFAULT_LOCATION, 0)

        assertEquals(135, total)
        assertEquals(135, night)
    }

    @Test
    fun `night source is MANUAL only when the switch was used without a fix`() {
        // Review finding 3: location denied and the switch never touched is
        // UNKNOWN, so a later time edit can give the drive night credit.
        assertEquals(NightSource.SUN, timedDriveNightSource(hasFix = true, manualSwitchUsed = true))
        assertEquals(NightSource.MANUAL, timedDriveNightSource(hasFix = false, manualSwitchUsed = true))
        assertEquals(NightSource.UNKNOWN, timedDriveNightSource(hasFix = false, manualSwitchUsed = false))
    }

    @Test
    fun `manual switch minutes never exceed the total`() {
        // Review finding 6.
        val start = Instant.parse("2025-11-02T07:50:00Z")
        val end = Instant.parse("2025-11-02T08:10:00Z")

        val (total, night) = timedDriveMinutes(start, end, fixLocation = null, manualNightSeconds = 25 * 60)

        assertEquals(20, total)
        assertEquals(20, night)
    }
}
