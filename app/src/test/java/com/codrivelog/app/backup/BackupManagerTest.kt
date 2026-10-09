package com.codrivelog.app.backup

import android.net.Uri
import com.codrivelog.app.data.db.DatabaseSnapshot
import com.codrivelog.app.data.fake.FakeBackupDao
import com.codrivelog.app.data.model.DriveRoutePoint
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.onboarding.OnboardingRepository
import com.codrivelog.app.service.DriveTimerRepository
import com.codrivelog.app.service.TimerState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BackupManagerTest {

    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 10, 9, 15, 30, 12).toInstant(ZoneOffset.UTC),
        ZoneOffset.UTC,
    )
    private val safetyName = "CoDriveLog_backup_before_import_20261009_153012.json"

    private lateinit var dao: FakeBackupDao
    private lateinit var onboarding: OnboardingRepository
    private lateinit var timer: DriveTimerRepository
    private lateinit var files: FakeFileStore
    private lateinit var manager: BackupManager

    @BeforeEach
    fun setUp() {
        dao = FakeBackupDao()
        onboarding = mockk(relaxed = true)
        setStoredProfile("", "")
        timer = DriveTimerRepository()
        files = FakeFileStore()
        manager = BackupManager(dao, onboarding, timer, files, clock, UnconfinedTestDispatcher())
    }

    // ---- Export ----

    @Test
    fun `export writes a readable backup with a timestamped name`() = runTest {
        setStoredProfile("Current Student", "CUR-1")
        dao.sessions = listOf(session(1))

        val fileName = manager.export()

        assertEquals("CoDriveLog_backup_20261009_153012.json", fileName)
        val saved = files.read(fileName)
        assertEquals(listOf(session(1)), saved.data.sessions)
        assertEquals(BackupProfile("Current Student", "CUR-1"), saved.profile)
    }

    @Test
    fun `export reports the name MediaStore stored`() = runTest {
        files.renameTo = { name -> name.replace(".json", " (1).json") }

        assertEquals("CoDriveLog_backup_20261009_153012 (1).json", manager.export())
    }

    @Test
    fun `export reports a write failure`() = runTest {
        files.failWrites = true

        val e = assertThrows<BackupException> { manager.export() }

        assertEquals(BackupError.WRITE_FAILED, e.error)
    }

    // ---- Summary ----

    @Test
    fun `current summary counts rows and reads the profile`() = runTest {
        setStoredProfile("Current Student", "CUR-1")
        dao.supervisors = listOf(Supervisor(1, "S", "S"))
        dao.sessions = listOf(session(1), session(2))

        assertEquals(
            BackupSummary(2, 1, 0, BackupProfile("Current Student", "CUR-1")),
            manager.currentSummary(),
        )
    }

    // ---- Restore ----

    @Test
    fun `restore saves a safety backup of the current data before replacing it`() = runTest {
        setStoredProfile("Current Student", "CUR-1")
        dao.supervisors = listOf(Supervisor(1, "Old Sup", "OS"))
        dao.sessions = listOf(session(1))

        val result = manager.restore(backupOf(sessions = listOf(session(5), session(6))))

        assertEquals(safetyName, result.safetyBackupFileName)
        val safety = files.read(safetyName)
        assertEquals(listOf(session(1)), safety.data.sessions)
        assertEquals(listOf(Supervisor(1, "Old Sup", "OS")), safety.data.supervisors)
        assertEquals(BackupProfile("Current Student", "CUR-1"), safety.profile)
        assertEquals(listOf(session(5), session(6)), dao.sessions)
        assertEquals(emptyList<Supervisor>(), dao.supervisors)
    }

    @Test
    fun `restore reports the safety backup name MediaStore stored`() = runTest {
        dao.sessions = listOf(session(1))
        files.renameTo = { name -> name.replace(".json", " (1).json") }

        val result = manager.restore(backupOf(sessions = listOf(session(5))))

        assertEquals(safetyName.replace(".json", " (1).json"), result.safetyBackupFileName)
    }

    @Test
    fun `restore backs up a stored profile even when the database is empty`() = runTest {
        setStoredProfile("Current Student", "CUR-1")

        val result = manager.restore(backupOf(sessions = listOf(session(5))))

        assertEquals(safetyName, result.safetyBackupFileName)
        assertEquals(BackupProfile("Current Student", "CUR-1"), files.read(safetyName).profile)
    }

    @Test
    fun `restore makes no safety backup when the app has no data at all`() = runTest {
        val result = manager.restore(backupOf(sessions = listOf(session(5))))

        assertNull(result.safetyBackupFileName)
        assertEquals(emptyMap<String, ByteArray>(), files.saved)
        assertEquals(listOf(session(5)), dao.sessions)
    }

    @Test
    fun `restore replaces the student profile`() = runTest {
        manager.restore(backupOf(profile = BackupProfile("New Student", "NEW-9")))

        coVerify { onboarding.updateStudentProfile("New Student", "NEW-9") }
    }

    @Test
    fun `restore keeps the stored profile when the file has none`() = runTest {
        manager.restore(backupOf(sessions = listOf(session(5)), profile = null))

        assertEquals(listOf(session(5)), dao.sessions)
        coVerify(exactly = 0) { onboarding.updateStudentProfile(any(), any()) }
    }

    @Test
    fun `restore stops and changes nothing when the safety backup fails`() = runTest {
        dao.sessions = listOf(session(1))
        files.failWrites = true

        val e = assertThrows<BackupException> { manager.restore(backupOf(sessions = listOf(session(5)))) }

        assertEquals(BackupError.SAFETY_BACKUP_FAILED, e.error)
        assertEquals(0, dao.replaceCount)
        assertEquals(listOf(session(1)), dao.sessions)
        coVerify(exactly = 0) { onboarding.updateStudentProfile(any(), any()) }
    }

    @Test
    fun `restore is refused while a drive is in progress`() = runTest {
        dao.sessions = listOf(session(1))
        timer.update(TimerState.Saving)

        val e = assertThrows<BackupException> { manager.restore(backupOf(sessions = listOf(session(5)))) }

        assertEquals(BackupError.DRIVE_IN_PROGRESS, e.error)
        assertEquals(0, dao.replaceCount)
        assertEquals(emptyMap<String, ByteArray>(), files.saved)
    }

    @Test
    fun `the drive check runs inside the transaction`() = runTest {
        // A drive stops after restore() starts but before the transaction:
        // the check must see it, not an earlier Idle state.
        dao.onTransactionStart = { timer.update(TimerState.Saving) }

        val e = assertThrows<BackupException> { manager.restore(backupOf(sessions = listOf(session(5)))) }

        assertEquals(BackupError.DRIVE_IN_PROGRESS, e.error)
        assertEquals(0, dao.replaceCount)
    }

    @Test
    fun `the safety backup holds rows saved just before the transaction`() = runTest {
        dao.sessions = listOf(session(1))
        dao.onTransactionStart = { dao.sessions = dao.sessions + session(2) }

        manager.restore(backupOf(sessions = listOf(session(5))))

        assertEquals(listOf(session(1), session(2)), files.read(safetyName).data.sessions)
    }

    @Test
    fun `restore completes even when the caller is cancelled`() = runTest {
        val inTransaction = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        dao.onTransactionStart = {
            inTransaction.complete(Unit)
            release.await()
        }

        val job = async { manager.restore(backupOf(sessions = listOf(session(5)), profile = BackupProfile("New", "N-1"))) }
        inTransaction.await()
        job.cancel()
        release.complete(Unit)
        yield()

        assertEquals(listOf(session(5)), dao.sessions)
        coVerify { onboarding.updateStudentProfile("New", "N-1") }
    }

    // ---- Read ----

    @Test
    fun `read parses and validates the picked file`() = runTest {
        val uri = mockk<Uri>()
        files.inputs[uri] = bytesOf(backupOf(sessions = listOf(session(3))))

        assertEquals(listOf(session(3)), manager.read(uri).data.sessions)
    }

    @Test
    fun `read reports a file that cannot be opened`() = runTest {
        val e = assertThrows<BackupException> { manager.read(mockk()) }

        assertEquals(BackupError.READ_FAILED, e.error)
    }

    @Test
    fun `read reports a file that is not a backup`() = runTest {
        val uri = mockk<Uri>()
        files.inputs[uri] = "name,date\n".toByteArray()

        val e = assertThrows<BackupException> { manager.read(uri) }

        assertEquals(BackupError.NOT_A_BACKUP, e.error)
    }

    // ---- Helpers ----

    private fun setStoredProfile(name: String, permit: String) {
        every { onboarding.studentName } returns flowOf(name)
        every { onboarding.permitNumber } returns flowOf(permit)
    }

    private class FakeFileStore : BackupFileStore {
        val saved = mutableMapOf<String, ByteArray>()
        val inputs = mutableMapOf<Uri, ByteArray>()
        var failWrites = false
        var renameTo: (String) -> String = { it }

        override fun saveToDownloads(fileName: String, write: (OutputStream) -> Unit): String? {
            if (failWrites) return null
            val out = ByteArrayOutputStream()
            write(out)
            val storedName = renameTo(fileName)
            saved[storedName] = out.toByteArray()
            return storedName
        }

        override fun openInput(uri: Uri): InputStream? = inputs[uri]?.let(::ByteArrayInputStream)

        fun read(name: String): DatabaseBackup =
            BackupJson.read { ByteArrayInputStream(saved.getValue(name)) }
    }

    private fun bytesOf(backup: DatabaseBackup): ByteArray =
        ByteArrayOutputStream().also { BackupJson.write(backup, it) }.toByteArray()

    private fun backupOf(
        sessions: List<DriveSession> = emptyList(),
        profile: BackupProfile? = BackupProfile("File Student", "FILE-1"),
    ) = DatabaseBackup(
        createdAt  = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        appVersion = "1.1.0",
        profile    = profile,
        data       = DatabaseSnapshot(emptyList(), sessions, emptyList<DriveRoutePoint>()),
    )

    private fun session(id: Long) = DriveSession(
        id                 = id,
        date               = LocalDate.of(2025, 6, 10),
        startTime          = LocalDateTime.of(2025, 6, 10, 9, 0),
        endTime            = LocalDateTime.of(2025, 6, 10, 10, 0),
        totalMinutes       = 60,
        nightMinutes       = 0,
        supervisorName     = "Jane Doe",
        supervisorInitials = "JD",
    )
}
