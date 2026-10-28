package com.xyrospace.expensetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.ui.home.epochMillisRange
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDashboardDaoTest {
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .addCallback(CategorySeedCallback())
            .build()
        dao = database.transactionDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun monthlyQueriesAggregateExcludeTransfersRespectBoundariesAndLimitRecent() = runBlocking {
        val range = YearMonth.of(2024, 2).epochMillisRange(ZoneId.of("UTC"))
        val rows = listOf(
            transaction(100L, TransactionType.EXPENSE, "Food", range.startInclusive),
            transaction(200L, TransactionType.EXPENSE, "Uncategorized", range.startInclusive + 1),
            transaction(300L, TransactionType.EXPENSE, "Uncategorized", range.startInclusive + 2, needsReview = true),
            transaction(400L, TransactionType.EXPENSE, "Food", range.startInclusive + 3, needsReview = true),
            transaction(500L, TransactionType.INCOME, "Uncategorized", range.startInclusive + 4, needsReview = true),
            transaction(600L, TransactionType.TRANSFER, "Food", range.startInclusive + 5, needsReview = true),
            transaction(700L, TransactionType.EXPENSE, "Food", range.endExclusive - 1),
            transaction(800L, TransactionType.EXPENSE, "Food", range.endExclusive),
        )
        rows.forEach { dao.insert(it) }

        val monthlyRows = dao.observeMonthlyAmountsByTypeAndCategory(
            range.startInclusive,
            range.endExclusive,
        ).first()
        assertEquals(500L, monthlyRows.filter { it.type == "income" }.sumOf { it.amount })
        assertEquals(1_700L, monthlyRows.filter { it.type == "expense" }.sumOf { it.amount })
        assertEquals(0L, monthlyRows.filter { it.type == "transfer" }.sumOf { it.amount })

        val recent = dao.observeRecentTransactions(range.startInclusive, range.endExclusive).first()
        assertEquals(5, recent.size)
        assertEquals(
            listOf(range.endExclusive - 1, range.startInclusive + 5, range.startInclusive + 4, range.startInclusive + 3, range.startInclusive + 2),
            recent.map { it.date },
        )

        assertEquals(
            3,
            dao.observeMonthlyUncategorizedReviewCount(range.startInclusive, range.endExclusive).first(),
        )
    }

    private fun transaction(
        amount: Long,
        type: TransactionType,
        category: String,
        date: Long,
        needsReview: Boolean = false,
    ) = TransactionEntity(
        amount = amount,
        type = type,
        category = category,
        date = date,
        source = TransactionSource.MANUAL,
        needsReview = needsReview,
        createdTimestamp = date,
    )
}
