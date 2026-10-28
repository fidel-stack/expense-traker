package com.xyrospace.expensetracker.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.xyrospace.expensetracker.ui.theme.ExpenseTrackerTheme
import org.junit.Rule
import org.junit.Test

class ExpenseTrackerNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationOpensAllFiveScreens() {
        composeRule.setContent {
            ExpenseTrackerTheme {
                ExpenseTrackerApp()
            }
        }

        listOf("home", "history", "add", "reports", "settings").forEach { route ->
            composeRule.onNodeWithTag("tab_$route").fetchSemanticsNode()
            composeRule.onNodeWithTag("tab_$route").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("screen_$route").fetchSemanticsNode()
        }
    }
}
