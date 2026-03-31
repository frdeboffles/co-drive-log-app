package com.codrivelog.app.export

import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.format.DateTimeFormatter

data class GeoJsonFeature(
    val session: DriveSession,
    val routePoints: List<DriveRoutePoint>,
)

object GeoJsonExporter {
    private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun write(features: List<GeoJsonFeature>, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        val exportable = features.filter { it.routePoints.isNotEmpty() }

        writer.write("{\"type\":\"FeatureCollection\",\"features\":[")
        exportable.forEachIndexed { index, feature ->
            if (index > 0) writer.write(",")
            writer.write(featureToJson(feature))
        }
        writer.write("]}")
        writer.flush()
    }

    private fun featureToJson(feature: GeoJsonFeature): String {
        val session = feature.session
        val points = feature.routePoints.sortedBy { it.timestamp }

        val geometryJson = if (points.size == 1) {
            val point = points.first()
            "{\"type\":\"Point\",\"coordinates\":[${point.longitude},${point.latitude}]}"
        } else {
            val coordinates = points.joinToString(",") { point ->
                "[${point.longitude},${point.latitude}]"
            }
            "{\"type\":\"LineString\",\"coordinates\":[${coordinates}]}"
        }

        val commentsJson = session.comments?.let { "\"${escapeJson(it)}\"" } ?: "null"

        return buildString {
            append("{\"type\":\"Feature\",")
            append("\"geometry\":")
            append(geometryJson)
            append(",\"properties\":{")
            append("\"sessionId\":${session.id},")
            append("\"date\":\"${session.date.format(DATE_FORMATTER)}\",")
            append("\"startTime\":\"${session.startTime.format(DATE_TIME_FORMATTER)}\",")
            append("\"endTime\":\"${session.endTime.format(DATE_TIME_FORMATTER)}\",")
            append("\"totalMinutes\":${session.totalMinutes},")
            append("\"nightMinutes\":${session.nightMinutes},")
            append("\"supervisorName\":\"${escapeJson(session.supervisorName)}\",")
            append("\"supervisorInitials\":\"${escapeJson(session.supervisorInitials)}\",")
            append("\"comments\":${commentsJson},")
            append("\"isManualEntry\":${session.isManualEntry},")
            append("\"pointCount\":${points.size}")
            append("}}")
        }
    }

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
