package com.xyrospace.expensetracker.ui.home

import com.xyrospace.expensetracker.data.local.CategoryExpenseRow
import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CENTS_PER_KES = 100L

fun adjacentMonth(month: YearMonth, deltaMonths: Long, currentMonth: YearMonth): YearMonth {
    val candidate = month.plusMonths(deltaMonths)
    return if (deltaMonths > 0) candidate.coerceAtMost(currentMonth) else candidate
}

fun YearMonth.epochMillisRange(zoneId: ZoneId = ZoneId.systemDefault()): LongRangeBoundary {
    val start = atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val end = plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return LongRangeBoundary(startInclusive = start, endExclusive = end)
}

data class LongRangeBoundary(val startInclusive: Long, val endExclusive: Long)

data class DashboardCategorySpending(
    val category: CategoryEntity,
    val amountMinor: Long,
    val percentageBasisPoints: Int,
)

data class HomeDashboardUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val incomeMinor: Long = 0,
    val expensesMinor: Long = 0,
    val categorySpending: List<DashboardCategorySpending> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val uncategorizedReviewCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val netIncomeMinor: Long
        get() = safeSubtract(incomeMinor, expensesMinor)

    val isEmptyMonth: Boolean
        get() = incomeMinor == 0L && expensesMinor == 0L && recentTransactions.isEmpty()
}

fun safeAdd(left: Long, right: Long): Long = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    if (right >= 0) Long.MAX_VALUE else Long.MIN_VALUE
}

fun safeSubtract(left: Long, right: Long): Long = try {
    Math.subtractExact(left, right)
} catch (_: ArithmeticException) {
    if (right < 0) Long.MAX_VALUE else Long.MIN_VALUE
}

fun categoryPercentageBasisPoints(amount: Long, total: Long): Int {
    if (amount <= 0L || total <= 0L) return 0
    val boundedAmount = amount.coerceAtMost(total)
    // Quotient/remainder prevents amount * 10,000 overflowing Long.
    val roundedBasisPoints = BigInteger.valueOf(boundedAmount)
        .multiply(BigInteger.valueOf(10_000L))
        .add(BigInteger.valueOf(total / 2L))
        .divide(BigInteger.valueOf(total))
    return roundedBasisPoints.min(BigInteger.valueOf(10_000L)).toInt()
}

fun formatKes(amountMinor: Long, showSign: Boolean = false): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "KE")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }
    val absolute = BigInteger.valueOf(amountMinor).abs()
    val formattedAmount = BigDecimal(absolute).movePointLeft(2)
    val formatted = "KES\u00A0${formatter.format(formattedAmount)}"
    return when {
        amountMinor < 0L -> "−$formatted"
        showSign && amountMinor > 0L -> "+$formatted"
        else -> formatted
    }
}

fun formatPercentage(basisPoints: Int): String =
    "${basisPoints / 100}.${(basisPoints % 100).toString().padStart(2, '0')}%"

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

fun YearMonth.displayName(): String = format(monthFormatter)

fun formatTransactionDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(epochMillis))

fun buildCategorySpending(
    rows: List<CategoryExpenseRow>,
    categories: List<CategoryEntity>,
    totalExpenses: Long,
): List<DashboardCategorySpending> {
    val categoriesByName = categories.associateBy { it.name.lowercase(Locale.ROOT) }
    return rows.mapNotNull { row ->
        val category = categoriesByName[row.category.lowercase(Locale.ROOT)] ?: return@mapNotNull null
        DashboardCategorySpending(
            category = category,
            amountMinor = row.amount,
            percentageBasisPoints = categoryPercentageBasisPoints(row.amount, totalExpenses),
        )
    }.sortedWith(compareByDescending<DashboardCategorySpending> { it.amountMinor }.thenBy { it.category.name })
}

