package com.sampark.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sampark.ui.theme.TranslateAmber
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CircularProgressRingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows the given percentage label`() {
        composeRule.setContent {
            CircularProgressRing(progressFraction = 0.64f, percentageLabel = "६४%", ringColor = TranslateAmber)
        }
        composeRule.onNodeWithText("६४%").assertExists()
    }
}
