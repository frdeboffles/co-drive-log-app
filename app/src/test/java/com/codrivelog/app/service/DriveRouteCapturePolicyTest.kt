package com.codrivelog.app.service

import com.codrivelog.app.location.LatLng
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DriveRouteCapturePolicyTest {

    private val now = LocalDateTime.of(2026, 3, 30, 12, 0)

    @Test
    fun `force capture always records`() {
        val shouldRecord = shouldRecordRoutePoint(
            recentAccepted = null,
            fix = LatLng(39.7392, -104.9903, accuracyMeters = 500f),
            now = now,
            force = true,
            strictAccuracyMeters = 80f,
            fallbackAccuracyMeters = 120f,
            fallbackStaleMinutes = 3,
            dedupeDistanceMeters = 25.0,
            dedupeMinIntervalMinutes = 2,
        )

        assertTrue(shouldRecord)
    }

    @Test
    fun `strict accuracy point is accepted`() {
        val shouldRecord = shouldRecordRoutePoint(
            recentAccepted = null,
            fix = LatLng(39.7392, -104.9903, accuracyMeters = 50f),
            now = now,
            force = false,
            strictAccuracyMeters = 80f,
            fallbackAccuracyMeters = 120f,
            fallbackStaleMinutes = 3,
            dedupeDistanceMeters = 25.0,
            dedupeMinIntervalMinutes = 2,
        )

        assertTrue(shouldRecord)
    }

    @Test
    fun `fallback accuracy is rejected when last point is recent`() {
        val recent = DriveRoutePointDraft(
            timestamp = now.minusMinutes(1),
            latitude = 39.7392,
            longitude = -104.9903,
            accuracyMeters = 10f,
        )
        val shouldRecord = shouldRecordRoutePoint(
            recentAccepted = recent,
            fix = LatLng(39.7394, -104.9901, accuracyMeters = 100f),
            now = now,
            force = false,
            strictAccuracyMeters = 80f,
            fallbackAccuracyMeters = 120f,
            fallbackStaleMinutes = 3,
            dedupeDistanceMeters = 25.0,
            dedupeMinIntervalMinutes = 2,
        )

        assertFalse(shouldRecord)
    }

    @Test
    fun `fallback accuracy is accepted when last point is stale`() {
        val recent = DriveRoutePointDraft(
            timestamp = now.minusMinutes(4),
            latitude = 39.7392,
            longitude = -104.9903,
            accuracyMeters = 10f,
        )
        val shouldRecord = shouldRecordRoutePoint(
            recentAccepted = recent,
            fix = LatLng(39.7394, -104.9901, accuracyMeters = 100f),
            now = now,
            force = false,
            strictAccuracyMeters = 80f,
            fallbackAccuracyMeters = 120f,
            fallbackStaleMinutes = 3,
            dedupeDistanceMeters = 25.0,
            dedupeMinIntervalMinutes = 2,
        )

        assertTrue(shouldRecord)
    }

    @Test
    fun `point is deduped when too close and too soon`() {
        val recent = DriveRoutePointDraft(
            timestamp = now.minusMinutes(1),
            latitude = 39.7392,
            longitude = -104.9903,
            accuracyMeters = 10f,
        )
        val shouldRecord = shouldRecordRoutePoint(
            recentAccepted = recent,
            fix = LatLng(39.73921, -104.99031, accuracyMeters = 20f),
            now = now,
            force = false,
            strictAccuracyMeters = 80f,
            fallbackAccuracyMeters = 120f,
            fallbackStaleMinutes = 3,
            dedupeDistanceMeters = 25.0,
            dedupeMinIntervalMinutes = 2,
        )

        assertFalse(shouldRecord)
    }
}
