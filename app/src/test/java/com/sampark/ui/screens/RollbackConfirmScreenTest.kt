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
class RollbackConfirmScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Yes invokes onConfirm`() {
        var confirmed = false
        composeRule.setContent {
            SamparkTheme { RollbackConfirmScreen(onConfirm = { confirmed = true }, onCancel = {}) }
        }
        composeRule.onNodeWithText("हो, परत आणा").performClick()
        assert(confirmed)
    }

    @Test
    fun `No invokes onCancel`() {
        var cancelled = false
        composeRule.setContent {
            SamparkTheme { RollbackConfirmScreen(onConfirm = {}, onCancel = { cancelled = true }) }
        }
        composeRule.onNodeWithText("नको").performClick()
        assert(cancelled)
    }
}
