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
            dedupeMinDistanceMeters = 10.0,
            dedupeAccuracyFactor = 2.0,
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
            dedupeMinDistanceMeters = 10.0,
            dedupeAccuracyFactor = 2.0,
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
            dedupeMinDistanceMeters = 10.0,
            dedupeAccuracyFactor = 2.0,
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
            dedupeMinDistanceMeters = 10.0,
            dedupeAccuracyFactor = 2.0,
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
            dedupeMinDistanceMeters = 10.0,
            dedupeAccuracyFactor = 2.0,
            dedupeMinIntervalMinutes = 2,
        )

        assertFalse(shouldRecord)
    }

    // ---- Movement threshold: max(10 m, 2 x accuracy) ----

    @Test
    fun `a precise fix 15 m away is movement`() {
        // With the old fixed 25 m it would have been skipped.
        assertTrue(record(metersNorth = 15.0, accuracy = 4f))
    }

    @Test
    fun `a precise fix 8 m away is jitter`() {
        assertFalse(record(metersNorth = 8.0, accuracy = 4f))
    }

    @Test
    fun `a coarse fix needs to move further`() {
        // 15 m accuracy: threshold 30 m.
        assertFalse(record(metersNorth = 25.0, accuracy = 15f))
        assertTrue(record(metersNorth = 35.0, accuracy = 15f))
    }

    private fun record(metersNorth: Double, accuracy: Float): Boolean {
        val last = DriveRoutePointDraft(now.minusSeconds(10), 39.7392, -104.9903, 4f)
        return shouldRecordRoutePoint(
            recentAccepted = last,
            fix = LatLng(39.7392 + metersNorth / 111_195.0, -104.9903, accuracy),
            now = now,
            force = false,
            strictAccuracyMeters = 80f,
            fallbackAccuracyMeters = 120f,
            fallbackStaleMinutes = 3,
            dedupeMinDistanceMeters = 10.0,
            dedupeAccuracyFactor = 2.0,
            dedupeMinIntervalMinutes = 2,
        )
    }
}
