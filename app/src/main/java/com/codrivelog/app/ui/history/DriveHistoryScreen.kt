package com.codrivelog.app.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Drive history screen showing all sessions with swipe-to-delete support.
 *
 * @param onBack    Invoked when the user taps the back button.
 * @param viewModel [DriveHistoryViewModel] provided by Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveHistoryScreen(
    onBack:    () -> Unit = {},
    viewModel: DriveHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            modifier  = Modifier.padding(padding),
            sessions  = uiState.sessions,
            onDelete  = viewModel::delete,
        )
    }
}

/** Stateless list content — easy to preview without Hilt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveHistoryContent(
    sessions: List<DriveSession>,
    onDelete: (DriveSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        Box(
            modifier          = modifier.fillMaxSize(),
            contentAlignment  = Alignment.Center,
        ) {
            Text(
                text  = stringResource(R.string.label_no_drives),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = sessions,
            key   = { it.id },
        ) { session ->
            SwipeToDismissSession(
                session  = session,
                onDelete = { onDelete(session) },
            )
        }
    }
}

// ---- Swipe-to-dismiss wrapper ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissSession(
    session:  DriveSession,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state            = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surface,
                label = "swipe_bg_color",
            )
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_drive),
                    tint               = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        DriveSessionCard(session = session)
    }
}

// ---- Session row card ----

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun DriveSessionCard(session: DriveSession, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = session.date.format(dateFormatter),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text     = "${session.startTime.format(timeFormatter)} – ${session.endTime.format(timeFormatter)}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val h = session.totalMinutes / 60
                    val m = session.totalMinutes % 60
                    Text(
                        text       = "%d:%02d hrs".format(h, m),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text     = session.supervisorName,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (session.nightMinutes > 0) {
                    SuggestionChip(
                        onClick = {},
                        label   = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector        = Icons.Default.NightsStay,
                                    contentDescription = null,
                                    modifier           = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text  = "${session.nightMinutes}m night",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        colors  = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
                if (session.isManualEntry) {
                    SuggestionChip(
                        onClick = {},
                        label   = {
                            Text(
                                text  = stringResource(R.string.label_manual_entry_badge),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            if (!session.comments.isNullOrBlank()) {
                Text(
                    text  = session.comments,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Previews ----

private val sampleSessions = listOf(
    DriveSession(
        id                  = 1,
        date                = LocalDate.now(),
        startTime           = LocalDateTime.now().minusHours(2),
        endTime             = LocalDateTime.now(),
        totalMinutes        = 120,
        nightMinutes        = 35,
        supervisorName      = "Jane Doe",
        supervisorInitials  = "JD",
        isManualEntry       = false,
        comments            = null,
    ),
    DriveSession(
        id                  = 2,
        date                = LocalDate.now().minusDays(1),
        startTime           = LocalDateTime.now().minusDays(1).minusMinutes(75),
        endTime             = LocalDateTime.now().minusDays(1),
        totalMinutes        = 75,
        nightMinutes        = 0,
        supervisorName      = "John Smith",
        supervisorInitials  = "JS",
        isManualEntry       = true,
        comments            = "Highway practice on I-25",
    ),
)

@Preview(showBackground = true, name = "DriveHistory – with sessions")
@Composable
private fun PreviewDriveHistoryList() {
    CoDriveLogTheme {
        DriveHistoryContent(sessions = sampleSessions, onDelete = {})
    }
}

@Preview(showBackground = true, name = "DriveHistory – empty")
@Composable
private fun PreviewDriveHistoryEmpty() {
    CoDriveLogTheme {
        DriveHistoryContent(sessions = emptyList(), onDelete = {})
    }
}

@Preview(showBackground = true, name = "DriveSessionCard – night + manual")
@Composable
private fun PreviewDriveSessionCard() {
    CoDriveLogTheme {
        DriveSessionCard(session = sampleSessions[1])
    }
}
