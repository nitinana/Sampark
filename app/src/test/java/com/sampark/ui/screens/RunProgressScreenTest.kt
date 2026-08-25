package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.data.status.Direction
import com.sampark.ui.RunUiState
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RunProgressScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `running state shows Pause and tapping it invokes onPause`() {
        var paused = false
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.TRANSLATE,
                    uiState = RunUiState(done = 32, total = 50, isPaused = false, lastProcessed = "Swarra Anande" to "स्वारा आनंदे"),
                    onPause = { paused = true },
                    onResume = {},
                    onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("थांबवा").performClick()
        assert(paused)
    }

    @Test
    fun `translate paused state shows both Resume and Cancel`() {
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.TRANSLATE,
                    uiState = RunUiState(done = 32, total = 50, isPaused = true, lastProcessed = null),
                    onPause = {}, onResume = {}, onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("सुरू ठेवा").assertExists()
        composeRule.onNodeWithText("रद्द करा").assertExists()
    }

    @Test
    fun `rollback paused state shows only Resume, no Cancel`() {
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.ROLLBACK,
                    uiState = RunUiState(done = 20, total = 50, isPaused = true, lastProcessed = null),
                    onPause = {}, onResume = {}, onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("सुरू ठेवा").assertExists()
        composeRule.onNodeWithText("रद्द करा").assertDoesNotExist()
    }

    @Test
    fun `resume button invokes onResume`() {
        var resumed = false
        composeRule.setContent {
            SamparkTheme {
                RunProgressScreen(
                    direction = Direction.TRANSLATE,
                    uiState = RunUiState(done = 32, total = 50, isPaused = true, lastProcessed = null),
                    onPause = {}, onResume = { resumed = true }, onCancel = {}
                )
            }
        }
        composeRule.onNodeWithText("सुरू ठेवा").performClick()
        assert(resumed)
    }
}
