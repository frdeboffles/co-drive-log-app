package com.codrivelog.app.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.ui.theme.CoDriveLogTheme

/**
 * Composable card widget that shows the live drive timer and Start/Stop controls.
 *
 * When idle it presents a "Start Drive" button.  When a session is active it
 * shows the elapsed time, night time, day/night indicator, and a "Stop Drive"
 * button.
 *
 * The widget manages an inline supervisor-name dialog: when the user taps
 * "Start Drive" a simple text-field row expands to capture supervisor details
 * before the service is started.
 *
 * @param modifier   Optional modifier for the outer card.
 * @param viewModel  Injected via Hilt by default.
 */
@Composable
fun DriveTimerWidget(
    modifier: Modifier = Modifier,
    viewModel: DriveTimerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val supervisors by viewModel.supervisors.collectAsStateWithLifecycle()
    DriveTimerWidgetContent(
        uiState      = uiState,
        supervisors  = supervisors,
        onStart      = { name, initials -> viewModel.startDrive(name, initials) },
        onStop       = viewModel::stopDrive,
        modifier     = modifier,
    )
}

/**
 * Stateless inner composable — receives all state and callbacks, easy to Preview.
 */
@Composable
fun DriveTimerWidgetContent(
    uiState: DriveTimerUiState,
    supervisors: List<Supervisor>,
    onStart: (supervisorName: String, supervisorInitials: String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (uiState) {
                DriveTimerUiState.Idle -> IdleContent(
                    supervisors = supervisors,
                    onStart = onStart,
                )
                is DriveTimerUiState.Active -> ActiveContent(state = uiState, onStop = onStop)
            }
        }
    }
}

// ---- Idle content ----

@Composable
private fun IdleContent(
    supervisors: List<Supervisor>,
    onStart: (supervisorName: String, supervisorInitials: String) -> Unit,
) {
    var showForm by remember { mutableStateOf(false) }
    var supervisorName by rememberSaveable { mutableStateOf("") }
    var supervisorInitials by rememberSaveable { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var manualEntry by rememberSaveable { mutableStateOf(supervisors.isEmpty()) }

    LaunchedEffect(supervisors.isEmpty()) {
        if (supervisors.isEmpty()) manualEntry = true
    }

    if (!showForm) {
        Button(
            onClick  = { showForm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.button_start_drive))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (supervisors.isNotEmpty()) {
                OutlinedTextField(
                    value = if (!manualEntry && supervisorName.isBlank()) {
                        stringResource(R.string.label_select_supervisor)
                    } else {
                        supervisorName
                    },
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_supervisor)) },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { dropdownExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                            )
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
                                supervisorName = supervisor.name
                                supervisorInitials = supervisor.initials
                                manualEntry = false
                                dropdownExpanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_enter_manually)) },
                        onClick = {
                            supervisorName = ""
                            supervisorInitials = ""
                            manualEntry = true
                            dropdownExpanded = false
                        },
                    )
                }
            }

            if (supervisors.isEmpty() || manualEntry) {
                OutlinedTextField(
                    value = supervisorName,
                    onValueChange = { supervisorName = it },
                    label = { Text(stringResource(R.string.hint_supervisor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supervisorInitials,
                    onValueChange = { supervisorInitials = it.uppercase().take(4) },
                    label = { Text(stringResource(R.string.hint_supervisor_initials)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.label_initials) + ": " + supervisorInitials,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        showForm = false
                        dropdownExpanded = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        if (supervisorName.isNotBlank() && supervisorInitials.isNotBlank()) {
                            onStart(supervisorName.trim(), supervisorInitials.trim())
                            showForm = false
                        }
                    },
                    enabled = supervisorName.isNotBlank() && supervisorInitials.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.button_start_drive)) }
            }
        }
    }
}

// ---- Active content ----

@Composable
private fun ActiveContent(
    state:  DriveTimerUiState.Active,
    onStop: () -> Unit,
) {
    Row(
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text  = state.elapsedFormatted,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text  = stringResource(R.string.label_night_time) + ": " + state.nightFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DayNightIndicator(
            isNight   = state.currentlyNight,
            hasGpsFix = state.hasGpsFix,
        )
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick  = onStop,
        modifier = Modifier.fillMaxWidth(),
        colors   = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(stringResource(R.string.button_stop_drive))
    }
}

// ---- Day/night indicator chip ----

@Composable
private fun DayNightIndicator(isNight: Boolean, hasGpsFix: Boolean) {
    if (!hasGpsFix) {
        Text(
            text  = stringResource(R.string.label_no_gps),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isNight) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
            contentDescription = if (isNight) stringResource(R.string.label_night_indicator)
                                 else stringResource(R.string.label_day_indicator),
            modifier = Modifier.size(20.dp),
            tint     = if (isNight) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text  = if (isNight) stringResource(R.string.label_night_indicator)
                    else stringResource(R.string.label_day_indicator),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ---- Previews ----

@Preview(showBackground = true, name = "Timer – Idle")
@Composable
private fun PreviewTimerIdle() {
    CoDriveLogTheme {
        DriveTimerWidgetContent(
            uiState  = DriveTimerUiState.Idle,
            supervisors = emptyList(),
            onStart  = { _, _ -> },
            onStop   = {},
        )
    }
}

@Preview(showBackground = true, name = "Timer – Active / Day")
@Composable
private fun PreviewTimerActiveDay() {
    CoDriveLogTheme {
        DriveTimerWidgetContent(
            uiState  = DriveTimerUiState.Active(
                elapsedFormatted = "1:23:45",
                nightFormatted   = "0:15",
                currentlyNight   = false,
                hasGpsFix        = true,
            ),
            supervisors = emptyList(),
            onStart  = { _, _ -> },
            onStop   = {},
        )
    }
}

@Preview(showBackground = true, name = "Timer – Active / Night / No GPS")
@Composable
private fun PreviewTimerActiveNightNoGps() {
    CoDriveLogTheme {
        DriveTimerWidgetContent(
            uiState  = DriveTimerUiState.Active(
                elapsedFormatted = "0:45:00",
                nightFormatted   = "0:45",
                currentlyNight   = true,
                hasGpsFix        = false,
            ),
            supervisors = emptyList(),
            onStart  = { _, _ -> },
            onStop   = {},
        )
    }
}
