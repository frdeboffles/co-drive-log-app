package com.codrivelog.app.backup

import com.codrivelog.app.data.db.DatabaseSnapshot
import java.time.LocalDateTime

/**
 * Full content of a backup file: every database row plus the student profile.
 *
 * @property createdAt  Local time at which the backup was made.
 * @property appVersion `versionName` of the app that made the backup.
 * @property profile    Student name and permit number from onboarding, or
 *                      `null` when the file has none. An import then keeps
 *                      the profile stored in the app.
 * @property data       All rows of the three database tables.
 */
data class DatabaseBackup(
    val createdAt: LocalDateTime,
    val appVersion: String,
    val profile: BackupProfile?,
    val data: DatabaseSnapshot,
) {
    fun summary() = BackupSummary(
        sessions    = data.sessions.size,
        supervisors = data.supervisors.size,
        routePoints = data.routePoints.size,
        profile     = profile,
    )
}

/** Student profile stored in DataStore during onboarding. */
data class BackupProfile(
    val studentName: String,
    val permitNumber: String,
) {
    val isBlank: Boolean
        get() = studentName.isBlank() && permitNumber.isBlank()
}

/**
 * Row counts and profile of a backup or of the data in the app.
 *
 * [hasData] is the one rule that decides whether an import first saves a
 * safety backup. The import dialog and [BackupManager.restore] both use it.
 */
data class BackupSummary(
    val sessions: Int,
    val supervisors: Int,
    val routePoints: Int,
    val profile: BackupProfile?,
) {
    /** `true` when there is at least one row or a non-blank profile. */
    val hasData: Boolean
        get() = sessions > 0 || supervisors > 0 || routePoints > 0 ||
            (profile != null && !profile.isBlank)
}

/** Reasons a backup export or import can fail, mapped to messages by the UI. */
enum class BackupError {
    /** The file could not be opened or read. */
    READ_FAILED,

    /** The file is larger than any backup this app can make. */
    TOO_LARGE,

    /** The file is not a CO Drive Log backup, or is not valid JSON. */
    NOT_A_BACKUP,

    /** The file was made by a newer app version with a newer format. */
    NEWER_FORMAT,

    /** The file is a backup, but its data is inconsistent or malformed. */
    INVALID_DATA,

    /** The backup file could not be written to Downloads. */
    WRITE_FAILED,

    /** The automatic backup of the current data failed, so the import was not run. */
    SAFETY_BACKUP_FAILED,

    /** A drive is being recorded, so the data cannot be replaced now. */
    DRIVE_IN_PROGRESS,

    /** Any other failure, such as a database or storage error. */
    UNEXPECTED,
}

/** Thrown by backup operations; [error] says what went wrong. */
class BackupException(
    val error: BackupError,
    message: String = error.name,
    cause: Throwable? = null,
) : Exception(message, cause)
