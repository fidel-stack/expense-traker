package com.xyrospace.expensetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java,
        ).addCallback(CategorySeedCallback()).build()
        dao = database.transactionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun transactionCanBeInsertedObservedRetrievedAndDeleted() = runBlocking {
        val transaction = TransactionEntity(
            id = 1L,
            amount = 12345L,
            type = TransactionType.EXPENSE,
            category = "Transport",
            description = "Matatu fare",
            source = TransactionSource.MANUAL,
            date = 1_735_689_600_000L,
            transactionCode = "TEST-REF",
            createdTimestamp = 1_735_689_700_000L,
        )

        dao.insert(transaction)

        assertEquals(transaction, dao.getById(transaction.id))
        assertEquals(listOf(transaction), dao.observeAll().first())

        dao.deleteById(transaction.id)

        assertNull(dao.getById(transaction.id))
        assertEquals(emptyList<TransactionEntity>(), dao.observeAll().first())
    }
}
