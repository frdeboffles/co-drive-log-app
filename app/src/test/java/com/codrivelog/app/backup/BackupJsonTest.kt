package com.codrivelog.app.backup

import com.codrivelog.app.data.db.DatabaseSnapshot
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime

class BackupJsonTest {

    @Test
    fun `round trip keeps every field, id and the profile`() {
        val original = sampleBackup()

        val restored = readText(write(original))

        assertEquals(original, restored)
    }

    @Test
    fun `round trip keeps null comments and special characters`() {
        val original = sampleBackup().let { backup ->
            backup.copy(
                profile = BackupProfile("Zoë \"Z\" O'Neil", "P-123\n456"),
                data = backup.data.copy(
                    sessions = listOf(session(id = 1, comments = null), session(id = 2, comments = "Snow, ice\t\"I-70\"")),
                    routePoints = emptyList(),
                ),
            )
        }

        assertEquals(original, readText(write(original)))
    }

    @Test
    fun `written file carries the format id and version`() {
        val json = write(sampleBackup())

        assertTrue(json.contains("\"format\": \"${BackupJson.FORMAT_ID}\""))
        assertTrue(json.contains("\"formatVersion\": ${BackupJson.FORMAT_VERSION}"))
    }

    @Test
    fun `empty backup round trips`() {
        val empty = sampleBackup().copy(data = DatabaseSnapshot(emptyList(), emptyList(), emptyList()))

        val restored = readText(write(empty))

        assertEquals(empty, restored)
        assertEquals(false, restored.summary().copy(profile = null).hasData)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = write(sampleBackup()).replaceFirst("{", "{\"futureField\": [1, 2],")

        assertEquals(sampleBackup(), readText(json))
    }

    @Test
    fun `rejects text that is not JSON`() {
        assertError(BackupError.NOT_A_BACKUP, "this is not json")
    }

    @Test
    fun `rejects JSON that is not an object`() {
        assertError(BackupError.NOT_A_BACKUP, "[1, 2, 3]")
    }

    @Test
    fun `rejects other JSON files such as GeoJSON`() {
        assertError(BackupError.NOT_A_BACKUP, """{"type":"FeatureCollection","features":[]}""")
    }

    @Test
    fun `rejects a format field that is not a string`() {
        assertError(BackupError.NOT_A_BACKUP, """{"format":{"x":1},"formatVersion":1}""")
    }

    @Test
    fun `rejects a newer format version`() {
        val json = write(sampleBackup())
            .replace("\"formatVersion\": ${BackupJson.FORMAT_VERSION}", "\"formatVersion\": ${BackupJson.FORMAT_VERSION + 1}")

        assertError(BackupError.NEWER_FORMAT, json)
    }

    @Test
    fun `rejects a missing format version`() {
        assertError(BackupError.INVALID_DATA, """{"format":"${BackupJson.FORMAT_ID}"}""")
    }

    @Test
    fun `rejects a missing required field`() {
        val json = write(sampleBackup()).replace("\"supervisorInitials\": \"JD\",", "")

        assertError(BackupError.INVALID_DATA, json)
    }

    @Test
    fun `rejects a malformed date`() {
        val json = write(sampleBackup()).replace("\"2025-06-10\"", "\"06/10/2025\"")

        assertError(BackupError.INVALID_DATA, json)
    }

    @Test
    fun `rejects duplicate session ids`() {
        val backup = sampleBackup().let { it.copy(data = it.data.copy(sessions = listOf(session(1), session(1)), routePoints = emptyList())) }

        assertError(BackupError.INVALID_DATA, write(backup))
    }

    @Test
    fun `rejects ids that are not positive`() {
        val backup = sampleBackup().let { it.copy(data = it.data.copy(supervisors = listOf(Supervisor(0, "No Id", "NI")))) }

        assertError(BackupError.INVALID_DATA, write(backup))
    }

    @Test
    fun `rejects route points that refer to a missing session`() {
        val backup = sampleBackup().let { it.copy(data = it.data.copy(routePoints = listOf(point(id = 1, sessionId = 99)))) }

        assertError(BackupError.INVALID_DATA, write(backup))
    }

    @Test
    fun `a file without a profile reads as a null profile`() {
        val json = write(sampleBackup())
            .replace(Regex("\"profile\": \\{[^}]*\\},"), "")

        assertNull(readText(json).profile)
    }

    @Test
    fun `a profile with a missing field is rejected instead of blanked`() {
        val json = write(sampleBackup()).replace("\"permitNumber\": \"CO-12345\"", "\"other\": 1")

        assertError(BackupError.INVALID_DATA, json)
    }

    @Test
    fun `rejects negative minutes`() {
        val backup = sampleBackup().let { it.copy(data = it.data.copy(sessions = listOf(session(3).copy(nightMinutes = -5)))) }

        assertError(BackupError.INVALID_DATA, write(backup))
    }

    @Test
    fun `rejects coordinates out of range`() {
        val backup = sampleBackup().let { it.copy(data = it.data.copy(routePoints = listOf(point(10, 3).copy(latitude = 91.0)))) }

        assertError(BackupError.INVALID_DATA, write(backup))
    }

    @Test
    fun `accepts the values a drive across the daylight-saving fall-back produces`() {
        // The timer stores local times: across the fall-back hour the end is
        // before the start, totalMinutes is clamped to 0, and night minutes
        // can be above it. A backup of real app data must still import.
        val dstDrive = session(3).copy(
            startTime    = LocalDateTime.of(2025, 11, 2, 1, 50),
            endTime      = LocalDateTime.of(2025, 11, 2, 1, 20),
            totalMinutes = 0,
            nightMinutes = 30,
        )
        val backup = sampleBackup().let { it.copy(data = it.data.copy(sessions = listOf(dstDrive), routePoints = emptyList())) }

        assertEquals(backup, readText(write(backup)))
    }

    @Test
    fun `rejects a file larger than the limit without reading it all`() {
        var bytesRead = 0L
        val endless = object : InputStream() {
            override fun read(): Int {
                bytesRead++
                return if (bytesRead == 1L) '{'.code else ' '.code
            }
        }

        val e = assertThrows<BackupException> { BackupJson.read { endless } }

        assertEquals(BackupError.TOO_LARGE, e.error)
        assertTrue(bytesRead <= BackupJson.MAX_FILE_BYTES + 64 * 1024)
    }

    // ---- Helpers ----

    private fun readText(json: String): DatabaseBackup =
        BackupJson.read { ByteArrayInputStream(json.toByteArray()) }

    private fun assertError(expected: BackupError, json: String) {
        val e = assertThrows<BackupException> { readText(json) }
        assertEquals(expected, e.error)
    }

    private fun write(backup: DatabaseBackup): String {
        val out = ByteArrayOutputStream()
        BackupJson.write(backup, out)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun sampleBackup() = DatabaseBackup(
        createdAt  = LocalDateTime.of(2026, 10, 9, 15, 30, 12),
        appVersion = "1.1.0",
        profile    = BackupProfile("Alex Driver", "CO-12345"),
        data       = DatabaseSnapshot(
            supervisors = listOf(Supervisor(1, "Jane Doe", "JD"), Supervisor(4, "John Roe", "JR")),
            sessions    = listOf(session(id = 3), session(id = 7, isManualEntry = true)),
            routePoints = listOf(point(id = 10, sessionId = 3), point(id = 11, sessionId = 3)),
        ),
    )

    private fun session(
        id: Long,
        comments: String? = "Highway practice",
        isManualEntry: Boolean = false,
    ) = DriveSession(
        id                 = id,
        date               = LocalDate.of(2025, 6, 10),
        startTime          = LocalDateTime.of(2025, 6, 10, 9, 0),
        endTime            = LocalDateTime.of(2025, 6, 10, 10, 15),
        totalMinutes       = 75,
        nightMinutes       = 0,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
        comments           = comments,
        isManualEntry      = isManualEntry,
    )

    private fun point(id: Long, sessionId: Long) = DriveRoutePoint(
        id             = id,
        sessionId      = sessionId,
        timestamp      = LocalDateTime.of(2025, 6, 10, 9, id.toInt() % 60),
        latitude       = 39.7392 + id / 1000.0,
        longitude      = -104.9903,
        accuracyMeters = 12.5f,
    )
}
