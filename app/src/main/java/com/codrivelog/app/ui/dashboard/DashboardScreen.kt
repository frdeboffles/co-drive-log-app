package com.codrivelog.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
import com.codrivelog.app.ui.theme.CoDriveLogTheme

/**
 * Main dashboard screen showing progress toward Colorado DR 2324 driving-hour
 * requirements (50 total hours, 10 night hours) and providing navigation to
 * drive entry and export.
 *
 * @param onAddEntry Callback invoked when the user taps the Add Entry FAB.
 * @param onExport   Callback invoked when the user taps the Export action.
 * @param viewModel  Injected [DashboardViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddEntry: () -> Unit,
    onExport: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_export),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_add_entry)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onAddEntry,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressCard(
                label = stringResource(R.string.label_total_hours),
                current = uiState.totalHours,
                goal = DashboardViewModel.GOAL_TOTAL_HOURS,
            )
            ProgressCard(
                label = stringResource(R.string.label_night_hours),
                current = uiState.nightHours,
                goal = DashboardViewModel.GOAL_NIGHT_HOURS,
            )
        }
    }
}

/**
 * Card displaying a labeled progress indicator toward a driving-hours goal.
 *
 * @param label   Human-readable label (e.g. "Total Hours").
 * @param current Accumulated hours so far.
 * @param goal    Target hours required by Colorado DMV.
 */
@Composable
fun ProgressCard(
    label: String,
    current: Float,
    goal: Float,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.progress_fraction, current, goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { (current / goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    CoDriveLogTheme {
        ProgressCard(label = "Total Hours", current = 23f, goal = 50f)
    }
}
