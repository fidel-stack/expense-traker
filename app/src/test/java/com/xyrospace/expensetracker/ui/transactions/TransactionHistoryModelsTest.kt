package com.xyrospace.expensetracker.ui.transactions

import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class TransactionHistoryModelsTest {

    companion object {
        private val UTC = ZoneId.of("UTC")

        @JvmStatic
        @BeforeClass
        fun useStableLocale() {
            Locale.setDefault(Locale.US)
        }

        private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
            LocalDate.parse(date).atTime(hour, minute).atZone(UTC).toInstant().toEpochMilli()

        private fun transaction(
            id: Long,
            date: Long,
            type: TransactionType = TransactionType.EXPENSE,
            amount: Long = 1_000,
            category: String = "Food",
        ) = TransactionEntity(
            id = id,
            amount = amount,
            type = type,
            category = category,
            recipient = null,
            date = date,
            source = TransactionSource.MANUAL,
            createdTimestamp = date,
        )
    }

    @Test
    fun groupsAreNewestFirstAndTitledRelativeToToday() {
        val transactions = listOf(
            transaction(1, at("2026-10-29")),
            transaction(2, at("2026-10-31", hour = 9)),
            transaction(3, at("2026-10-31", hour = 18)),
            transaction(4, at("2026-10-30")),
        )

        val groups = groupTransactionsByLocalDate(
            transactions = transactions,
            today = LocalDate.of(2026, 10, 31),
            zoneId = UTC,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 29)),
            groups.map { it.date },
        )
        assertEquals("Today — 31 October 2026", groups[0].title)
        assertEquals("Yesterday — 30 October 2026", groups[1].title)
        assertEquals("29 October 2026", groups[2].title)
        // Within a day, newest timestamp first — independent of database order.
        assertEquals(listOf(3L, 2L), groups[0].transactions.map { it.id })
    }

    @Test
    fun groupingUsesTheProvidedTimeZoneAcrossMidnight() {
        val justAfterMidnightUtc = Instant.parse("2026-10-31T00:30:00Z").toEpochMilli()
        val groups = groupTransactionsByLocalDate(
            transactions = listOf(transaction(1, justAfterMidnightUtc)),
            today = LocalDate.of(2026, 10, 30),
            zoneId = ZoneId.of("America/Los_Angeles"),
        )
        // 30 October 17:30 in Los Angeles.
        assertEquals(LocalDate.of(2026, 10, 30), groups.single().date)
    }

    @Test
    fun allDatesFilterProducesNoRange() {
        assertNull(TransactionHistoryFilters(dateFilter = HistoryDateFilter.ALL).resolveDateRange(LocalDate.of(2026, 10, 31), UTC))
    }

    @Test
    fun todayRangeIsHalfOpenAndIncludesTheWholeDay() {
        val range = TransactionHistoryFilters(HistoryDateFilter.TODAY)
            .resolveDateRange(LocalDate.of(2026, 10, 31), UTC)!!
        assertEquals(at("2026-10-31", hour = 0), range.startInclusive)
        assertEquals(at("2026-11-01", hour = 0), range.endExclusive)
    }

    @Test
    fun weekRangeStartsMondayAndEndsExclusiveAfterSunday() {
        val range = TransactionHistoryFilters(HistoryDateFilter.THIS_WEEK)
            .resolveDateRange(LocalDate.of(2026, 10, 31), UTC)!!
        assertEquals(at("2026-10-26", hour = 0), range.startInclusive)
        assertEquals(at("2026-11-02", hour = 0), range.endExclusive)
    }

    @Test
    fun monthAndCustomRangesHandleBoundariesAndValidation() {
        val month = TransactionHistoryFilters(HistoryDateFilter.THIS_MONTH)
            .resolveDateRange(LocalDate.of(2026, 2, 14), UTC)!!
        assertEquals(at("2026-02-01", hour = 0), month.startInclusive)
        assertEquals(at("2026-03-01", hour = 0), month.endExclusive)

        val custom = TransactionHistoryFilters(
            dateFilter = HistoryDateFilter.CUSTOM,
            customStart = LocalDate.of(2026, 10, 5),
            customEnd = LocalDate.of(2026, 10, 31),
        ).resolveDateRange(LocalDate.of(2026, 12, 1), UTC)!!
        assertEquals(at("2026-10-05", hour = 0), custom.startInclusive)
        // The end date is fully included.
        assertEquals(at("2026-11-01", hour = 0), custom.endExclusive)

        val reversed = TransactionHistoryFilters(
            dateFilter = HistoryDateFilter.CUSTOM,
            customStart = LocalDate.of(2026, 10, 31),
            customEnd = LocalDate.of(2026, 10, 1),
        )
        assertNull(reversed.resolveDateRange(LocalDate.of(2026, 10, 31), UTC))

        val incomplete = TransactionHistoryFilters(
            dateFilter = HistoryDateFilter.CUSTOM,
            customStart = LocalDate.of(2026, 10, 1),
        )
        assertNull(incomplete.resolveDateRange(LocalDate.of(2026, 10, 31), UTC))
    }

    @Test
    fun customSummaryAndActiveFiltersAreExposed() {
        val filters = TransactionHistoryFilters(
            dateFilter = HistoryDateFilter.CUSTOM,
            customStart = LocalDate.of(2026, 10, 1),
            customEnd = LocalDate.of(2026, 10, 31),
        )
        assertEquals("1 Oct 2026 – 31 Oct 2026", filters.dateFilterSummary())
        assertTrue(filters.hasActiveFilters)
        assertEquals("All dates", TransactionHistoryFilters().dateFilterSummary())
    }

    @Test
    fun searchTermIsTrimmedAndBlankBecomesNull() {
        assertNull(normalizedSearchTerm("   "))
        assertNull(normalizedSearchTerm(""))
        assertEquals("Naivas", normalizedSearchTerm("  Naivas  "))
    }

    @Test
    fun amountsUseTypeSpecificSigns() {
        assertEquals("−KES\u00A01,250.00", formatHistoryAmount(TransactionType.EXPENSE, 125_000))
        assertEquals("+KES\u00A035,000.00", formatHistoryAmount(TransactionType.INCOME, 3_500_000))
        assertEquals("KES\u00A0500.00", formatHistoryAmount(TransactionType.TRANSFER, 50_000))
    }

    @Test
    fun timesUseTheLocalTimeZone() {
        val timestamp = at("2026-10-31", hour = 14, minute = 5)
        assertEquals("14:05", formatTransactionTime(timestamp, UTC))
    }
}
