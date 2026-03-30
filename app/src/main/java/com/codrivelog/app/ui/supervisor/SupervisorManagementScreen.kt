package com.codrivelog.app.ui.supervisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.R
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.ui.theme.CoDriveLogTheme

/**
 * Supervisor management screen.
 *
 * Lists all saved supervisors and provides an inline form to add new ones.
 * Each row has a delete icon button.
 *
 * @param onBack    Invoked when the user taps the back button.
 * @param viewModel [SupervisorViewModel] provided by Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorManagementScreen(
    onBack:    () -> Unit = {},
    viewModel: SupervisorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_supervisors)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        SupervisorManagementContent(
            modifier    = Modifier.padding(padding),
            supervisors = uiState.supervisors,
            onAdd       = { name, initials -> viewModel.add(name, initials) },
            onDelete    = viewModel::delete,
        )
    }
}

/** Stateless content — easy to preview without Hilt. */
@Composable
fun SupervisorManagementContent(
    supervisors: List<Supervisor>,
    onAdd:       (name: String, initials: String) -> Boolean,
    onDelete:    (Supervisor) -> Unit,
    modifier:    Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // ---- Inline add form ----
        AddSupervisorRow(onAdd = onAdd)

        // ---- List ----
        if (supervisors.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.label_no_supervisors),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = supervisors,
                    key   = { it.id },
                ) { supervisor ->
                    SupervisorRow(supervisor = supervisor, onDelete = { onDelete(supervisor) })
                }
            }
        }
    }
}

// ---- Add row ----

@Composable
private fun AddSupervisorRow(onAdd: (name: String, initials: String) -> Boolean) {
    var name     by remember { mutableStateOf("") }
    var initials by remember { mutableStateOf("") }
    var nameErr  by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text  = "Add Supervisor",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it; nameErr = false },
                label         = { Text(stringResource(R.string.hint_supervisor_name)) },
                isError       = nameErr,
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value         = initials,
                onValueChange = { initials = it.uppercase().take(4) },
                label         = { Text(stringResource(R.string.hint_supervisor_initials)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.Button(
                onClick = {
                    val added = onAdd(name.trim(), initials.trim())
                    if (added) {
                        name     = ""
                        initials = ""
                        nameErr  = false
                    } else {
                        nameErr = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.button_add_supervisor))
            }
        }
    }
}

// ---- Supervisor row ----

@Composable
private fun SupervisorRow(supervisor: Supervisor, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = supervisor.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text  = supervisor.initials,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_supervisor),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true, name = "SupervisorManagement – empty")
@Composable
private fun PreviewSupervisorEmpty() {
    CoDriveLogTheme {
        SupervisorManagementContent(
            supervisors = emptyList(),
            onAdd       = { _, _ -> true },
            onDelete    = {},
        )
    }
}

@Preview(showBackground = true, name = "SupervisorManagement – with list")
@Composable
private fun PreviewSupervisorList() {
    CoDriveLogTheme {
        SupervisorManagementContent(
            supervisors = listOf(
                Supervisor(id = 1, name = "Jane Doe",     initials = "JD"),
                Supervisor(id = 2, name = "John Smith",   initials = "JS"),
                Supervisor(id = 3, name = "Alice Parent", initials = "AP"),
            ),
            onAdd    = { _, _ -> true },
            onDelete = {},
        )
    }
}
