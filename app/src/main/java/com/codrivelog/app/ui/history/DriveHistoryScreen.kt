package com.codrivelog.app.ui.history

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.BuildConfig
import com.codrivelog.app.R
import com.codrivelog.app.data.model.DriveSession
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveHistoryScreen(
    onBack: () -> Unit = {},
    viewModel: DriveHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingSession by remember { mutableStateOf<DriveSession?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_drive_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        DriveHistoryContent(
            modifier = Modifier.padding(padding),
            sessions = uiState.sessions,
            sessionIdsWithRoute = uiState.sessionIdsWithRoute,
            onDelete = viewModel::delete,
            onEdit = { editingSession = it },
            onViewRoute = { session ->
                scope.launch {
                    val url = viewModel.buildGoogleMapsDirectionsUrl(session.id)
                    if (url == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.error_no_route_for_session),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            },
            onSeed = { if (BuildConfig.DEBUG) viewModel.seedRandomEntries(100) },
            onClearAll = { if (BuildConfig.DEBUG) viewModel.clearAll() },
        )
    }

    editingSession?.let { session ->
        EditDriveDialog(
            drive = session,
            onDismiss = { editingSession = null },
            onSave = { date, startTime, endTime, supervisorName, supervisorInitials, comments ->
                viewModel.update(
                    session = session,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    supervisorName = supervisorName,
                    supervisorInitials = supervisorInitials,
                    comments = comments,
                )
                editingSession = null
            },
        )
    }
}

@Composable
fun DriveHistoryContent(
    sessions: List<DriveSession>,
    sessionIdsWithRoute: Set<Long>,
    onDelete: (DriveSession) -> Unit,
    onEdit: (DriveSession) -> Unit,
    onViewRoute: (DriveSession) -> Unit,
    onSeed: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (BuildConfig.DEBUG) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onSeed) { Text("Seed 100 entries") }
                        OutlinedButton(onClick = onClearAll) { Text("Clear all") }
                    }
                    Spacer(Modifier.size(16.dp))
                }
                Text(
                    text = stringResource(R.string.label_no_drives),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (BuildConfig.DEBUG) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onSeed) { Text("Seed 100 entries") }
                OutlinedButton(onClick = onClearAll) { Text("Clear all") }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = sessions, key = { it.id }) { session ->
                DriveSessionCard(
                    session = session,
                    hasRoute = sessionIdsWithRoute.contains(session.id),
                    onDelete = { onDelete(session) },
                    onEdit = { onEdit(session) },
                    onViewRoute = { onViewRoute(session) },
                )
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun DriveSessionCard(
    session: DriveSession,
    hasRoute: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onViewRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.date.format(dateFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${session.startTime.format(timeFormatter)} - ${session.endTime.format(timeFormatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val h = session.totalMinutes / 60
                    val m = session.totalMinutes % 60
                    Text(
                        text = "%d:%02d hrs".format(h, m),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row {
                        if (hasRoute) {
                            TextButton(onClick = onViewRoute) {
                                Text(stringResource(R.string.action_view_route))
                            }
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit drive")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_drive))
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = session.supervisorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.nightMinutes > 0) {
                    val nightH = session.nightMinutes / 60
                    val nightM = session.nightMinutes % 60
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NightsStay, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("%d:%02d hrs night".format(nightH, nightM), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
                if (session.isManualEntry) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = stringResource(R.string.label_manual_entry_badge),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            if (!session.comments.isNullOrBlank()) {
                Text(
                    text = session.comments,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDriveDialog(
    drive: DriveSession,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalTime, LocalTime, String, String, String) -> Unit,
) {
    var selectedDate by remember(drive.id) { mutableStateOf(drive.date) }
    var startTime by remember(drive.id) { mutableStateOf(drive.startTime.toLocalTime()) }
    var endTime by remember(drive.id) { mutableStateOf(drive.endTime.toLocalTime()) }
    var supervisorName by remember(drive.id) { mutableStateOf(drive.supervisorName) }
    var supervisorInitials by remember(drive.id) { mutableStateOf(drive.supervisorInitials) }
    var comments by remember(drive.id) { mutableStateOf(drive.comments.orEmpty()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val canSave = supervisorName.isNotBlank() && supervisorInitials.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Edit drive", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = selectedDate.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startTime.format(timeFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Start Time") },
                    trailingIcon = {
                        IconButton(onClick = { showStartTimePicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endTime.format(timeFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("End Time") },
                    trailingIcon = {
                        IconButton(onClick = { showEndTimePicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supervisorName,
                    onValueChange = { supervisorName = it },
                    label = { Text("Supervisor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supervisorInitials,
                    onValueChange = { supervisorInitials = it.uppercase().take(4) },
                    label = { Text("Initials") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Comments") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            onSave(selectedDate, startTime, endTime, supervisorName, supervisorInitials, comments)
                        },
                        enabled = canSave,
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
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

    if (showStartTimePicker) {
        val state = rememberTimePickerState(
            initialHour = startTime.hour,
            initialMinute = startTime.minute,
            is24Hour = false,
        )
        TimePickerDialog(
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startTime = LocalTime.of(state.hour, state.minute)
                showStartTimePicker = false
            },
        ) { TimePicker(state = state) }
    }
    if (showEndTimePicker) {
        val state = rememberTimePickerState(
            initialHour = endTime.hour,
            initialMinute = endTime.minute,
            is24Hour = false,
        )
        TimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endTime = LocalTime.of(state.hour, state.minute)
                showEndTimePicker = false
            },
        ) { TimePicker(state = state) }
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(24.dp)) {
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}

private val sampleSessions = listOf(
    DriveSession(
        id = 1,
        date = LocalDate.now(),
        startTime = LocalDateTime.now().minusHours(2),
        endTime = LocalDateTime.now(),
        totalMinutes = 120,
        nightMinutes = 35,
        supervisorName = "Jane Doe",
        supervisorInitials = "JD",
        isManualEntry = false,
        comments = null,
    ),
    DriveSession(
        id = 2,
        date = LocalDate.now().minusDays(1),
        startTime = LocalDateTime.now().minusDays(1).minusMinutes(75),
        endTime = LocalDateTime.now().minusDays(1),
        totalMinutes = 75,
        nightMinutes = 0,
        supervisorName = "John Smith",
        supervisorInitials = "JS",
        isManualEntry = true,
        comments = "Highway practice on I-25",
    ),
)

@Preview(showBackground = true, name = "DriveHistory - with sessions")
@Composable
private fun PreviewDriveHistoryList() {
    CoDriveLogTheme {
        DriveHistoryContent(
            sessions = sampleSessions,
            sessionIdsWithRoute = setOf(1L),
            onDelete = {},
            onEdit = {},
            onViewRoute = {},
            onSeed = {},
            onClearAll = {},
        )
    }
}

@Preview(showBackground = true, name = "DriveHistory - empty")
@Composable
private fun PreviewDriveHistoryEmpty() {
    CoDriveLogTheme {
        DriveHistoryContent(
            sessions = emptyList(),
            sessionIdsWithRoute = emptySet(),
            onDelete = {},
            onEdit = {},
            onViewRoute = {},
            onSeed = {},
            onClearAll = {},
        )
    }
}
