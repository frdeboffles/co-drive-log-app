package com.codrivelog.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DriveTimerServiceConfigTest {

    @Test
    fun `location poll interval is twenty seconds`() {
        assertEquals(20_000L, DriveTimerService.LOCATION_POLL_INTERVAL_MS)
    }
}
