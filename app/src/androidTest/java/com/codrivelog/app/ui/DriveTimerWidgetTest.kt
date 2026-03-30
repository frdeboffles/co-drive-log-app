package com.codrivelog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codrivelog.app.data.model.Supervisor
import com.codrivelog.app.ui.theme.CoDriveLogTheme
import com.codrivelog.app.ui.timer.DriveTimerUiState
import com.codrivelog.app.ui.timer.DriveTimerWidgetContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveTimerWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idle_startForm_withSupervisors_hidesManualEntryFields() {
        composeRule.setContent {
            CoDriveLogTheme {
                DriveTimerWidgetContent(
                    uiState = DriveTimerUiState.Idle,
                    supervisors = listOf(
                        Supervisor(id = 1, name = "Jane Doe", initials = "JD"),
                        Supervisor(id = 2, name = "John Smith", initials = "JS"),
                    ),
                    onStart = { _, _ -> },
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithText("Start Drive").performClick()
        composeRule.onNodeWithText("Supervisor").assertIsDisplayed()
        composeRule.onNodeWithText("Select Supervisor").assertIsDisplayed()
        composeRule.onNodeWithText("Full name").assertIsNotDisplayed()
        composeRule.onNodeWithText("Initials (e.g. JD)").assertIsNotDisplayed()
        composeRule.onNodeWithText("Enter manually...").assertIsNotDisplayed()
    }
}
