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
class PermissionDeniedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Go to Settings invokes onGoToSettings`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { PermissionDeniedScreen(onGoToSettings = { tapped = true }, onRetry = {}) }
        }
        composeRule.onNodeWithText("सेटिंग्जमध्ये जा").performClick()
        assert(tapped)
    }

    @Test
    fun `retry link invokes onRetry`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { PermissionDeniedScreen(onGoToSettings = {}, onRetry = { tapped = true }) }
        }
        composeRule.onNodeWithText("पुन्हा विचारा").performClick()
        assert(tapped)
    }
}
