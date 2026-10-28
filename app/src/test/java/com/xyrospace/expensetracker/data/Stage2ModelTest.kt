package com.xyrospace.expensetracker.data

import com.xyrospace.expensetracker.data.local.DefaultCategories
import com.xyrospace.expensetracker.data.local.RecipientNameNormalizer
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class Stage2ModelTest {
    @Test
    fun transactionRejectsNegativeAmountAndSupportsExplicitTransfer() {
        assertThrows(IllegalArgumentException::class.java) {
            transaction(amount = -1L)
        }
        assertEquals(TransactionType.TRANSFER, transaction(type = TransactionType.TRANSFER).type)
        assertEquals(123L, transaction(amount = 123L).amount)
    }

    @Test
    fun vocabularyHasOnlySupportedTransactionTypesAndSources() {
        assertEquals(setOf("expense", "income", "transfer"), TransactionType.entries.map { it.storageValue }.toSet())
        assertEquals(setOf("mpesa", "bank", "manual"), TransactionSource.entries.map { it.storageValue }.toSet())
    }

    @Test
    fun recipientNormalizationTrimsAndUsesLocaleIndependentLowercase() {
        assertEquals("naivas limited", RecipientNameNormalizer.normalize("  NAIVAS LIMITED \t"))
    }

    @Test
    fun defaultCategoriesContainExactlyTheRequestedThirteenWithValidMetadata() {
        assertEquals(13, DefaultCategories.all.size)
        assertEquals(
            setOf(
                "Food", "Transport", "Shopping", "Bills and utilities", "Airtime and data",
                "Health", "Entertainment", "Education", "Rent", "Cash", "Family and people",
                "Other", "Uncategorized",
            ),
            DefaultCategories.all.map { it.name }.toSet(),
        )
        assertTrue(DefaultCategories.all.all { it.icon.isNotBlank() && Regex("^#[0-9A-Fa-f]{6}$").matches(it.color) })
    }

    private fun transaction(amount: Long = 123L, type: TransactionType = TransactionType.EXPENSE) =
        TransactionEntity(
            amount = amount,
            type = type,
            category = "Food",
            date = 1L,
            source = TransactionSource.MANUAL,
            createdTimestamp = 1L,
        )
}
