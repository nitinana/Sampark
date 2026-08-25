package com.sampark.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.performClick
import com.sampark.ui.theme.TranslateAmber
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `PrimaryButton shows its text and is clickable`() {
        var clicked = false
        composeRule.setContent {
            PrimaryButton(text = "सुरू करा", color = TranslateAmber, onClick = { clicked = true })
        }
        composeRule.onNodeWithText("सुरू करा").assertHasClickAction()
        composeRule.onNodeWithText("सुरू करा").performClick()
        assert(clicked)
    }

    @Test
    fun `SecondaryButton shows its text and is clickable`() {
        var clicked = false
        composeRule.setContent {
            SecondaryButton(text = "रद्द करा", onClick = { clicked = true })
        }
        composeRule.onNodeWithText("रद्द करा").assertHasClickAction()
        composeRule.onNodeWithText("रद्द करा").performClick()
        assert(clicked)
    }
}
