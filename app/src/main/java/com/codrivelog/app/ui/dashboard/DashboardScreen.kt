package com.codrivelog.app.ui.dashboard

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import com.codrivelog.app.ui.timer.DriveTimerWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Main dashboard screen showing circular progress toward Colorado DR 2324
 * driving-hour requirements (50 total, 10 night), a list of recent drives,
 * and the live-drive timer widget.
 *
 * @param onAddEntry     Invoked when the user taps the "Add Entry" FAB.
 * @param onExport       Invoked when the user taps the export action.
 * @param onViewHistory  Invoked when the user taps "See all" on the recent drives card.
 * @param onSupervisors  Invoked when the user taps the supervisors action.
 * @param timerWidget    Composable slot for the timer widget; defaults to [DriveTimerWidget].
 *                       Pass a no-op lambda in tests to avoid Hilt lookup.
 * @param viewModel      Injected [DashboardViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddEntry:    () -> Unit,
    onExport:      () -> Unit,
    onViewHistory: () -> Unit = {},
    onSupervisors: () -> Unit = {},
    timerWidget:   @Composable () -> Unit = { DriveTimerWidget() },
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSupervisors) {
                        Icon(
                            imageVector        = Icons.Default.People,
                            contentDescription = stringResource(R.string.screen_supervisors),
                        )
                    }
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector        = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_export),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text  = { Text(stringResource(R.string.action_add_entry)) },
                icon  = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onAddEntry,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Circular progress cards ----
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularHoursCard(
                    modifier  = Modifier.weight(1f),
                    label     = stringResource(R.string.label_total_hours),
                    icon      = Icons.Default.DirectionsCar,
                    current   = uiState.totalHours,
                    goal      = DashboardViewModel.GOAL_TOTAL_HOURS,
                )
                CircularHoursCard(
                    modifier  = Modifier.weight(1f),
                    label     = stringResource(R.string.label_night_hours),
                    icon      = Icons.Default.NightsStay,
                    current   = uiState.nightHours,
                    goal      = DashboardViewModel.GOAL_NIGHT_HOURS,
                )
            }

            // ---- Live timer widget ----
            timerWidget()

            // ---- Recent drives ----
            RecentDrivesCard(
                drives        = uiState.recentDrives,
                onViewHistory = onViewHistory,
            )
        }
    }
}

// ---- Circular progress card ----

/**
 * Square card with a large [CircularProgressIndicator] showing hours progress.
 */
@Composable
fun CircularHoursCard(
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    current:  Float,
    goal:     Float,
    modifier: Modifier = Modifier,
) {
    val fraction = (current / goal).coerceIn(0f, 1f)

    Card(
        modifier  = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress            = { fraction },
                    modifier            = Modifier.size(88.dp),
                    strokeWidth         = 8.dp,
                    trackColor          = MaterialTheme.colorScheme.surfaceVariant,
                )
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    modifier           = Modifier.size(28.dp),
                    tint               = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text  = stringResource(R.string.progress_fraction, current, goal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---- Recent drives card ----

@Composable
private fun RecentDrivesCard(
    drives:        List<DriveSession>,
    onViewHistory: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.History,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp),
                        tint               = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = "Recent Drives",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                TextButton(onClick = onViewHistory) {
                    Text(text = "See all", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (drives.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.label_no_drives),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                drives.forEach { drive ->
                    RecentDriveRow(drive = drive)
                }
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun RecentDriveRow(drive: DriveSession) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = drive.date.format(dateFormatter),
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text     = drive.supervisorName,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val totalH = drive.totalMinutes / 60
            val totalM = drive.totalMinutes % 60
            Text(
                text  = "%d:%02d".format(totalH, totalM),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (drive.nightMinutes > 0) {
                Text(
                    text  = "%dm night".format(drive.nightMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true, name = "Dashboard – empty state")
@Composable
private fun DashboardScreenPreviewEmpty() {
    CoDriveLogTheme {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label    = "Total Hours",
                    icon     = Icons.Default.DirectionsCar,
                    current  = 0f,
                    goal     = 50f,
                )
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label    = "Night Hours",
                    icon     = Icons.Default.NightsStay,
                    current  = 0f,
                    goal     = 10f,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Dashboard – with progress")
@Composable
private fun DashboardScreenPreviewProgress() {
    val sampleDrives = listOf(
        DriveSession(
            id = 1, date = LocalDate.now(), isManualEntry = false,
            startTime = LocalDateTime.now().minusHours(2),
            endTime = LocalDateTime.now(),
            totalMinutes = 120, nightMinutes = 30,
            supervisorName = "Jane Doe", supervisorInitials = "JD",
        ),
        DriveSession(
            id = 2, date = LocalDate.now().minusDays(1), isManualEntry = true,
            startTime = LocalDateTime.now().minusDays(1).minusHours(1),
            endTime = LocalDateTime.now().minusDays(1),
            totalMinutes = 60, nightMinutes = 0,
            supervisorName = "John Smith", supervisorInitials = "JS",
        ),
    )
    CoDriveLogTheme {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label    = "Total Hours",
                    icon     = Icons.Default.DirectionsCar,
                    current  = 23f,
                    goal     = 50f,
                )
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label    = "Night Hours",
                    icon     = Icons.Default.NightsStay,
                    current  = 4.5f,
                    goal     = 10f,
                )
            }
            RecentDrivesCard(drives = sampleDrives, onViewHistory = {})
        }
    }
}

@Preview(showBackground = true, name = "CircularHoursCard – complete")
@Composable
private fun CircularHoursCardCompletePreview() {
    CoDriveLogTheme {
        CircularHoursCard(
            label   = "Total Hours",
            icon    = Icons.Default.DirectionsCar,
            current = 50f,
            goal    = 50f,
        )
    }
}
