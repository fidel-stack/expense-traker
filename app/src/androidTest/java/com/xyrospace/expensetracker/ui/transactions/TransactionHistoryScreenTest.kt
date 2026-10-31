package com.xyrospace.expensetracker.ui.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.ui.theme.ExpenseTrackerTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("UTC")

    private fun transaction(id: Long, recipient: String, type: TransactionType, amount: Long): TransactionEntity =
        TransactionEntity(
            id = id,
            amount = amount,
            type = type,
            category = "Food",
            recipient = recipient,
            date = Instant.parse("2026-10-31T09:00:00Z").toEpochMilli(),
            source = TransactionSource.MANUAL,
            createdTimestamp = 1L,
        )

    private fun state(groups: List<TransactionDateGroup>) = HistoryUiState(
        groups = groups,
        resultCount = groups.sumOf { it.transactions.size },
        isLoading = false,
    )

    @Test
    fun groupsRenderWithHeadingsAndRowsAreClickable() {
        var clickedId: Long? = null
        val items = listOf(
            transaction(1, "Naivas", TransactionType.EXPENSE, 125_000),
            transaction(2, "Equity Bank", TransactionType.INCOME, 3_500_000),
        )
        val groups = groupTransactionsByLocalDate(items, LocalDate.of(2026, 10, 31), zone)

        composeRule.setContent {
            ExpenseTrackerTheme {
                TransactionHistoryContent(
                    state = state(groups),
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onDateFilterChange = {},
                    onCustomDateRangeChange = { _, _ -> },
                    onCategoryChange = {},
                    onTypeChange = {},
                    onResetFilters = {},
                    onRetry = {},
                    onTransactionClick = { clickedId = it },
                )
            }
        }

        composeRule.onNodeWithText("Today — 31 October 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Naivas").assertIsDisplayed()
        composeRule.onNodeWithText("+KES\u00A035,000.00").assertIsDisplayed()
        composeRule.onNodeWithTag("transaction_row_1").performClick()
        composeRule.waitForIdle()
        assertEquals(1L, clickedId)
    }

    @Test
    fun emptyStateAppearsWhenNoRecordsMatch() {
        composeRule.setContent {
            ExpenseTrackerTheme {
                TransactionHistoryContent(
                    state = HistoryUiState(
                        searchQuery = "unknown",
                        groups = emptyList(),
                        resultCount = 0,
                        isLoading = false,
                    ),
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onDateFilterChange = {},
                    onCustomDateRangeChange = { _, _ -> },
                    onCategoryChange = {},
                    onTypeChange = {},
                    onResetFilters = {},
                    onRetry = {},
                    onTransactionClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("No matching transactions").assertIsDisplayed()
    }

    @Test
    fun clearSearchButtonInvokesCallback() {
        var cleared = false
        composeRule.setContent {
            ExpenseTrackerTheme {
                TransactionHistoryContent(
                    state = HistoryUiState(
                        searchQuery = "Naivas",
                        groups = emptyList(),
                        isLoading = false,
                    ),
                    onSearchQueryChange = {},
                    onClearSearch = { cleared = true },
                    onDateFilterChange = {},
                    onCustomDateRangeChange = { _, _ -> },
                    onCategoryChange = {},
                    onTypeChange = {},
                    onResetFilters = {},
                    onRetry = {},
                    onTransactionClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_clear_search").performClick()
        composeRule.waitForIdle()
        assertTrue(cleared)
    }
}
