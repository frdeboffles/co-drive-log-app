package com.codrivelog.app.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Manual drive entry screen.
 *
 * Lets the user retroactively record a past drive with a date picker,
 * start/end time pickers, supervisor selection and optional comments.
 *
 * On successful save, [onSaved] is invoked to pop the back-stack.
 *
 * @param onBack    Invoked when the user taps the back arrow.
 * @param onSaved   Invoked after the drive is successfully persisted.
 * @param viewModel [ManualEntryViewModel] provided by Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    onBack:    () -> Unit = {},
    onSaved:   () -> Unit = {},
    viewModel: ManualEntryViewModel = hiltViewModel(),
) {
    val supervisors by viewModel.supervisors.collectAsStateWithLifecycle()
    val saveState   by viewModel.saveState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate away once the record is saved
    LaunchedEffect(saveState) {
        if (saveState == SaveState.Saved) {
            viewModel.resetState()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_manual_entry)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        ManualEntryForm(
            modifier    = Modifier.padding(padding),
            supervisors = supervisors,
            saveState   = saveState,
            onSave      = { date, start, end, name, initials, comments ->
                viewModel.save(date, start, end, name, initials, comments)
            },
        )
    }
}

/** Stateless form content — testable without Hilt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryForm(
    supervisors: List<Supervisor>,
    saveState:   SaveState,
    onSave:      (LocalDate, LocalTime, LocalTime, String, String, String) -> Unit,
    modifier:    Modifier = Modifier,
) {
    // --- Form state ---
    var selectedDate       by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var startTime          by rememberSaveable { mutableStateOf(LocalTime.of(16, 0)) }
    var endTime            by rememberSaveable { mutableStateOf(LocalTime.of(17, 0)) }
    var supervisorName     by rememberSaveable { mutableStateOf("") }
    var supervisorInitials by rememberSaveable { mutableStateOf("") }
    var comments           by rememberSaveable { mutableStateOf("") }
    var dropdownExpanded   by remember { mutableStateOf(false) }

    // --- Dialog state ---
    var showDatePicker      by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker   by remember { mutableStateOf(false) }

    val dateFormatter   = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormatter   = remember { DateTimeFormatter.ofPattern("h:mm a") }

    val endTimeError = saveState is SaveState.ValidationError &&
            (saveState as SaveState.ValidationError).field == Field.END_TIME
    val nameError = saveState is SaveState.ValidationError &&
            (saveState as SaveState.ValidationError).field == Field.SUPERVISOR_NAME
    val initialsError = saveState is SaveState.ValidationError &&
            (saveState as SaveState.ValidationError).field == Field.SUPERVISOR_INITIALS

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Date ----
        OutlinedTextField(
            value         = selectedDate.format(dateFormatter),
            onValueChange = {},
            readOnly      = true,
            label         = { Text(stringResource(R.string.label_date)) },
            trailingIcon  = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                }
            },
            modifier      = Modifier.fillMaxWidth(),
        )

        // ---- Start time ----
        OutlinedTextField(
            value         = startTime.format(timeFormatter),
            onValueChange = {},
            readOnly      = true,
            label         = { Text(stringResource(R.string.label_start_time)) },
            trailingIcon  = {
                IconButton(onClick = { showStartTimePicker = true }) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                }
            },
            modifier      = Modifier.fillMaxWidth(),
        )

        // ---- End time ----
        OutlinedTextField(
            value         = endTime.format(timeFormatter),
            onValueChange = {},
            readOnly      = true,
            label         = { Text(stringResource(R.string.label_end_time)) },
            isError       = endTimeError,
            supportingText = if (endTimeError) {
                { Text(stringResource(R.string.error_end_before_start)) }
            } else null,
            trailingIcon  = {
                IconButton(onClick = { showEndTimePicker = true }) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                }
            },
            modifier      = Modifier.fillMaxWidth(),
        )

        // ---- Supervisor ----
        if (supervisors.isNotEmpty()) {
            // Dropdown from saved supervisors
            OutlinedTextField(
                value         = supervisorName.ifBlank {
                    stringResource(R.string.label_select_supervisor)
                },
                onValueChange = {},
                readOnly      = true,
                label         = { Text(stringResource(R.string.label_supervisor)) },
                isError       = nameError,
                trailingIcon  = {
                    IconButton(onClick = { dropdownExpanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                },
                modifier      = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded         = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                supervisors.forEach { s ->
                    DropdownMenuItem(
                        text    = { Text("${s.name}  (${s.initials})") },
                        onClick = {
                            supervisorName     = s.name
                            supervisorInitials = s.initials
                            dropdownExpanded   = false
                        },
                    )
                }
                DropdownMenuItem(
                    text    = { Text("Enter manually…") },
                    onClick = {
                        supervisorName     = ""
                        supervisorInitials = ""
                        dropdownExpanded   = false
                    },
                )
            }
        }

        // Manual name/initials fields (always shown when no supervisors or manual selected)
        if (supervisors.isEmpty() || supervisorName.isBlank()) {
            OutlinedTextField(
                value         = supervisorName,
                onValueChange = { supervisorName = it },
                label         = { Text(stringResource(R.string.label_supervisor)) },
                isError       = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.error_supervisor_required)) }
                } else null,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value         = supervisorInitials,
                onValueChange = { supervisorInitials = it.uppercase().take(4) },
                label         = { Text(stringResource(R.string.label_initials)) },
                isError       = initialsError,
                supportingText = if (initialsError) {
                    { Text(stringResource(R.string.error_initials_required)) }
                } else null,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
        }

        // ---- Comments ----
        OutlinedTextField(
            value         = comments,
            onValueChange = { comments = it },
            label         = { Text(stringResource(R.string.label_comments)) },
            minLines      = 2,
            maxLines      = 4,
            modifier      = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick  = {
                onSave(
                    selectedDate,
                    startTime,
                    endTime,
                    supervisorName,
                    supervisorInitials,
                    comments,
                )
            },
            enabled  = saveState !is SaveState.Saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                text  = if (saveState is SaveState.Saving) "Saving…"
                        else stringResource(R.string.button_save_drive),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    // ---- Date picker dialog ----
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toDatePickerUtcMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = millis.toLocalDateFromDatePickerUtc()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton    = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ---- Start time picker dialog ----
    if (showStartTimePicker) {
        val state = rememberTimePickerState(
            initialHour   = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour      = false,
        )
        TimePickerDialog(
            onDismiss  = { showStartTimePicker = false },
            onConfirm  = {
                startTime           = LocalTime.of(state.hour, state.minute)
                showStartTimePicker = false
            },
        ) { TimePicker(state = state) }
    }

    // ---- End time picker dialog ----
    if (showEndTimePicker) {
        val state = rememberTimePickerState(
            initialHour   = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour      = false,
        )
        TimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endTime           = LocalTime.of(state.hour, state.minute)
                showEndTimePicker = false
            },
        ) { TimePicker(state = state) }
    }
}

// ---- Time picker wrapper dialog ----

/**
 * Wraps Material 3 [TimePicker] in a plain [Dialog] with OK / Cancel buttons,
 * since Material 3 does not ship a dedicated TimePickerDialog yet.
 */
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content:   @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                content()
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true, name = "ManualEntry – empty form")
@Composable
private fun PreviewManualEntryEmpty() {
    CoDriveLogTheme {
        ManualEntryForm(
            supervisors = emptyList(),
            saveState   = SaveState.Idle,
            onSave      = { _, _, _, _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "ManualEntry – with supervisors")
@Composable
private fun PreviewManualEntryWithSupervisors() {
    CoDriveLogTheme {
        ManualEntryForm(
            supervisors = listOf(
                Supervisor(id = 1, name = "Jane Doe",   initials = "JD"),
                Supervisor(id = 2, name = "John Smith", initials = "JS"),
            ),
            saveState   = SaveState.Idle,
            onSave      = { _, _, _, _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, name = "ManualEntry – validation error")
@Composable
private fun PreviewManualEntryError() {
    CoDriveLogTheme {
        ManualEntryForm(
            supervisors = emptyList(),
            saveState   = SaveState.ValidationError(Field.END_TIME),
            onSave      = { _, _, _, _, _, _ -> },
        )
    }
}
