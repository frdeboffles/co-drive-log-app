package com.codrivelog.app.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codrivelog.app.ui.theme.CoDriveLogTheme

/**
 * Full-screen onboarding flow shown on first launch.
 *
 * **Page 0** — Captures the student's name and permit number.
 * **Page 1** — Captures the first supervisor's name and initials.
 *
 * When the user completes page 1 the ViewModel persists both the student name
 * (DataStore) and the supervisor (Room) then emits [OnboardingViewModel.onboardingComplete]
 * which triggers [onComplete].
 *
 * @param onComplete Called once the user finishes onboarding.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel:  OnboardingViewModel = hiltViewModel(),
) {
    val page     by viewModel.page.collectAsStateWithLifecycle()
    val complete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val error    by viewModel.error.collectAsStateWithLifecycle()

    // Student name kept in saveable state so it survives page transitions
    var studentName        by rememberSaveable { mutableStateOf("") }
    var permitNumber       by rememberSaveable { mutableStateOf("") }
    var supervisorName     by rememberSaveable { mutableStateOf("") }
    var supervisorInitials by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(complete) {
        if (complete) onComplete()
    }

    Scaffold { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "onboarding_page",
        ) { currentPage ->
            when (currentPage) {
                0 -> NamePage(
                    studentName  = studentName,
                    permitNumber = permitNumber,
                    onNameChange = { studentName = it; viewModel.clearError() },
                    onPermitChange = { permitNumber = it; viewModel.clearError() },
                    error        = error,
                    onNext       = { viewModel.nextPage(studentName, permitNumber) },
                    modifier     = Modifier.padding(padding),
                )
                else -> SupervisorPage(
                    supervisorName        = supervisorName,
                    supervisorInitials    = supervisorInitials,
                    onNameChange          = { supervisorName = it; viewModel.clearError() },
                    onInitialsChange      = { supervisorInitials = it; viewModel.clearError() },
                    error                 = error,
                    onBack                = { viewModel.previousPage() },
                    onFinish              = {
                        viewModel.finish(
                            studentName        = studentName,
                            permitNumber       = permitNumber,
                            supervisorName     = supervisorName,
                            supervisorInitials = supervisorInitials,
                        )
                    },
                    modifier              = Modifier.padding(padding),
                )
            }
        }
    }
}

// ── Page 0: Student Name ──────────────────────────────────────────────────────

@Composable
private fun NamePage(
    studentName:  String,
    permitNumber: String,
    onNameChange: (String) -> Unit,
    onPermitChange: (String) -> Unit,
    error:        String?,
    onNext:       () -> Unit,
    modifier:     Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.DirectionsCar,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text  = "Welcome to CO Drive Log",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Let's set up your driving log. First, what's the student's name?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value         = studentName,
            onValueChange = onNameChange,
            label         = { Text("Student's full name") },
            singleLine    = true,
            isError       = error != null && studentName.isBlank(),
            supportingText = if (error != null && studentName.isBlank()) {
                { Text(error) }
            } else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier      = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value          = permitNumber,
            onValueChange  = onPermitChange,
            label          = { Text("Permit number") },
            singleLine     = true,
            isError        = error != null && permitNumber.isBlank(),
            supportingText = if (error != null && permitNumber.isBlank()) {
                { Text(error) }
            } else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction      = ImeAction.Next,
            ),
            modifier       = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onNext,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Next")
        }
    }
}

// ── Page 1: First Supervisor ──────────────────────────────────────────────────

@Composable
private fun SupervisorPage(
    supervisorName:     String,
    supervisorInitials: String,
    onNameChange:       (String) -> Unit,
    onInitialsChange:   (String) -> Unit,
    error:              String?,
    onBack:             () -> Unit,
    onFinish:           () -> Unit,
    modifier:           Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Default.Person,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text  = "Add your first supervisor",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Colorado requires a licensed adult to supervise all practice drives. " +
                    "You can add more supervisors later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value         = supervisorName,
            onValueChange = onNameChange,
            label         = { Text("Supervisor's full name") },
            singleLine    = true,
            isError       = error != null && supervisorName.isBlank(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = ImeAction.Next,
            ),
            modifier      = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = supervisorInitials,
            onValueChange = { onInitialsChange(it.uppercase().take(4)) },
            label         = { Text("Initials (e.g. JD)") },
            singleLine    = true,
            isError       = error != null && supervisorInitials.isBlank(),
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction      = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onFinish() }),
            modifier      = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.weight(1f),
            ) { Text("Back") }
            Button(
                onClick  = onFinish,
                modifier = Modifier.weight(1f),
            ) { Text("Get started") }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Onboarding – Name page")
@Composable
private fun PreviewNamePage() {
    CoDriveLogTheme {
        NamePage(
            studentName  = "",
            permitNumber = "",
            onNameChange = {},
            onPermitChange = {},
            error        = null,
            onNext       = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding – Supervisor page")
@Composable
private fun PreviewSupervisorPage() {
    CoDriveLogTheme {
        SupervisorPage(
            supervisorName     = "",
            supervisorInitials = "",
            onNameChange       = {},
            onInitialsChange   = {},
            error              = null,
            onBack             = {},
            onFinish           = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding – Name error")
@Composable
private fun PreviewNamePageError() {
    CoDriveLogTheme {
        NamePage(
            studentName  = "",
            permitNumber = "",
            onNameChange = {},
            onPermitChange = {},
            error        = "Please enter the student's name.",
            onNext       = {},
        )
    }
}
