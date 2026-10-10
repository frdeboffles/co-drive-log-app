package com.codrivelog.app.backup

import com.codrivelog.app.data.db.DatabaseSnapshot
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.NightSource
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.util.NightRuleRecalculation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.intOrNull
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads and writes [DatabaseBackup] as a versioned JSON document.
 *
 * The file format is defined by private DTOs, not by the Room entities, so
 * a change to an entity does not silently change the file format. Dates use
 * the same ISO-8601 text as the database converters.
 *
 * Both directions stream, so the document text is never held in memory.
 * [read] validates the whole file before it returns, so a caller never
 * starts an import with data that cannot be inserted.
 */
@OptIn(ExperimentalSerializationApi::class)
object BackupJson {

    /** Value of the `format` field that identifies a backup file. */
    const val FORMAT_ID = "co-drive-log-backup"

    /** Version of the file format written by this app version. */
    const val FORMAT_VERSION = 1

    /**
     * Value of the `nightRule` field: night minutes count from sunset to
     * sunrise. Files without the field (app 1.1.0) used a one-hour buffer.
     * An optional field, not a new format version, so 1.1.0 can still read
     * newer files. A value this version does not know comes from a newer
     * app and is refused.
     */
    const val NIGHT_RULE_SUNSET_TO_SUNRISE = "sunset-to-sunrise"

    /**
     * Largest file [read] accepts. About 200 hours of drives with a route
     * point every 10 seconds fit in well under a third of this.
     */
    const val MAX_FILE_BYTES = 32L * 1024 * 1024

    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun write(backup: DatabaseBackup, outputStream: OutputStream) {
        json.encodeToStream(BackupFileDto.serializer(), backup.toDto(), outputStream)
        outputStream.flush()
    }

    /**
     * Parses and validates a backup file.
     *
     * The file is read twice through [open]: first only the header, so a
     * file from a newer format is refused before its body is parsed with
     * this version's rules; then the whole document.
     *
     * @param open Opens a new stream on the file at each call.
     * @throws BackupException with [BackupError.TOO_LARGE],
     *   [BackupError.NOT_A_BACKUP], [BackupError.NEWER_FORMAT] or
     *   [BackupError.INVALID_DATA].
     */
    fun read(open: () -> InputStream): DatabaseBackup {
        val header = decode(open, HeaderDto.serializer(), BackupError.NOT_A_BACKUP)

        val format = (header.format as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != FORMAT_ID) {
            throw BackupException(BackupError.NOT_A_BACKUP, "Missing or wrong format id: $format")
        }
        val version = (header.formatVersion as? JsonPrimitive)?.intOrNull
            ?: throw BackupException(BackupError.INVALID_DATA, "Missing formatVersion")
        if (version > FORMAT_VERSION) {
            throw BackupException(BackupError.NEWER_FORMAT, "Format version $version is newer than $FORMAT_VERSION")
        }
        if (version < 1) {
            throw BackupException(BackupError.INVALID_DATA, "Invalid format version $version")
        }

        val dto = decode(open, BackupFileDto.serializer(), BackupError.INVALID_DATA)
        when (dto.nightRule) {
            null, NIGHT_RULE_SUNSET_TO_SUNRISE -> Unit
            "" -> throw BackupException(BackupError.INVALID_DATA, "Empty nightRule")
            else -> throw BackupException(BackupError.NEWER_FORMAT, "Unknown nightRule ${dto.nightRule}")
        }
        val backup = try {
            dto.toBackup()
        } catch (e: DateTimeParseException) {
            throw BackupException(BackupError.INVALID_DATA, "Invalid date: ${e.parsedString}", e)
        }
        validate(backup.data)
        return backup
    }

    private fun <T> decode(
        open: () -> InputStream,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        malformedError: BackupError,
    ): T = try {
        SizeLimitedInputStream(open(), MAX_FILE_BYTES).use { stream ->
            json.decodeFromStream(deserializer, stream)
        }
    } catch (e: FileTooLargeException) {
        throw BackupException(BackupError.TOO_LARGE, "File is larger than $MAX_FILE_BYTES bytes", e)
    } catch (e: SerializationException) {
        throw BackupException(malformedError, e.message ?: "Malformed backup", e)
    } catch (e: IllegalArgumentException) {
        throw BackupException(malformedError, e.message ?: "Malformed backup", e)
    }

    /**
     * Checks that the rows can be inserted with their ids preserved, and
     * rejects values the app can never produce.
     *
     * It does not reject an end time before the start time, or night
     * minutes above the total: a timed drive across the daylight-saving
     * fall-back hour produces both, and a backup of real app data must
     * always import.
     */
    private fun validate(data: DatabaseSnapshot) {
        fun invalid(message: String): Nothing = throw BackupException(BackupError.INVALID_DATA, message)

        fun checkIds(table: String, ids: List<Long>) {
            if (ids.any { it <= 0 }) invalid("$table has an id <= 0")
            if (ids.toSet().size != ids.size) invalid("$table has duplicate ids")
        }
        checkIds("supervisors", data.supervisors.map { it.id })
        checkIds("sessions", data.sessions.map { it.id })
        checkIds("routePoints", data.routePoints.map { it.id })

        data.sessions.forEach { session ->
            if (session.totalMinutes < 0 || session.nightMinutes < 0) {
                invalid("Session ${session.id} has negative minutes")
            }
            // A bad location would count every minute as night at the next
            // edit; half a location would silently fall back to Denver.
            val lat = session.nightLatitude
            val lng = session.nightLongitude
            if ((lat == null) != (lng == null)) invalid("Session ${session.id} has half a night location")
            if (lat != null && lng != null && (lat !in -90.0..90.0 || lng !in -180.0..180.0)) {
                invalid("Session ${session.id} has a night location out of range")
            }
            if (session.nightSource == NightSource.SUN && lat == null) {
                invalid("Session ${session.id} is a sun calculation without a location")
            }
        }

        val sessionIds = data.sessions.mapTo(HashSet()) { it.id }
        data.routePoints.forEach { point ->
            if (point.sessionId !in sessionIds) {
                invalid("Route point ${point.id} refers to missing session ${point.sessionId}")
            }
            if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) {
                invalid("Route point ${point.id} has coordinates out of range")
            }
            if (point.accuracyMeters.isNaN() || point.accuracyMeters < 0f) {
                invalid("Route point ${point.id} has an invalid accuracy")
            }
        }
    }

    // ---- Mapping ----

    private fun DatabaseBackup.toDto() = BackupFileDto(
        format        = FORMAT_ID,
        formatVersion = FORMAT_VERSION,
        appVersion    = appVersion,
        createdAt     = createdAt.format(DATE_TIME),
        // Old-rule data (the backup taken before the 1.2.0 migration) is
        // written like a 1.1.0 file, so an import reclassifies it.
        nightRule     = if (legacyNightRule) null else NIGHT_RULE_SUNSET_TO_SUNRISE,
        profile       = profile?.let { ProfileDto(it.studentName, it.permitNumber) },
        supervisors   = data.supervisors.map { SupervisorDto(it.id, it.name, it.initials) },
        sessions      = data.sessions.map {
            SessionDto(
                id                 = it.id,
                date               = it.date.format(DATE),
                startTime          = it.startTime.format(DATE_TIME),
                endTime            = it.endTime.format(DATE_TIME),
                totalMinutes       = it.totalMinutes,
                nightMinutes       = it.nightMinutes,
                supervisorName     = it.supervisorName,
                supervisorInitials = it.supervisorInitials,
                comments           = it.comments,
                isManualEntry      = it.isManualEntry,
                nightSource        = it.nightSource.name,
                nightLatitude      = it.nightLatitude,
                nightLongitude     = it.nightLongitude,
                timeZone           = it.timeZone,
            )
        },
        routePoints   = data.routePoints.map {
            RoutePointDto(
                id             = it.id,
                sessionId      = it.sessionId,
                timestamp      = it.timestamp.format(DATE_TIME),
                latitude       = it.latitude,
                longitude      = it.longitude,
                accuracyMeters = it.accuracyMeters,
            )
        },
    )

    private fun BackupFileDto.toBackup() = DatabaseBackup(
        createdAt  = LocalDateTime.parse(createdAt, DATE_TIME),
        appVersion = appVersion,
        profile    = profile?.let { BackupProfile(it.studentName, it.permitNumber) },
        legacyNightRule = nightRule == null,
        data       = DatabaseSnapshot(
            supervisors = supervisors.map { Supervisor(it.id, it.name, it.initials) },
            sessions    = sessions.map {
                DriveSession(
                    id                 = it.id,
                    date               = LocalDate.parse(it.date, DATE),
                    startTime          = LocalDateTime.parse(it.startTime, DATE_TIME),
                    endTime            = LocalDateTime.parse(it.endTime, DATE_TIME),
                    totalMinutes       = it.totalMinutes,
                    nightMinutes       = it.nightMinutes,
                    supervisorName     = it.supervisorName,
                    supervisorInitials = it.supervisorInitials,
                    comments           = it.comments,
                    isManualEntry      = it.isManualEntry,
                    nightSource        = it.nightSource?.let(::parseNightSource) ?: NightSource.UNKNOWN,
                    nightLatitude      = it.nightLatitude,
                    nightLongitude     = it.nightLongitude,
                    // A zone this phone's time-zone data does not know is
                    // dropped, not refused: real app data must always import,
                    // and an edit then uses Colorado (NightRuleRecalculation.zoneOf).
                    timeZone           = NightRuleRecalculation.parseZone(it.timeZone)?.id,
                )
            },
            routePoints = routePoints.map {
                DriveRoutePoint(
                    id             = it.id,
                    sessionId      = it.sessionId,
                    timestamp      = LocalDateTime.parse(it.timestamp, DATE_TIME),
                    latitude       = it.latitude,
                    longitude      = it.longitude,
                    accuracyMeters = it.accuracyMeters,
                )
            },
        ),
    )
}

/** Thrown by [SizeLimitedInputStream]; an [IOException] so stream readers pass it through. */
private class FileTooLargeException : IOException("File too large")

/** Fails with [FileTooLargeException] once more than [maxBytes] bytes have been read. */
private class SizeLimitedInputStream(
    input: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(input) {

    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        add(skipped)
        return skipped
    }

    private fun add(n: Long) {
        count += n
        if (count > maxBytes) throw FileTooLargeException()
    }
}

private fun parseNightSource(value: String): NightSource =
    NightSource.entries.firstOrNull { it.name == value }
        ?: throw if (value.isBlank()) {
            BackupException(BackupError.INVALID_DATA, "Empty nightSource")
        } else {
            // Like an unknown nightRule: a value this version does not know
            // comes from a newer app.
            BackupException(BackupError.NEWER_FORMAT, "Unknown nightSource $value")
        }

// ---- File format, version 1 ----

/** First pass: only the fields that decide whether the file can be read at all. */
@Serializable
private data class HeaderDto(
    val format: JsonElement? = null,
    val formatVersion: JsonElement? = null,
)

@Serializable
private data class BackupFileDto(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: String,
    val nightRule: String? = null,
    val profile: ProfileDto? = null,
    val supervisors: List<SupervisorDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val routePoints: List<RoutePointDto> = emptyList(),
)

@Serializable
private data class ProfileDto(
    val studentName: String,
    val permitNumber: String,
)

@Serializable
private data class SupervisorDto(
    val id: Long,
    val name: String,
    val initials: String,
)

@Serializable
private data class SessionDto(
    val id: Long,
    val date: String,
    val startTime: String,
    val endTime: String,
    val totalMinutes: Int,
    val nightMinutes: Int,
    val supervisorName: String,
    val supervisorInitials: String,
    val comments: String? = null,
    val isManualEntry: Boolean = false,
    // Since nightRule "sunset-to-sunrise" (app 1.2.0).
    val nightSource: String? = null,
    val nightLatitude: Double? = null,
    val nightLongitude: Double? = null,
    val timeZone: String? = null,
)

@Serializable
private data class RoutePointDto(
    val id: Long,
    val sessionId: Long,
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)
