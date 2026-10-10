package com.codrivelog.app.util

import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class NightRuleRecalculationTest {

    private val denver = ZoneId.of("America/Denver")

    @Test
    fun `recalculateSessions counts recalculated and kept drives`() {
        val bufferedManualEntry = session(id = 1, nightMinutes = 21, isManualEntry = true)
        val manualSwitchDrive = session(id = 2, nightMinutes = 45, isManualEntry = false)

        val result = NightRuleRecalculation.recalculateSessions(
            sessions = listOf(bufferedManualEntry, manualSwitchDrive),
            routes   = emptyMap(),
            zones    = listOf(denver),
        )

        assertEquals(NightRuleRecalculation.Summary(recalculated = 1, kept = 1), result.summary)
        assertEquals(listOf(2L), result.keptIds)
        assertEquals(listOf(60, 45), result.sessions.map { it.nightMinutes })
        assertEquals(listOf(NightSource.SUN, NightSource.UNKNOWN), result.sessions.map { it.nightSource })
    }

    private fun session(id: Long, nightMinutes: Int, isManualEntry: Boolean) = DriveSession(
        id                 = id,
        date               = LocalDate.of(2025, 12, 21),
        startTime          = LocalDateTime.of(2025, 12, 21, 17, 0),
        endTime            = LocalDateTime.of(2025, 12, 21, 18, 0),
        totalMinutes       = 60,
        nightMinutes       = nightMinutes,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
        isManualEntry      = isManualEntry,
    )
}
