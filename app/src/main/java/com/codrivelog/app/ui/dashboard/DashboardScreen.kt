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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.codrivelog.app.ui.timer.DriveTimerWidget
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddEntry: () -> Unit,
    onExport: () -> Unit,
    onViewHistory: () -> Unit = {},
    onSupervisors: () -> Unit = {},
    timerWidget: @Composable () -> Unit = { DriveTimerWidget() },
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showProfileEditor by remember { mutableStateOf(false) }
    var editingDrive by remember { mutableStateOf<DriveSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showProfileEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit student profile")
                    }
                    IconButton(onClick = onSupervisors) {
                        Icon(Icons.Default.People, contentDescription = stringResource(R.string.screen_supervisors))
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_export))
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
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.label_total_hours),
                    icon = Icons.Default.DirectionsCar,
                    current = uiState.totalHours,
                    goal = DashboardViewModel.GOAL_TOTAL_HOURS,
                )
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.label_night_hours),
                    icon = Icons.Default.NightsStay,
                    current = uiState.nightHours,
                    goal = DashboardViewModel.GOAL_NIGHT_HOURS,
                )
            }

            timerWidget()

            RecentDrivesCard(
                drives = uiState.recentDrives,
                onViewHistory = onViewHistory,
                onEditDrive = { editingDrive = it },
                onDeleteDrive = viewModel::deleteSession,
                onSeedEntries = { if (BuildConfig.DEBUG) viewModel.seedRandomEntries(100) },
            )
        }
    }

    if (showProfileEditor) {
        ProfileEditDialog(
            studentName = uiState.studentName,
            permitNumber = uiState.permitNumber,
            onDismiss = { showProfileEditor = false },
            onSave = { studentName, permitNumber ->
                viewModel.updateStudentProfile(studentName, permitNumber)
                showProfileEditor = false
            },
        )
    }

    editingDrive?.let { drive ->
        EditDriveDialog(
            drive = drive,
            onDismiss = { editingDrive = null },
            onSave = { date, start, end, name, initials, comments ->
                viewModel.updateSession(
                    session = drive,
                    date = date,
                    startTime = start,
                    endTime = end,
                    supervisorName = name,
                    supervisorInitials = initials,
                    comments = comments,
                )
                editingDrive = null
            },
        )
    }
}

@Composable
fun CircularHoursCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    current: Float,
    goal: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = (current / goal).coerceIn(0f, 1f)

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(R.string.progress_fraction, current, goal),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RecentDrivesCard(
    drives: List<DriveSession>,
    onViewHistory: () -> Unit,
    onEditDrive: (DriveSession) -> Unit,
    onDeleteDrive: (DriveSession) -> Unit,
    onSeedEntries: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Recent Drives", style = MaterialTheme.typography.titleSmall)
                }
                TextButton(onClick = onViewHistory) {
                    Text(text = "See all", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (BuildConfig.DEBUG) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onSeedEntries) {
                        Text("Seed 100 entries")
                    }
                }
            }

            if (drives.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_no_drives),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                drives.forEach { drive ->
                    RecentDriveRow(
                        drive = drive,
                        onEdit = { onEditDrive(drive) },
                        onDelete = { onDeleteDrive(drive) },
                    )
                }
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun RecentDriveRow(
    drive: DriveSession,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = drive.date.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = "${drive.supervisorName}  ${drive.startTime.toLocalTime().format(timeFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val totalH = drive.totalMinutes / 60
            val totalM = drive.totalMinutes % 60
            Text(
                text = "%d:%02d".format(totalH, totalM),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (drive.nightMinutes > 0) {
                Text(
                    text = "%dm night".format(drive.nightMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit drive")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete drive")
                }
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    studentName: String,
    permitNumber: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var editedName by remember(studentName) { mutableStateOf(studentName) }
    var editedPermit by remember(permitNumber) { mutableStateOf(permitNumber) }
    val canSave = editedName.isNotBlank() && editedPermit.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Edit student profile", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Student name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editedPermit,
                    onValueChange = { editedPermit = it },
                    label = { Text("Permit number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onSave(editedName, editedPermit) }, enabled = canSave) {
                        Text("Save")
                    }
                }
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
                    ) {
                        Text("Save")
                    }
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
                Spacer(Modifier.height(12.dp))
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

@Preview(showBackground = true, name = "Dashboard - empty")
@Composable
private fun DashboardScreenPreviewEmpty() {
    CoDriveLogTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label = "Total Hours",
                    icon = Icons.Default.DirectionsCar,
                    current = 0f,
                    goal = 50f,
                )
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label = "Night Hours",
                    icon = Icons.Default.NightsStay,
                    current = 0f,
                    goal = 10f,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Dashboard - progress")
@Composable
private fun DashboardScreenPreviewProgress() {
    val sampleDrives = listOf(
        DriveSession(
            id = 1,
            date = LocalDate.now(),
            isManualEntry = false,
            startTime = LocalDateTime.now().minusHours(2),
            endTime = LocalDateTime.now(),
            totalMinutes = 120,
            nightMinutes = 30,
            supervisorName = "Jane Doe",
            supervisorInitials = "JD",
        ),
        DriveSession(
            id = 2,
            date = LocalDate.now().minusDays(1),
            isManualEntry = true,
            startTime = LocalDateTime.now().minusDays(1).minusHours(1),
            endTime = LocalDateTime.now().minusDays(1),
            totalMinutes = 60,
            nightMinutes = 0,
            supervisorName = "John Smith",
            supervisorInitials = "JS",
        ),
    )

    CoDriveLogTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label = "Total Hours",
                    icon = Icons.Default.DirectionsCar,
                    current = 23f,
                    goal = 50f,
                )
                CircularHoursCard(
                    modifier = Modifier.weight(1f),
                    label = "Night Hours",
                    icon = Icons.Default.NightsStay,
                    current = 4.5f,
                    goal = 10f,
                )
            }
            RecentDrivesCard(
                drives = sampleDrives,
                onViewHistory = {},
                onEditDrive = {},
                onDeleteDrive = {},
                onSeedEntries = {},
            )
        }
    }
}
