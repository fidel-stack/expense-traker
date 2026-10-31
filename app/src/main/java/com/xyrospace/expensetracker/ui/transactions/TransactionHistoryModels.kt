package com.xyrospace.expensetracker.ui.transactions

import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.ui.home.formatKes
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class HistoryDateFilter(val label: String) {
    ALL("All dates"),
    TODAY("Today"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    CUSTOM("Custom range"),
}

/** Half-open epoch millisecond range: `startInclusive <= date < endExclusive`. */
data class EpochMillisRange(val startInclusive: Long, val endExclusive: Long)

data class TransactionHistoryFilters(
    val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val category: String? = null,
    val type: TransactionType? = null,
) {
    val hasActiveFilters: Boolean
        get() = dateFilter != HistoryDateFilter.ALL || category != null || type != null

    fun resolveDateRange(today: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): EpochMillisRange? {
        val (start, endInclusive) = when (dateFilter) {
            HistoryDateFilter.ALL -> return null
            HistoryDateFilter.TODAY -> today to today
            HistoryDateFilter.THIS_WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                monday to monday.plusDays(6)
            }
            HistoryDateFilter.THIS_MONTH -> today.withDayOfMonth(1) to
                today.withDayOfMonth(today.lengthOfMonth())
            HistoryDateFilter.CUSTOM -> {
                val start = customStart ?: return null
                val end = customEnd ?: return null
                if (start.isAfter(end)) return null
                start to end
            }
        }
        return EpochMillisRange(
            startInclusive = start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            // End is exclusive at the start of the following day so the whole end date is included.
            endExclusive = endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }

    fun dateFilterSummary(): String = when (dateFilter) {
        HistoryDateFilter.CUSTOM -> {
            val start = customStart
            val end = customEnd
            if (start != null && end != null && !start.isAfter(end)) {
                "${formatFilterDate(start)} – ${formatFilterDate(end)}"
            } else {
                HistoryDateFilter.CUSTOM.label
            }
        }
        else -> dateFilter.label
    }
}

fun normalizedSearchTerm(raw: String): String? = raw.trim().ifEmpty { null }

data class TransactionDateGroup(
    val date: LocalDate,
    val title: String,
    val transactions: List<TransactionEntity>,
)

private val headingFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val filterDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

fun formatFilterDate(date: LocalDate): String = date.format(filterDateFormatter)

fun formatDateHeading(date: LocalDate, today: LocalDate): String {
    val formatted = date.format(headingFormatter)
    return when (date) {
        today -> "Today — $formatted"
        today.minusDays(1) -> "Yesterday — $formatted"
        else -> formatted
    }
}

/**
 * Orders newest first and groups by calendar date in the device's local time zone.
 * Sorting is defensive so callers do not need to pre-sort database results.
 */
fun groupTransactionsByLocalDate(
    transactions: List<TransactionEntity>,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<TransactionDateGroup> = transactions
    .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
    .groupBy { Instant.ofEpochMilli(it.date).atZone(zoneId).toLocalDate() }
    .entries
    .sortedByDescending { it.key }
    .map { (date, items) ->
        TransactionDateGroup(
            date = date,
            title = formatDateHeading(date, today),
            transactions = items,
        )
    }

fun formatTransactionTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    timeFormatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))

/** Expressions keep their sign, income is marked `+`, transfers stay neutral. */
fun formatHistoryAmount(type: TransactionType, amountMinor: Long): String = when (type) {
    TransactionType.EXPENSE -> "−${formatKes(amountMinor)}"
    TransactionType.INCOME -> formatKes(amountMinor, showSign = true)
    TransactionType.TRANSFER -> formatKes(amountMinor)
}

fun transactionTypeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "Expense"
    TransactionType.INCOME -> "Income"
    TransactionType.TRANSFER -> "Transfer"
}

fun transactionSourceLabel(source: TransactionSource): String = when (source) {
    TransactionSource.MANUAL -> "Manual entry"
    TransactionSource.MPESA -> "M-Pesa"
    TransactionSource.BANK -> "Bank"
}
