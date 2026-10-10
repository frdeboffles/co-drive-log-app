package com.codrivelog.app.ui.export

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codrivelog.app.backup.BackupError
import com.codrivelog.app.backup.BackupException
import com.codrivelog.app.backup.BackupManager
import com.codrivelog.app.backup.BackupSummary
import com.codrivelog.app.backup.DatabaseBackup
import com.codrivelog.app.util.NightRuleRecalculation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Drives the full backup export and import on the Export screen.
 *
 * Import flow: [onFilePicked] reads and validates the file, then
 * [BackupUiState.ConfirmImport] shows what will be replaced. Only
 * [confirmImport] changes data.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private companion object {
        const val TAG = "BackupViewModel"
    }

    /** Backup read from the picked file, waiting for the user to confirm. */
    private var pendingBackup: DatabaseBackup? = null

    fun exportBackup() {
        launchOperation {
            BackupUiState.ExportDone(fileName = backupManager.export())
        }
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        launchOperation {
            val incoming = backupManager.read(uri)
            val current = backupManager.currentSummary()
            pendingBackup = incoming
            BackupUiState.ConfirmImport(
                current  = current,
                incoming = incoming.summary(),
                incomingCreatedAt  = incoming.createdAt,
                incomingAppVersion = incoming.appVersion,
                nightRecalculation = incoming.nightRecalculation,
            )
        }
    }

    fun confirmImport() {
        val backup = pendingBackup ?: return
        pendingBackup = null
        launchOperation {
            val result = backupManager.restore(backup)
            BackupUiState.ImportDone(safetyBackupFileName = result.safetyBackupFileName)
        }
    }

    /** Closes any dialog and drops a pending import. */
    fun dismiss() {
        pendingBackup = null
        _state.value = BackupUiState.Idle
    }

    private fun launchOperation(block: suspend () -> BackupUiState) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                pendingBackup = null
                BackupUiState.Failed(e.error)
            } catch (e: Exception) {
                // Database, DataStore or storage errors must show the error
                // dialog, not crash the app.
                Log.e(TAG, "Backup operation failed", e)
                pendingBackup = null
                BackupUiState.Failed(BackupError.UNEXPECTED)
            }
        }
    }
}

sealed interface BackupUiState {
    data object Idle : BackupUiState

    data object Working : BackupUiState

    data class ConfirmImport(
        val current: BackupSummary,
        val incoming: BackupSummary,
        val incomingCreatedAt: LocalDateTime,
        val incomingAppVersion: String,
        val nightRecalculation: NightRuleRecalculation.Summary? = null,
    ) : BackupUiState

    data class ExportDone(val fileName: String) : BackupUiState

    data class ImportDone(val safetyBackupFileName: String?) : BackupUiState

    data class Failed(val error: BackupError) : BackupUiState
}
