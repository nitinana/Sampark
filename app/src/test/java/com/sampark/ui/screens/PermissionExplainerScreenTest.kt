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
class PermissionExplainerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping Continue invokes onContinue`() {
        var continued = false
        composeRule.setContent {
            SamparkTheme { PermissionExplainerScreen(onContinue = { continued = true }) }
        }
        composeRule.onNodeWithText("पुढे जा").performClick()
        assert(continued)
    }
}
