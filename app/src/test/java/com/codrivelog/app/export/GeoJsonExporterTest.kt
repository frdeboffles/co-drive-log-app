package com.codrivelog.app.export

import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

class GeoJsonExporterTest {

    @Test
    fun `writes FeatureCollection with linestring for multi-point route`() {
        val session = session(id = 7)
        val points = listOf(
            point(sessionId = 7, time = LocalDateTime.of(2025, 6, 10, 9, 0), lat = 39.7392, lng = -104.9903),
            point(sessionId = 7, time = LocalDateTime.of(2025, 6, 10, 9, 5), lat = 39.7400, lng = -104.9810),
        )

        val json = write(
            listOf(
                GeoJsonFeature(session = session, routePoints = points),
            )
        )

        assertTrue(json.contains("\"type\":\"FeatureCollection\""))
        assertTrue(json.contains("\"type\":\"Feature\""))
        assertTrue(json.contains("\"type\":\"LineString\""))
        assertTrue(json.contains("[-104.9903,39.7392]"))
        assertTrue(json.contains("[-104.981,39.74]"))
        assertTrue(json.contains("\"sessionId\":7"))
    }

    @Test
    fun `writes point geometry for single-point route`() {
        val json = write(
            listOf(
                GeoJsonFeature(
                    session = session(id = 9),
                    routePoints = listOf(
                        point(sessionId = 9, time = LocalDateTime.of(2025, 6, 10, 9, 0), lat = 39.75, lng = -104.9),
                    ),
                ),
            )
        )

        assertTrue(json.contains("\"type\":\"Point\""))
        assertTrue(json.contains("\"coordinates\":[-104.9,39.75]"))
    }

    @Test
    fun `skips sessions without route points`() {
        val json = write(
            listOf(
                GeoJsonFeature(session = session(id = 1), routePoints = emptyList()),
                GeoJsonFeature(
                    session = session(id = 2),
                    routePoints = listOf(
                        point(sessionId = 2, time = LocalDateTime.of(2025, 6, 10, 10, 0), lat = 40.0, lng = -105.0),
                        point(sessionId = 2, time = LocalDateTime.of(2025, 6, 10, 10, 4), lat = 40.01, lng = -105.01),
                    ),
                ),
            )
        )

        assertFalse(json.contains("\"sessionId\":1"))
        assertTrue(json.contains("\"sessionId\":2"))
    }

    private fun write(features: List<GeoJsonFeature>): String {
        val out = ByteArrayOutputStream()
        GeoJsonExporter.write(features, out)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun session(id: Long): DriveSession = DriveSession(
        id = id,
        date = LocalDate.of(2025, 6, 10),
        startTime = LocalDateTime.of(2025, 6, 10, 9, 0),
        endTime = LocalDateTime.of(2025, 6, 10, 10, 0),
        totalMinutes = 60,
        nightMinutes = 0,
        supervisorName = "Jane Doe",
        supervisorInitials = "JD",
        comments = "Practice",
        isManualEntry = false,
    )

    private fun point(
        sessionId: Long,
        time: LocalDateTime,
        lat: Double,
        lng: Double,
    ): DriveRoutePoint = DriveRoutePoint(
        sessionId = sessionId,
        timestamp = time,
        latitude = lat,
        longitude = lng,
        accuracyMeters = 5f,
    )
}
