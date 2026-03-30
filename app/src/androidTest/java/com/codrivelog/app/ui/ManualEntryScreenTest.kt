package com.codrivelog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.fake.FakeDriveSessionDao
import com.codrivelog.app.data.fake.FakeSupervisorDao
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.data.repository.DriveSessionRepository
import com.codrivelog.app.data.repository.SupervisorRepository
import com.codrivelog.app.ui.entry.ManualEntryForm
import com.codrivelog.app.ui.entry.ManualEntryViewModel
import com.codrivelog.app.ui.entry.SaveState
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * Compose UI tests for [ManualEntryScreen] / [ManualEntryForm].
 *
 * Tests use the stateless [ManualEntryForm] composable directly to avoid
 * needing a Hilt component graph or a live database.
 */
@RunWith(AndroidJUnit4::class)
class ManualEntryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---- Form renders ----

    @Test
    fun form_rendersAllLabels() {
        setFormContent()
        composeTestRule.onNodeWithText("Date").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("End Time").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comments (optional)").assertIsDisplayed()
    }

    @Test
    fun form_saveDriveButton_isDisplayed() {
        setFormContent()
        composeTestRule.onNodeWithText("Save Drive").assertIsDisplayed()
    }

    @Test
    fun form_saveDriveButton_isEnabledInitially() {
        setFormContent()
        composeTestRule.onNodeWithText("Save Drive").assertIsEnabled()
    }

    // ---- Supervisor fields ----

    @Test
    fun form_noSupervisors_showsSupervisorTextField() {
        setFormContent(supervisors = emptyList())
        // When there are no saved supervisors the text field label appears
        composeTestRule.onNodeWithText("Full name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Initials (e.g. JD)").assertIsDisplayed()
    }

    @Test
    fun form_withSupervisors_showsDropdownHint() {
        setFormContent(
            supervisors = listOf(Supervisor(id = 1, name = "Jane Doe", initials = "JD"))
        )
        composeTestRule.onNodeWithText("Select Supervisor").assertIsDisplayed()
    }

    // ---- Validation error state ----

    @Test
    fun form_endTimeError_showsErrorMessage() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                ManualEntryForm(
                    supervisors = emptyList(),
                    saveState   = SaveState.ValidationError(
                        com.codrivelog.app.ui.entry.Field.END_TIME
                    ),
                    onSave = { _, _, _, _, _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithText("End time must be after start time").assertIsDisplayed()
    }

    @Test
    fun form_supervisorNameError_showsErrorMessage() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                ManualEntryForm(
                    supervisors = emptyList(),
                    saveState   = SaveState.ValidationError(
                        com.codrivelog.app.ui.entry.Field.SUPERVISOR_NAME
                    ),
                    onSave = { _, _, _, _, _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithText("Supervisor name is required").assertIsDisplayed()
    }

    // ---- Saving state ----

    @Test
    fun form_savingState_buttonShowsSavingText() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                ManualEntryForm(
                    supervisors = emptyList(),
                    saveState   = SaveState.Saving,
                    onSave      = { _, _, _, _, _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithText("Saving…").assertIsDisplayed()
    }

    @Test
    fun form_savingState_buttonIsDisabled() {
        composeTestRule.setContent {
            CoDriveLogTheme {
                ManualEntryForm(
                    supervisors = emptyList(),
                    saveState   = SaveState.Saving,
                    onSave      = { _, _, _, _, _, _ -> },
                )
            }
        }
        composeTestRule.onNodeWithText("Saving…").assertIsNotEnabled()
    }

    // ---- Save callback ----

    @Test
    fun form_tapSaveDrive_invokesOnSave() {
        var saved = false
        composeTestRule.setContent {
            CoDriveLogTheme {
                ManualEntryForm(
                    supervisors = emptyList(),
                    saveState   = SaveState.Idle,
                    onSave      = { _, _, _, _, _, _ -> saved = true },
                )
            }
        }
        composeTestRule.onNodeWithText("Save Drive").performClick()
        assert(saved) { "Expected onSave callback to be invoked on button click" }
    }

    // ---- Helpers ----

    private fun setFormContent(supervisors: List<Supervisor> = emptyList()) {
        composeTestRule.setContent {
            CoDriveLogTheme {
                ManualEntryForm(
                    supervisors = supervisors,
                    saveState   = SaveState.Idle,
                    onSave      = { _, _, _, _, _, _ -> },
                )
            }
        }
    }
}
