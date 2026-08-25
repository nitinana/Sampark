package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping Start invokes onStart`() {
        var started = false
        composeRule.setContent {
            SamparkTheme { WelcomeScreen(onStart = { started = true }) }
        }
        composeRule.onNodeWithText("सुरू करा").performClick()
        assert(started)
    }

    @Test
    fun `shows the app name and explanation`() {
        composeRule.setContent {
            SamparkTheme { WelcomeScreen(onStart = {}) }
        }
        composeRule.onNodeWithText("मराठी संपर्क").assertExists()
    }
}
