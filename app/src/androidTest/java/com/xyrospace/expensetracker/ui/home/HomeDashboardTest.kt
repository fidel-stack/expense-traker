package com.xyrospace.expensetracker.ui.home

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.xyrospace.expensetracker.ui.navigation.ExpenseTrackerApp
import com.xyrospace.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class HomeDashboardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun monthSelectorChangesSelectionAndDisablesNextAtCurrentMonth() {
        val currentMonth = YearMonth.now()
        val selectedMonth = mutableStateOf(currentMonth)
        composeRule.setContent {
            ExpenseTrackerTheme {
                HomeDashboardContent(
                    state = HomeDashboardUiState(selectedMonth = selectedMonth.value, isLoading = false),
                    onPreviousMonth = {
                        selectedMonth.value = adjacentMonth(selectedMonth.value, -1, currentMonth)
                    },
                    onNextMonth = {
                        selectedMonth.value = adjacentMonth(selectedMonth.value, 1, currentMonth)
                    },
                    onViewAll = {},
                    today = currentMonth,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        composeRule.onNodeWithText(currentMonth.displayName()).assertExists()

        composeRule.onNodeWithContentDescription("Previous month").assertIsEnabled().performClick()
        val previousMonth = adjacentMonth(currentMonth, -1, currentMonth)
        composeRule.onNodeWithText(previousMonth.displayName()).assertExists()

        composeRule.onNodeWithContentDescription("Next month").performClick()
        composeRule.onNodeWithText(currentMonth.displayName()).assertExists()
        composeRule.onNodeWithContentDescription("Next month").assertIsNotEnabled()
    }

    @Test
    fun viewAllNavigatesToExistingHistoryTab() {
        composeRule.setContent {
            ExpenseTrackerTheme { ExpenseTrackerApp() }
        }
        composeRule.onNodeWithTag("home_dashboard_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("View all"))
        composeRule.onNodeWithText("View all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_history").fetchSemanticsNode()
    }
}
