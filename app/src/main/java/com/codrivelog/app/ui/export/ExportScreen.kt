package com.codrivelog.app.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
import com.codrivelog.app.backup.BackupError
import com.codrivelog.app.backup.BackupSummary
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.ui.toDatePickerUtcMillis
import com.codrivelog.app.ui.toLocalDateFromDatePickerUtc
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Export screen allowing the user to generate a PDF (DR 2324) or CSV export.
 *
 * Actual file generation is delegated to the host [Activity] via callbacks
 * ([onExportPdf], [onExportCsv]) so that the screen stays testable and the
 * file-save dialog / sharing intent can be launched from the Activity context.
 *
 * @param onBack       Invoked when the user taps the back button.
 * @param onExportPdf  Invoked when the user taps "Export PDF".
 * @param onExportCsv  Invoked when the user taps "Export CSV".
 * @param onExportGeoJson Invoked when the user taps "Export GeoJSON".
 * @param viewModel    [ExportViewModel] provided by Hilt.
 * @param backupViewModel [BackupViewModel] for the full backup export and import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack:      () -> Unit = {},
    onExportPdf: (signatureName: String, signatureDate: String) -> Unit = { _, _ -> },
    onExportCsv: () -> Unit = {},
    onExportGeoJson: () -> Unit = {},
    viewModel:   ExportViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    var showPdfDialog by remember { mutableStateOf(false) }
    val pickBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> backupViewModel.onFilePicked(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_export)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        ExportContent(
            modifier      = Modifier.padding(padding),
            uiState       = uiState,
            onExportPdf   = { showPdfDialog = true },
            onExportCsv   = onExportCsv,
            onExportGeoJson = onExportGeoJson,
            backupBusy    = backupState is BackupUiState.Working,
            onExportBackup = backupViewModel::exportBackup,
            onImportBackup = { pickBackupFile.launch(BACKUP_MIME_TYPES) },
        )

        BackupDialogs(
            state     = backupState,
            onConfirm = backupViewModel::confirmImport,
            onDismiss = backupViewModel::dismiss,
        )

        if (showPdfDialog) {
            PdfSignatureDialog(
                supervisors = uiState.supervisors,
                onDismiss = { showPdfDialog = false },
                onConfirm = { signatureName, signatureDate ->
                    onExportPdf(signatureName, signatureDate)
                    showPdfDialog = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfSignatureDialog(
    supervisors: List<Supervisor>,
    onDismiss: () -> Unit,
    onConfirm: (signatureName: String, signatureDate: String) -> Unit,
) {
    var selectedSupervisor by remember(supervisors) { mutableStateOf(supervisors.firstOrNull()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.dialog_pdf_signature_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                if (supervisors.isNotEmpty()) {
                    OutlinedTextField(
                        value = selectedSupervisor?.name.orEmpty(),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.hint_supervisor_name)) },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        supervisors.forEach { supervisor ->
                            DropdownMenuItem(
                                text = { Text("${supervisor.name} (${supervisor.initials})") },
                                onClick = {
                                    selectedSupervisor = supervisor
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.label_no_supervisors),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_date)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.button_pick_date))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                        Button(
                            onClick = {
                                onConfirm(
                                    selectedSupervisor?.name.orEmpty(),
                                    date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
                                )
                            },
                            enabled = selectedSupervisor != null,
                        ) { Text(stringResource(R.string.button_export)) }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toDatePickerUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = millis.toLocalDateFromDatePickerUtc()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Stateless export content — easy to preview without Hilt. */
@Composable
fun ExportContent(
    uiState:     ExportUiState,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportGeoJson: () -> Unit,
    modifier:    Modifier = Modifier,
    backupBusy:  Boolean = false,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- Summary card ----
        SummaryCard(uiState = uiState)

        // ---- PDF export ----
        ExportOptionCard(
            icon        = Icons.Default.Description,
            title       = stringResource(R.string.label_export_pdf),
            description = stringResource(R.string.desc_export_pdf),
            buttonLabel = stringResource(R.string.button_export_pdf),
            primary     = true,
            enabled     = uiState.sessionCount > 0 && uiState.supervisors.isNotEmpty(),
            onClick     = onExportPdf,
        )

        // ---- CSV export ----
        ExportOptionCard(
            icon        = Icons.Default.GridOn,
            title       = stringResource(R.string.label_export_csv),
            description = stringResource(R.string.desc_export_csv),
            buttonLabel = stringResource(R.string.button_export_csv),
            primary     = false,
            enabled     = uiState.sessionCount > 0,
            onClick     = onExportCsv,
        )

        ExportOptionCard(
            icon        = Icons.Default.Map,
            title       = stringResource(R.string.label_export_geojson),
            description = stringResource(R.string.desc_export_geojson),
            buttonLabel = stringResource(R.string.button_export_geojson),
            primary     = false,
            enabled     = uiState.routeSessionCount > 0,
            onClick     = onExportGeoJson,
        )

        BackupCard(
            busy     = backupBusy,
            onExport = onExportBackup,
            onImport = onImportBackup,
        )

        if (uiState.sessionCount == 0) {
            EmptyHint()
        } else if (uiState.supervisors.isEmpty()) {
            Text(
                text = stringResource(R.string.hint_signed_pdf_requires_supervisor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Summary card ----

@Composable
private fun SummaryCard(uiState: ExportUiState) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = "Log Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text  = stringResource(R.string.label_total_sessions) + ": ${uiState.sessionCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text  = stringResource(
                    R.string.label_total_hours_value,
                    uiState.totalHours,
                    uiState.nightHours,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---- Export option card ----

@Composable
private fun ExportOptionCard(
    icon:        androidx.compose.ui.graphics.vector.ImageVector,
    title:       String,
    description: String,
    buttonLabel: String,
    primary:     Boolean,
    enabled:     Boolean,
    onClick:     () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(28.dp),
                    tint               = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (primary) {
                Button(
                    onClick  = onClick,
                    enabled  = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(buttonLabel) }
            } else {
                OutlinedButton(
                    onClick  = onClick,
                    enabled  = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(buttonLabel) }
            }
        }
    }
}

// ---- Full backup ----

/** MIME types offered by the file picker. Other apps may label JSON as plain text or binary. */
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@Composable
private fun BackupCard(
    busy:     Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.SettingsBackupRestore,
                    contentDescription = null,
                    modifier           = Modifier.size(28.dp),
                    tint               = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text       = stringResource(R.string.label_backup),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text  = stringResource(R.string.desc_backup),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick  = onExport,
                    enabled  = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.button_backup_export)) }
                OutlinedButton(
                    onClick  = onImport,
                    enabled  = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.button_backup_import)) }
            }
        }
    }
}

@Composable
private fun BackupDialogs(
    state:     BackupUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is BackupUiState.ConfirmImport -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_backup_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Whole sentences, one per Text: translators can reword each.
                    Text(stringResource(R.string.dialog_backup_import_warning))
                    Text(
                        stringResource(
                            if (state.incoming.profile != null) R.string.dialog_backup_import_profile_replaced
                            else R.string.dialog_backup_import_profile_kept,
                        ),
                    )
                    Text(backupCountsText(R.string.dialog_backup_import_now, state.current))
                    Text(backupCountsText(R.string.dialog_backup_import_file, state.incoming))
                    Text(
                        stringResource(
                            R.string.dialog_backup_import_file_info,
                            state.incomingCreatedAt.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")),
                            state.incomingAppVersion,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.nightRecalculation?.let { night ->
                        Text(
                            stringResource(R.string.dialog_backup_import_night_recalculated),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (night.recalculated > 0) {
                            Text(
                                pluralStringResource(R.plurals.backup_night_recalculated_count, night.recalculated, night.recalculated),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (night.kept > 0) {
                            Text(
                                pluralStringResource(R.plurals.backup_night_kept_count, night.kept, night.kept),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(
                        stringResource(
                            if (state.current.hasData) R.string.dialog_backup_import_safety
                            else R.string.dialog_backup_import_no_safety,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) { Text(stringResource(R.string.button_backup_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is BackupUiState.ExportDone -> BackupMessageDialog(
            title     = stringResource(R.string.dialog_backup_done_title),
            message   = stringResource(R.string.backup_export_done, state.fileName),
            onDismiss = onDismiss,
        )

        is BackupUiState.ImportDone -> BackupMessageDialog(
            title     = stringResource(R.string.dialog_backup_done_title),
            message   = state.safetyBackupFileName
                ?.let { stringResource(R.string.backup_import_done_safety, it) }
                ?: stringResource(R.string.backup_import_done),
            onDismiss = onDismiss,
        )

        is BackupUiState.Failed -> BackupMessageDialog(
            title     = stringResource(R.string.dialog_backup_failed_title),
            message   = stringResource(state.error.messageRes()),
            onDismiss = onDismiss,
        )

        BackupUiState.Idle, BackupUiState.Working -> Unit
    }
}

@Composable
private fun backupCountsText(labelRes: Int, counts: BackupSummary): String =
    stringResource(
        R.string.dialog_backup_import_counts,
        stringResource(labelRes),
        pluralStringResource(R.plurals.backup_count_drives, counts.sessions, counts.sessions),
        pluralStringResource(R.plurals.backup_count_supervisors, counts.supervisors, counts.supervisors),
        pluralStringResource(R.plurals.backup_count_route_points, counts.routePoints, counts.routePoints),
    )

@Composable
private fun BackupMessageDialog(
    title:     String,
    message:   String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

private fun BackupError.messageRes(): Int = when (this) {
    BackupError.READ_FAILED          -> R.string.backup_error_read_failed
    BackupError.TOO_LARGE            -> R.string.backup_error_too_large
    BackupError.NOT_A_BACKUP         -> R.string.backup_error_not_a_backup
    BackupError.NEWER_FORMAT         -> R.string.backup_error_newer_format
    BackupError.INVALID_DATA         -> R.string.backup_error_invalid_data
    BackupError.WRITE_FAILED         -> R.string.backup_error_write_failed
    BackupError.SAFETY_BACKUP_FAILED -> R.string.backup_error_safety_backup_failed
    BackupError.DRIVE_IN_PROGRESS    -> R.string.backup_error_drive_in_progress
    BackupError.UNEXPECTED           -> R.string.backup_error_unexpected
}

// ---- Empty hint ----

@Composable
private fun EmptyHint() {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = Icons.Default.Info,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text  = stringResource(R.string.hint_record_drive_to_export),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Previews ----

@Preview(showBackground = true, name = "Export – with sessions")
@Composable
private fun PreviewExportWithSessions() {
    CoDriveLogTheme {
        ExportContent(
            uiState = ExportUiState(
                sessionCount = 12,
                routeSessionCount = 8,
                totalHours   = 23.5f,
                nightHours   = 4.0f,
            ),
            onExportPdf = {},
            onExportCsv = {},
            onExportGeoJson = {},
        )
    }
}

@Preview(showBackground = true, name = "Export – empty (buttons disabled)")
@Composable
private fun PreviewExportEmpty() {
    CoDriveLogTheme {
        ExportContent(
            uiState     = ExportUiState(),
            onExportPdf = {},
            onExportCsv = {},
            onExportGeoJson = {},
        )
    }
}
