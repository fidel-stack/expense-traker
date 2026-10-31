package com.xyrospace.expensetracker.ui.transactions

import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionFormModelsTest {

    private val utc = ZoneId.of("UTC")
    private val categories = listOf(
        CategoryEntity(name = "Food", icon = "restaurant", color = "#C62828"),
        CategoryEntity(name = "Other", icon = "more_horiz", color = "#546E7A"),
        CategoryEntity(name = UNCATEGORIZED_CATEGORY, icon = "help_outline", color = "#616161"),
    )

    private fun parsedValue(input: String): Long =
        (parseKesAmount(input) as ParsedAmount.Valid).minorUnits

    @Test
    fun decimalAmountsConvertExactlyToMinorUnits() {
        assertEquals(25_000L, parsedValue("250"))
        assertEquals(25_050L, parsedValue("250.5"))
        assertEquals(25_050L, parsedValue("250.50"))
        assertEquals(150_075L, parsedValue("1500.75"))
        assertEquals(50L, parsedValue("0.50"))
        assertEquals(7L, parsedValue("0.07"))
        assertEquals(25_050L, parsedValue("  250.50  "))
    }

    @Test
    fun zeroNegativeAndInvalidAmountsAreRejected() {
        assertTrue(parseKesAmount("") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("   ") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("0") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("0.00") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("-50") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("abc") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("1.234") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount("1,000") is ParsedAmount.Invalid)
        assertTrue(parseKesAmount(".50") is ParsedAmount.Invalid)
        assertEquals("Amount must be greater than zero.", (parseKesAmount("0") as ParsedAmount.Invalid).message)
    }

    @Test
    fun amountsBeyondTheLongRangeAreRejectedInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, parsedValue("92233720368547758.07"))
        val overflow = parseKesAmount("92233720368547758.08")
        assertTrue(overflow is ParsedAmount.Invalid)
        assertEquals("Amount is too large.", (overflow as ParsedAmount.Invalid).message)
        assertTrue(parseKesAmount("999999999999999999999999") is ParsedAmount.Invalid)
    }

    @Test
    fun minorUnitsRoundTripToInputText() {
        assertEquals("250.50", minorUnitsToInput(25_050))
        assertEquals("0.07", minorUnitsToInput(7))
        assertEquals("92233720368547758.07", minorUnitsToInput(Long.MAX_VALUE))
    }

    @Test
    fun validationCanonicalizesCategoriesAndFallsBackToUncategorized() {
        val valid = TransactionFormState(
            amountInput = "250.50",
            category = "food",
            categories = categories,
        ).validate()
        assertEquals(25_050L, valid.amountMinor)
        assertEquals("Food", valid.categoryName)
        assertFalse(valid.errors.hasErrors)

        val fallback = TransactionFormState(
            amountInput = "10",
            category = "",
            categories = categories,
        ).validate()
        assertEquals(UNCATEGORIZED_CATEGORY, fallback.categoryName)
        assertNull(fallback.errors.category)

        val noCategories = TransactionFormState(amountInput = "10", categories = emptyList()).validate()
        assertEquals("Choose a category.", noCategories.errors.category)

        val badAmount = TransactionFormState(amountInput = "0", categories = categories).validate()
        assertNull(badAmount.amountMinor)
        assertEquals("Amount must be greater than zero.", badAmount.errors.amount)
    }

    @Test
    fun uncategorizedExpensesRequireReviewButOtherTypesDoNot() {
        assertTrue(computeNeedsReview(TransactionType.EXPENSE, UNCATEGORIZED_CATEGORY))
        assertTrue(computeNeedsReview(TransactionType.EXPENSE, "uncategorized"))
        assertFalse(computeNeedsReview(TransactionType.EXPENSE, "Food"))
        assertFalse(computeNeedsReview(TransactionType.INCOME, UNCATEGORIZED_CATEGORY))
        assertFalse(computeNeedsReview(TransactionType.TRANSFER, UNCATEGORIZED_CATEGORY))
    }

    @Test
    fun newManualTransactionsUseManualSourceAndNullReference() {
        val now = Instant.parse("2026-10-31T10:15:00Z").toEpochMilli()
        val state = TransactionFormState(
            amountInput = "35000",
            type = TransactionType.INCOME,
            category = "Other",
            recipient = "  Equity Bank  ",
            date = LocalDate.of(2026, 10, 31),
            note = "  October salary payment  ",
            categories = categories,
        )
        val entity = buildNewTransaction(state, 3_500_000L, "Other", now, utc)

        assertEquals(3_500_000L, entity.amount)
        assertEquals(TransactionType.INCOME, entity.type)
        assertEquals(TransactionSource.MANUAL, entity.source)
        assertNull(entity.transactionCode)
        assertEquals("Equity Bank", entity.recipient)
        assertEquals("October salary payment", entity.description)
        assertEquals(now, entity.date)
        assertFalse(entity.needsReview)
    }

    @Test
    fun backDatedManualEntriesUseLocalNoon() {
        val now = Instant.parse("2026-10-31T10:15:00Z").toEpochMilli()
        val state = TransactionFormState(
            amountInput = "100",
            category = "Food",
            date = LocalDate.of(2026, 10, 20),
            categories = categories,
        )
        val entity = buildNewTransaction(state, 10_000L, "Food", now, utc)
        assertEquals(Instant.parse("2026-10-20T12:00:00Z").toEpochMilli(), entity.date)
    }

    @Test
    fun editingPreservesIdentitySourceCodeAndUnchangedDate() {
        val originalDate = Instant.parse("2026-10-31T08:30:00Z").toEpochMilli()
        val existing = TransactionEntity(
            id = 42L,
            amount = 100_000,
            type = TransactionType.EXPENSE,
            category = "Food",
            recipient = "Naivas",
            date = originalDate,
            source = TransactionSource.MPESA,
            transactionCode = "QK12345",
            needsReview = false,
            description = "old note",
            createdTimestamp = 1_000L,
            legacyId = "legacy-42",
        )
        val state = TransactionFormState(
            transactionId = 42L,
            existing = existing,
            amountInput = "1300.25",
            type = TransactionType.EXPENSE,
            category = "Shopping",
            recipient = "Carrefour",
            date = LocalDate.of(2026, 10, 31),
            note = "corrected note",
            categories = categories,
        )

        val edited = buildEditedTransaction(
            existing = existing,
            state = state,
            amountMinor = 130_025L,
            categoryName = "Shopping",
            nowMillis = Instant.parse("2026-11-02T09:00:00Z").toEpochMilli(),
            zoneId = utc,
        )

        assertEquals(42L, edited.id)
        assertEquals(130_025L, edited.amount)
        assertEquals("Shopping", edited.category)
        assertEquals("Carrefour", edited.recipient)
        assertEquals("corrected note", edited.description)
        // Source metadata is never replaced by manual entry data.
        assertEquals(TransactionSource.MPESA, edited.source)
        assertEquals("QK12345", edited.transactionCode)
        assertEquals(1_000L, edited.createdTimestamp)
        assertEquals("legacy-42", edited.legacyId)
        // Same calendar date keeps the original timestamp (and its time of day).
        assertEquals(originalDate, edited.date)
    }

    @Test
    fun editingADifferentDateKeepsTheOriginalTimeZoneSemantics() {
        val originalDate = Instant.parse("2026-10-31T08:30:00Z").toEpochMilli()
        val existing = TransactionEntity(
            id = 7L,
            amount = 500,
            type = TransactionType.EXPENSE,
            category = "Food",
            date = originalDate,
            source = TransactionSource.MANUAL,
            createdTimestamp = 1_000L,
        )
        val edited = buildEditedTransaction(
            existing = existing,
            state = TransactionFormState(
                transactionId = 7L,
                existing = existing,
                category = "Food",
                date = LocalDate.of(2026, 10, 25),
            ),
            amountMinor = 500,
            categoryName = "Food",
            nowMillis = Instant.parse("2026-11-02T09:00:00Z").toEpochMilli(),
            zoneId = utc,
        )
        assertEquals(Instant.parse("2026-10-25T12:00:00Z").toEpochMilli(), edited.date)
    }

    @Test
    fun transfersCannotBeConvertedToIncomeOrExpenseWhenEdited() {
        val existing = TransactionEntity(
            id = 9L,
            amount = 1_000,
            type = TransactionType.TRANSFER,
            category = "Cash",
            date = 100L,
            source = TransactionSource.BANK,
            transactionCode = "TRX-9",
            createdTimestamp = 10L,
        )
        val state = TransactionFormState(
            transactionId = 9L,
            existing = existing,
            amountInput = "10",
            type = TransactionType.EXPENSE,
            category = "Cash",
            date = LocalDate.of(1970, 1, 1),
            categories = categories,
        )
        assertFalse(state.canChangeType)
        val edited = buildEditedTransaction(existing, state, 1_000L, "Cash", 10L, utc)
        assertEquals(TransactionType.TRANSFER, edited.type)
        assertEquals("TRX-9", edited.transactionCode)
        assertEquals(TransactionSource.BANK, edited.source)
        assertFalse(edited.needsReview)
    }
}
