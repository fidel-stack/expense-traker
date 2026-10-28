package com.xyrospace.expensetracker.ui.home

import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.local.CategoryExpenseRow
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardModelsTest {
    @Test
    fun monthSelectionMovesAcrossYearsAndNeverMovesBeyondCurrentMonth() {
        val december = YearMonth.of(2025, 12)
        assertEquals(YearMonth.of(2026, 1), adjacentMonth(december, 1, YearMonth.of(2026, 3)))
        assertEquals(YearMonth.of(2025, 11), adjacentMonth(december, -1, YearMonth.of(2025, 12)))
        assertEquals(YearMonth.of(2026, 3), adjacentMonth(YearMonth.of(2026, 2), 1, YearMonth.of(2026, 3)))
        assertEquals(YearMonth.of(2026, 3), adjacentMonth(YearMonth.of(2026, 3), 1, YearMonth.of(2026, 3)))
    }

    @Test
    fun calendarMonthRangesAreHalfOpenAndHandleLeapYearsAndYearTransitions() {
        val zone = ZoneId.of("UTC")
        val february = YearMonth.of(2024, 2).epochMillisRange(zone)
        assertEquals(1_706_745_600_000L, february.startInclusive)
        assertEquals(1_709_251_200_000L, february.endExclusive)

        val december = YearMonth.of(2025, 12).epochMillisRange(zone)
        assertEquals(YearMonth.of(2026, 1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(), december.endExclusive)
        assertTrue(december.startInclusive < december.endExclusive)
    }

    @Test
    fun netIncomeAndCurrencyFormattingPreserveSignsAndCents() {
        assertEquals(5_500L, HomeDashboardUiState(incomeMinor = 10_000, expensesMinor = 4_500).netIncomeMinor)
        assertEquals(-5_500L, HomeDashboardUiState(incomeMinor = 0, expensesMinor = 5_500).netIncomeMinor)
        assertEquals(0L, HomeDashboardUiState().netIncomeMinor)
        assertEquals("KES 123.45", formatKes(12_345L))
        assertEquals("−KES 123.45", formatKes(-12_345L))
        assertEquals("+KES 123.45", formatKes(12_345L, showSign = true))
    }

    @Test
    fun arithmeticSaturatesRatherThanWrapping() {
        assertEquals(Long.MAX_VALUE, safeAdd(Long.MAX_VALUE, 1L))
        assertEquals(Long.MIN_VALUE, safeSubtract(Long.MIN_VALUE, 1L))
        assertEquals(Long.MAX_VALUE, safeSubtract(Long.MAX_VALUE, -1L))
        assertEquals("−KES 92,233,720,368,547,758.08", formatKes(Long.MIN_VALUE))
    }

    @Test
    fun categoryPercentagesUseNearestBasisPointAndAreZeroSafe() {
        assertEquals(3_333, categoryPercentageBasisPoints(1L, 3L))
        assertEquals("33.33%", formatPercentage(categoryPercentageBasisPoints(1L, 3L)))
        assertEquals(1_667, categoryPercentageBasisPoints(1L, 6L))
        assertEquals(33, categoryPercentageBasisPoints(1L, 300L))
        assertEquals("0.33%", formatPercentage(categoryPercentageBasisPoints(1L, 300L)))
        assertEquals(0, categoryPercentageBasisPoints(10L, 0L))
        assertEquals(10_000, categoryPercentageBasisPoints(Long.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(10_000, categoryPercentageBasisPoints(Long.MAX_VALUE, 1L))
    }

    @Test
    fun categorySpendingSortsHighestFirstAndUsesExactRows() {
        val food = CategoryEntity(name = "Food", icon = "restaurant", color = "#C62828")
        val health = CategoryEntity(name = "Health", icon = "medical_services", color = "#2E7D32")
        val rows = buildCategorySpending(
            rows = listOf(CategoryExpenseRow("Health", 2_500), CategoryExpenseRow("Food", 7_500)),
            categories = listOf(food, health),
            totalExpenses = 10_000,
        )
        assertEquals(listOf("Food", "Health"), rows.map { it.category.name })
        assertEquals(listOf(7_500L, 2_500L), rows.map { it.amountMinor })
        assertEquals(listOf(7_500, 2_500), rows.map { it.percentageBasisPoints })
    }

    @Test
    fun emptyMonthIsRecognized() {
        assertTrue(HomeDashboardUiState(isLoading = false).isEmptyMonth)
        assertFalse(HomeDashboardUiState(incomeMinor = 1, isLoading = false).isEmptyMonth)
    }
}
