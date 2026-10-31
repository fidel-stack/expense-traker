package com.xyrospace.expensetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.data.repository.TransactionRepository
import com.xyrospace.expensetracker.ui.home.epochMillisRange
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionHistoryDaoTest {
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var dao: TransactionDao
    private lateinit var repository: TransactionRepository

    private val zone = ZoneId.of("UTC")

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .addCallback(CategorySeedCallback())
            .build()
        dao = database.transactionDao()
        repository = TransactionRepository(dao)
    }

    @After
    fun closeDatabase() = database.close()

    private suspend fun seed() {
        dao.insert(
            TransactionEntity(
                amount = 125_000,
                type = TransactionType.EXPENSE,
                category = "Food",
                recipient = "Naivas",
                date = at("2026-10-31", 10),
                source = TransactionSource.MANUAL,
                transactionCode = "QK12345",
                description = "Weekly groceries",
                createdTimestamp = 1L,
            ),
        )
        dao.insert(
            TransactionEntity(
                amount = 45_000,
                type = TransactionType.EXPENSE,
                category = "Transport",
                recipient = "Uber",
                date = at("2026-10-31", 9),
                source = TransactionSource.MANUAL,
                description = "Ride to town",
                createdTimestamp = 2L,
            ),
        )
        dao.insert(
            TransactionEntity(
                amount = 3_500_000,
                type = TransactionType.INCOME,
                category = "Other",
                recipient = "Equity Bank",
                date = at("2026-10-31", 8),
                source = TransactionSource.MANUAL,
                description = "October salary payment",
                createdTimestamp = 3L,
            ),
        )
        dao.insert(
            TransactionEntity(
                amount = 250_000,
                type = TransactionType.EXPENSE,
                category = "Shopping",
                recipient = "Carrefour",
                date = at("2026-10-30", 15),
                source = TransactionSource.MANUAL,
                description = "Household items",
                createdTimestamp = 4L,
            ),
        )
        dao.insert(
            TransactionEntity(
                amount = 20_000,
                type = TransactionType.EXPENSE,
                category = "Uncategorized",
                recipient = "Safaricom",
                date = at("2026-10-30", 12),
                source = TransactionSource.MPESA,
                transactionCode = "SF9988",
                needsReview = true,
                description = "Airtime",
                createdTimestamp = 5L,
            ),
        )
        dao.insert(
            TransactionEntity(
                amount = 50_000,
                type = TransactionType.TRANSFER,
                category = "Cash",
                recipient = "Savings",
                date = at("2026-10-29", 11),
                source = TransactionSource.BANK,
                transactionCode = "TRX7788",
                description = "Move to savings",
                createdTimestamp = 6L,
            ),
        )
        dao.insert(
            TransactionEntity(
                amount = 99_000,
                type = TransactionType.EXPENSE,
                category = "Food",
                recipient = "Naivas",
                date = at("2026-09-15", 10),
                source = TransactionSource.MANUAL,
                description = "Previous month groceries",
                createdTimestamp = 7L,
            ),
        )
    }

    @Test
    fun historyIsOrderedNewestFirstBeforeGrouping() = runBlocking {
        seed()
        val all = dao.observeFiltered(null, null, null, null, null).first()
        assertEquals(7, all.size)
        assertEquals(
            all.map { it.date }.sortedDescending(),
            all.map { it.date },
        )
    }

    @Test
    fun searchMatchesRecipientDescriptionCategoryAndCodeCaseInsensitively() = runBlocking {
        seed()

        val byRecipient = dao.observeFiltered(null, null, null, null, "nAiVaS").first()
        assertEquals(2, byRecipient.size)
        assertTrue(byRecipient.all { it.recipient == "Naivas" })

        val byDescription = dao.observeFiltered(null, null, null, null, "SALARY").first()
        assertEquals(listOf("Equity Bank"), byDescription.map { it.recipient })

        val byCategory = dao.observeFiltered(null, null, null, null, "transport").first()
        assertEquals(listOf("Uber"), byCategory.map { it.recipient })

        val byCode = dao.observeFiltered(null, null, null, null, "qk12").first()
        assertEquals(listOf("QK12345"), byCode.map { it.transactionCode })

        val noMatch = dao.observeFiltered(null, null, null, null, "nothing-matches").first()
        assertEquals(emptyList<TransactionEntity>(), noMatch)
    }

    @Test
    fun categoryAndTypeFiltersWorkIncludingTransfers() = runBlocking {
        seed()

        val food = dao.observeFiltered(null, null, "Food", null, null).first()
        assertEquals(2, food.size)
        assertTrue(food.all { it.category == "Food" })

        val uncategorized = dao.observeFiltered(null, null, "uncategorized", null, null).first()
        assertEquals(listOf("Safaricom"), uncategorized.map { it.recipient })

        val transfers = dao.observeFiltered(null, null, null, "transfer", null).first()
        assertEquals(1, transfers.size)
        assertEquals(TransactionType.TRANSFER, transfers.single().type)

        val income = dao.observeFiltered(null, null, null, "income", null).first()
        assertEquals(listOf("Equity Bank"), income.map { it.recipient })
    }

    @Test
    fun dateRangeUsesHalfOpenBoundsCoveringTheEndDate() = runBlocking {
        seed()
        val start = at("2026-10-30", 0)
        val endExclusive = at("2026-11-01", 0)

        val within = dao.observeFiltered(start, endExclusive, null, null, null).first()
        assertEquals(5, within.size)
        assertTrue(within.all { it.date >= start && it.date < endExclusive })

        val endBoundary = dao.observeFiltered(endExclusive, null, null, null, null).first()
        assertEquals(emptyList<TransactionEntity>(), endBoundary)
    }

    @Test
    fun combinedFiltersReturnOnlyMatchingRecords() = runBlocking {
        seed()
        val october = YearMonth.of(2026, 10).epochMillisRange(zone)

        val results = dao.observeFiltered(
            startInclusive = october.startInclusive,
            endExclusive = october.endExclusive,
            category = "Food",
            type = "expense",
            search = "naiv",
        ).first()

        assertEquals(1, results.size)
        assertEquals("Naivas", results.single().recipient)
        assertEquals(125_000L, results.single().amount)
    }

    @Test
    fun editingUpdatesInPlacePreservingIdSourceAndCode() = runBlocking {
        seed()
        val original = dao.getByCode("SF9988")!!
        val countBefore = dao.getAll().size

        val updated = original.copy(
            amount = 21_000,
            category = "Airtime and data",
            needsReview = false,
            description = "Corrected airtime",
        )
        assertEquals(1, dao.update(updated))

        val persisted = dao.getById(original.id)!!
        assertEquals(original.id, persisted.id)
        assertEquals(21_000L, persisted.amount)
        assertEquals("Airtime and data", persisted.category)
        assertEquals(TransactionSource.MPESA, persisted.source)
        assertEquals("SF9988", persisted.transactionCode)
        assertEquals(countBefore, dao.getAll().size)
        assertEquals(persisted, dao.observeById(original.id).first())
    }

    @Test
    fun deletingRemovesOnlyTheSelectedTransaction() = runBlocking {
        seed()
        val target = dao.getByCode("QK12345")!!
        val total = dao.getAll().size

        assertEquals(1, repository.delete(target.id))

        assertNull(dao.getById(target.id))
        assertEquals(total - 1, dao.getAll().size)
        assertTrue(dao.getAll().none { it.transactionCode == "QK12345" })
    }

    @Test
    fun manualInsertUpdatesHomeDashboardQueriesReactively() = runBlocking {
        val october = YearMonth.of(2026, 10).epochMillisRange(zone)

        assertEquals(
            0L,
            dao.observeMonthlyAmountsByTypeAndCategory(october.startInclusive, october.endExclusive)
                .first().sumOf { it.amount },
        )
        assertEquals(0, dao.observeRecentTransactions(october.startInclusive, october.endExclusive).first().size)
        assertEquals(
            0,
            dao.observeMonthlyUncategorizedReviewCount(october.startInclusive, october.endExclusive).first(),
        )

        val id = repository.insert(
            TransactionEntity(
                amount = 3_500_000,
                type = TransactionType.INCOME,
                category = "Other",
                recipient = "Equity Bank",
                date = at("2026-10-31", 9),
                source = TransactionSource.MANUAL,
                description = "October salary payment",
                createdTimestamp = at("2026-10-31", 9),
            ),
        )
        repository.insert(
            TransactionEntity(
                amount = 12_500,
                type = TransactionType.EXPENSE,
                category = "Uncategorized",
                recipient = "Duka",
                date = at("2026-10-31", 11),
                source = TransactionSource.MANUAL,
                needsReview = true,
                createdTimestamp = at("2026-10-31", 11),
            ),
        )

        val monthly = dao.observeMonthlyAmountsByTypeAndCategory(
            october.startInclusive,
            october.endExclusive,
        ).first()
        assertEquals(3_500_000L, monthly.filter { it.type == "income" }.sumOf { it.amount })
        assertEquals(12_500L, monthly.filter { it.type == "expense" }.sumOf { it.amount })

        assertEquals(
            2,
            dao.observeRecentTransactions(october.startInclusive, october.endExclusive).first().size,
        )
        assertEquals(
            1,
            dao.observeMonthlyUncategorizedReviewCount(october.startInclusive, october.endExclusive).first(),
        )

        repository.update(dao.getById(id)!!.copy(amount = 4_000_000))
        assertEquals(
            4_000_000L,
            dao.observeMonthlyAmountsByTypeAndCategory(october.startInclusive, october.endExclusive)
                .first().filter { it.type == "income" }.sumOf { it.amount },
        )

        repository.delete(id)
        assertEquals(
            0L,
            dao.observeMonthlyAmountsByTypeAndCategory(october.startInclusive, october.endExclusive)
                .first().filter { it.type == "income" }.sumOf { it.amount },
        )
    }

    private fun at(date: String, hour: Int): Long =
        java.time.LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
}
