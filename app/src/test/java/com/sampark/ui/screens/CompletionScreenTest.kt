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
class CompletionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows the summary text and OK invokes onOk`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { CompletionScreen(summaryText = "५० संपर्कांची नावं मराठीत बदलली", onOk = { tapped = true }) }
        }
        composeRule.onNodeWithText("५० संपर्कांची नावं मराठीत बदलली").assertExists()
        composeRule.onNodeWithText("ठीक आहे").performClick()
        assert(tapped)
    }
}
