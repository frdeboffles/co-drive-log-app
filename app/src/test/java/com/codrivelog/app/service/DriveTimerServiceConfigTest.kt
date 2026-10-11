package com.codrivelog.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DriveTimerServiceConfigTest {

    @Test
    fun `location update interval is ten seconds`() {
        assertEquals(10_000L, DriveTimerService.LOCATION_UPDATE_INTERVAL_MS)
    }
}
