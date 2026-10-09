package com.codrivelog.app.backup

import android.net.Uri
import com.codrivelog.app.BuildConfig
import com.codrivelog.app.data.db.BackupDao
import com.codrivelog.app.data.db.DatabaseSnapshot
import com.codrivelog.app.onboarding.OnboardingRepository
import com.codrivelog.app.service.DriveTimerRepository
import com.codrivelog.app.service.TimerState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a successful import. [safetyBackupFileName] is `null` when the app had no data to back up. */
data class RestoreResult(val safetyBackupFileName: String?)

/**
 * Exports the full database and student profile to a JSON file, and restores
 * them from one.
 *
 * A restore replaces all data. Before it does, it saves the current data to
 * Downloads as a safety backup, unless the app has no data. If that safety
 * backup fails, the restore does not run.
 */
@Singleton
class BackupManager internal constructor(
    private val backupDao: BackupDao,
    private val onboardingRepository: OnboardingRepository,
    private val timerRepository: DriveTimerRepository,
    private val fileStore: BackupFileStore,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        backupDao: BackupDao,
        onboardingRepository: OnboardingRepository,
        timerRepository: DriveTimerRepository,
        fileStore: BackupFileStore,
    ) : this(
        backupDao,
        onboardingRepository,
        timerRepository,
        fileStore,
        Clock.systemDefaultZone(),
        Dispatchers.IO,
    )

    /** Row counts and profile of the data now in the app, without loading the rows. */
    suspend fun currentSummary(): BackupSummary = withContext(ioDispatcher) {
        BackupSummary(
            sessions    = backupDao.countSessions(),
            supervisors = backupDao.countSupervisors(),
            routePoints = backupDao.countRoutePoints(),
            profile     = currentProfile(),
        )
    }

    /**
     * Saves a full backup to Downloads.
     *
     * @return The file name in Downloads.
     * @throws BackupException with [BackupError.WRITE_FAILED].
     */
    suspend fun export(): String = withContext(ioDispatcher) {
        val backup = backupOf(backupDao.snapshot(), currentProfile())
        save(backup, "CoDriveLog_backup_${timestamp()}.json", BackupError.WRITE_FAILED)
    }

    /**
     * Reads and validates the backup file at [uri]. Changes nothing.
     *
     * @throws BackupException when the file cannot be read or is not a valid backup.
     */
    suspend fun read(uri: Uri): DatabaseBackup = withContext(ioDispatcher) {
        try {
            BackupJson.read {
                fileStore.openInput(uri) ?: throw IOException("Cannot open $uri")
            }
        } catch (e: IOException) {
            throw BackupException(BackupError.READ_FAILED, "Cannot read $uri", e)
        } catch (e: SecurityException) {
            throw BackupException(BackupError.READ_FAILED, "No access to $uri", e)
        }
    }

    /**
     * Replaces all data with [backup], and the student profile when the
     * backup has one.
     *
     * The drive check, the safety backup and the replacement run in one
     * database transaction, so a drive saved meanwhile is neither lost nor
     * missing from the safety backup. The whole restore is not cancellable:
     * leaving the screen cannot stop it between the data and the profile.
     *
     * @throws BackupException with [BackupError.DRIVE_IN_PROGRESS] or
     *   [BackupError.SAFETY_BACKUP_FAILED]. In both cases nothing changed.
     */
    suspend fun restore(backup: DatabaseBackup): RestoreResult =
        withContext(NonCancellable + ioDispatcher) {
            val currentProfile = currentProfile()
            var safetyBackupFileName: String? = null

            backupDao.replaceAll(backup.data) { current ->
                if (timerRepository.timerState.value !is TimerState.Idle) {
                    throw BackupException(BackupError.DRIVE_IN_PROGRESS)
                }
                val currentBackup = backupOf(current, currentProfile)
                if (currentBackup.summary().hasData) {
                    safetyBackupFileName = save(
                        currentBackup,
                        "CoDriveLog_backup_before_import_${timestamp()}.json",
                        BackupError.SAFETY_BACKUP_FAILED,
                    )
                }
            }

            backup.profile?.let { profile ->
                onboardingRepository.updateStudentProfile(
                    studentName  = profile.studentName,
                    permitNumber = profile.permitNumber,
                )
            }
            RestoreResult(safetyBackupFileName)
        }

    private suspend fun currentProfile() = BackupProfile(
        studentName  = onboardingRepository.studentName.first(),
        permitNumber = onboardingRepository.permitNumber.first(),
    )

    private fun backupOf(data: DatabaseSnapshot, profile: BackupProfile) = DatabaseBackup(
        createdAt  = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS),
        appVersion = BuildConfig.VERSION_NAME,
        profile    = profile,
        data       = data,
    )

    private fun save(backup: DatabaseBackup, fileName: String, error: BackupError): String =
        try {
            fileStore.saveToDownloads(fileName) { stream -> BackupJson.write(backup, stream) }
        } catch (e: Exception) {
            throw BackupException(error, "Cannot write $fileName", e)
        } ?: throw BackupException(error, "Cannot write $fileName")

    private fun timestamp(): String =
        LocalDateTime.now(clock).format(FILE_TIMESTAMP)

    private companion object {
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}
