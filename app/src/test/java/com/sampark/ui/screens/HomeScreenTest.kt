package com.sampark.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sampark.data.status.Direction
import com.sampark.ui.theme.SamparkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Marathi state shows rollback button and invokes callback`() {
        var tapped = false
        composeRule.setContent {
            SamparkTheme { HomeScreen(direction = Direction.TRANSLATE, onRollbackOrTranslateAgain = { tapped = true }) }
        }
        composeRule.onNodeWithText("इंग्रजी नावं परत आणा").performClick()
        assert(tapped)
    }

    @Test
    fun `post-rollback state shows translate-again button`() {
        composeRule.setContent {
            SamparkTheme { HomeScreen(direction = Direction.ROLLBACK, onRollbackOrTranslateAgain = {}) }
        }
        composeRule.onNodeWithText("पुन्हा मराठीत बदला").assertExists()
    }
}
