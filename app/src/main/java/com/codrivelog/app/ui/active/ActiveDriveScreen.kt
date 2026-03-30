package com.codrivelog.app.ui.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import com.codrivelog.app.ui.supervisor.SupervisorViewModel
import com.codrivelog.app.ui.timer.DriveTimerUiState
import com.codrivelog.app.ui.timer.DriveTimerViewModel

/**
 * Full-screen active drive screen.
 *
 * Shows a large timer display when a drive is running, or a supervisor picker
 * and start button when idle. Includes day/night indicator based on GPS/NOAA.
 *
 * @param onBack       Invoked when the back button is pressed.
 * @param timerVm      [DriveTimerViewModel] controlling the foreground service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveDriveScreen(
    onBack:   () -> Unit = {},
    timerVm:  DriveTimerViewModel = hiltViewModel(),
    supervisorVm: SupervisorViewModel = hiltViewModel(),
) {
    val uiState     by timerVm.uiState.collectAsStateWithLifecycle()
    val supervisors by supervisorVm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_active_drive)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        ActiveDriveContent(
            modifier        = Modifier.padding(padding),
            uiState         = uiState,
            supervisors     = supervisors.supervisors,
            onStart         = { name, initials -> timerVm.startDrive(name, initials) },
            onStop          = timerVm::stopDrive,
            onNightOverride = timerVm::setManualNightOverride,
        )
    }
}

/** Stateless content — easy to test and preview. */
@Composable
fun ActiveDriveContent(
    uiState:         DriveTimerUiState,
    supervisors:     List<Supervisor>,
    onStart:         (name: String, initials: String) -> Unit,
    onStop:          () -> Unit,
    onNightOverride: (Boolean) -> Unit = {},
    modifier:        Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        when (uiState) {
            DriveTimerUiState.Idle -> IdleControls(
                supervisors = supervisors,
                onStart     = onStart,
            )
            is DriveTimerUiState.Active -> ActiveDisplay(
                state           = uiState,
                onStop          = onStop,
                onNightOverride = onNightOverride,
            )
        }
    }
}

// ---- Idle controls ----

@Composable
private fun IdleControls(
    supervisors: List<Supervisor>,
    onStart:     (name: String, initials: String) -> Unit,
) {
    var selectedSupervisor  by remember { mutableStateOf<Supervisor?>(null) }
    var dropdownExpanded    by remember { mutableStateOf(false) }

    if (supervisors.isNotEmpty() && selectedSupervisor == null) {
        selectedSupervisor = supervisors.first()
    }

    Text(
        text  = "Ready to Start",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )

    // Supervisor picker
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text  = stringResource(R.string.label_supervisor),
                style = MaterialTheme.typography.titleSmall,
            )
            if (supervisors.isNotEmpty()) {
                // Dropdown from saved supervisors
                Box {
                    OutlinedButton(
                        onClick  = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = selectedSupervisor?.name ?: stringResource(R.string.label_select_supervisor), modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded         = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        supervisors.forEach { s ->
                            DropdownMenuItem(
                                text    = { Text("${s.name}  (${s.initials})") },
                                onClick = {
                                    selectedSupervisor = s
                                    dropdownExpanded   = false
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.label_no_supervisors),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val effectiveName     = selectedSupervisor?.name.orEmpty()
    val effectiveInitials = selectedSupervisor?.initials.orEmpty()
    val canStart          = effectiveName.isNotBlank() && effectiveInitials.isNotBlank()

    Button(
        onClick  = { onStart(effectiveName, effectiveInitials) },
        enabled  = canStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(
            text  = stringResource(R.string.button_start_drive),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ---- Active display ----

@Composable
private fun ActiveDisplay(
    state:           DriveTimerUiState.Active,
    onStop:          () -> Unit,
    onNightOverride: (Boolean) -> Unit,
) {
    // Large day/night badge
    DayNightBadge(isNight = state.currentlyNight, hasGpsFix = state.hasGpsFix)

    // Manual night override toggle — only shown when GPS is unavailable
    if (state.manualOverrideAvailable) {
        ManualNightToggle(
            isNight  = state.manualNightOverride,
            onChange = onNightOverride,
        )
    }

    // Giant timer
    Text(
        text  = state.elapsedFormatted,
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
    )

    Text(
        text  = stringResource(R.string.label_elapsed_time),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Night time card
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.NightsStay,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = stringResource(R.string.label_night_time),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text       = state.nightFormatted,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    Button(
        onClick  = onStop,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(
            text  = stringResource(R.string.button_stop_drive),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ---- Manual night toggle (shown only when GPS is unavailable) ----

@Composable
private fun ManualNightToggle(
    isNight:  Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = stringResource(R.string.label_manual_night_toggle),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text  = stringResource(R.string.label_manual_night_toggle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked         = isNight,
                onCheckedChange = onChange,
            )
        }
    }
}

// ---- Day/night badge ----

@Composable
private fun DayNightBadge(isNight: Boolean, hasGpsFix: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isNight) MaterialTheme.colorScheme.primaryContainer
                else          MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isNight) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                contentDescription = null,
                modifier    = Modifier.size(24.dp),
            )
            Text(
                text  = if (!hasGpsFix) stringResource(R.string.label_no_gps)
                        else if (isNight) stringResource(R.string.label_night_indicator)
                        else stringResource(R.string.label_day_indicator),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true, name = "ActiveDrive – Idle (no supervisors)")
@Composable
private fun PreviewActiveDriveIdle() {
    CoDriveLogTheme {
        ActiveDriveContent(
            uiState     = DriveTimerUiState.Idle,
            supervisors = emptyList(),
            onStart     = { _, _ -> },
            onStop      = {},
        )
    }
}

@Preview(showBackground = true, name = "ActiveDrive – Idle (with supervisors)")
@Composable
private fun PreviewActiveDriveIdleWithSupervisors() {
    CoDriveLogTheme {
        ActiveDriveContent(
            uiState = DriveTimerUiState.Idle,
            supervisors = listOf(
                Supervisor(id = 1, name = "Jane Doe", initials = "JD"),
                Supervisor(id = 2, name = "John Smith", initials = "JS"),
            ),
            onStart = { _, _ -> },
            onStop  = {},
        )
    }
}

@Preview(showBackground = true, name = "ActiveDrive – Running / Day")
@Composable
private fun PreviewActiveDriveRunningDay() {
    CoDriveLogTheme {
        ActiveDriveContent(
            uiState = DriveTimerUiState.Active(
                elapsedFormatted = "1:23:45",
                nightFormatted   = "0:15",
                currentlyNight   = false,
                hasGpsFix        = true,
            ),
            supervisors = emptyList(),
            onStart     = { _, _ -> },
            onStop      = {},
        )
    }
}

@Preview(showBackground = true, name = "ActiveDrive – Running / Night")
@Composable
private fun PreviewActiveDriveRunningNight() {
    CoDriveLogTheme {
        ActiveDriveContent(
            uiState = DriveTimerUiState.Active(
                elapsedFormatted = "0:45:30",
                nightFormatted   = "0:45",
                currentlyNight   = true,
                hasGpsFix        = true,
            ),
            supervisors = emptyList(),
            onStart     = { _, _ -> },
            onStop      = {},
        )
    }
}

@Preview(showBackground = true, name = "ActiveDrive – Running / No GPS (manual night)")
@Composable
private fun PreviewActiveDriveNoGps() {
    CoDriveLogTheme {
        ActiveDriveContent(
            uiState = DriveTimerUiState.Active(
                elapsedFormatted        = "0:30:00",
                nightFormatted          = "0:30",
                currentlyNight          = true,
                hasGpsFix               = false,
                manualNightOverride     = true,
                manualOverrideAvailable = true,
            ),
            supervisors = emptyList(),
            onStart     = { _, _ -> },
            onStop      = {},
        )
    }
}
