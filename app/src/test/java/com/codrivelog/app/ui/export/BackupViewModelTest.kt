package com.codrivelog.app.ui.export

import android.net.Uri
import android.util.Log
import com.codrivelog.app.backup.BackupError
import com.codrivelog.app.backup.BackupException
import com.codrivelog.app.backup.BackupManager
import com.codrivelog.app.backup.BackupProfile
import com.codrivelog.app.backup.BackupSummary
import com.codrivelog.app.backup.DatabaseBackup
import com.codrivelog.app.backup.RestoreResult
import com.codrivelog.app.data.db.DatabaseSnapshot
import com.codrivelog.app.data.model.Supervisor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val manager = mockk<BackupManager>()
    private val uri = mockk<Uri>()
    private lateinit var viewModel: BackupViewModel

    private val current = backup(supervisors = 1, studentName = "Current")
    private val incoming = backup(supervisors = 3, studentName = "Incoming")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BackupViewModel(manager)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `export shows the saved file name`() {
        coEvery { manager.export() } returns "CoDriveLog_backup_x.json"

        viewModel.exportBackup()

        assertEquals(BackupUiState.ExportDone("CoDriveLog_backup_x.json"), viewModel.state.value)
    }

    @Test
    fun `picking a file asks for confirmation with both counts and changes nothing`() {
        coEvery { manager.read(uri) } returns incoming
        coEvery { manager.currentSummary() } returns current.summary()

        viewModel.onFilePicked(uri)

        assertEquals(
            BackupUiState.ConfirmImport(
                current            = BackupSummary(0, 1, 0, BackupProfile("Current", "")),
                incoming           = BackupSummary(0, 3, 0, BackupProfile("Incoming", "")),
                incomingCreatedAt  = incoming.createdAt,
                incomingAppVersion = "1.0.5",
            ),
            viewModel.state.value,
        )
        coVerify(exactly = 0) { manager.restore(any()) }
    }

    @Test
    fun `confirming restores the picked backup`() {
        coEvery { manager.read(uri) } returns incoming
        coEvery { manager.currentSummary() } returns current.summary()
        coEvery { manager.restore(incoming) } returns RestoreResult("before.json")

        viewModel.onFilePicked(uri)
        viewModel.confirmImport()

        assertEquals(BackupUiState.ImportDone("before.json"), viewModel.state.value)
    }

    @Test
    fun `cancelling drops the pending import`() {
        coEvery { manager.read(uri) } returns incoming
        coEvery { manager.currentSummary() } returns current.summary()

        viewModel.onFilePicked(uri)
        viewModel.dismiss()
        viewModel.confirmImport()

        assertEquals(BackupUiState.Idle, viewModel.state.value)
        coVerify(exactly = 0) { manager.restore(any()) }
    }

    @Test
    fun `an invalid file shows the error`() {
        coEvery { manager.read(uri) } throws BackupException(BackupError.NOT_A_BACKUP)

        viewModel.onFilePicked(uri)

        assertEquals(BackupUiState.Failed(BackupError.NOT_A_BACKUP), viewModel.state.value)
    }

    @Test
    fun `an unexpected error shows the error dialog instead of crashing`() {
        coEvery { manager.read(uri) } returns incoming
        coEvery { manager.currentSummary() } throws IllegalStateException("database is locked")
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        viewModel.onFilePicked(uri)

        assertEquals(BackupUiState.Failed(BackupError.UNEXPECTED), viewModel.state.value)
    }

    @Test
    fun `a cancelled file picker does nothing`() {
        viewModel.onFilePicked(null)

        assertEquals(BackupUiState.Idle, viewModel.state.value)
    }

    private fun backup(supervisors: Int, studentName: String) = DatabaseBackup(
        createdAt  = LocalDateTime.of(2026, 10, 1, 8, 0),
        appVersion = "1.0.5",
        profile    = BackupProfile(studentName, ""),
        data       = DatabaseSnapshot(
            supervisors = (1..supervisors).map { Supervisor(it.toLong(), "S$it", "S") },
            sessions    = emptyList(),
            routePoints = emptyList(),
        ),
    )
}
