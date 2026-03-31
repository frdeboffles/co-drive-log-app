package com.codrivelog.app.ui.export

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack:      () -> Unit = {},
    onExportPdf: (signatureName: String, signatureDate: String) -> Unit = { _, _ -> },
    onExportCsv: () -> Unit = {},
    onExportGeoJson: () -> Unit = {},
    viewModel:   ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPdfDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_export)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
