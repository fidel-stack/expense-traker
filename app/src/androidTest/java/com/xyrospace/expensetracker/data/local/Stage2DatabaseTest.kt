package com.xyrospace.expensetracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage2DatabaseTest {
    private lateinit var context: Context
    private lateinit var database: ExpenseTrackerDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var ruleDao: RecipientRuleDao

    @Before
    fun createDatabase() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ExpenseTrackerDatabase::class.java)
            .addCallback(CategorySeedCallback())
            .addMigrations(ExpenseTrackerDatabase.MIGRATION_1_2)
            .build()
        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
        ruleDao = database.recipientRuleDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun transactionCrudNullCodesUniqueCodesAndNewestFirstOrdering() = runBlocking {
        val older = transaction(code = "TX-OLDER", date = 100L, description = "original")
        val newer = transaction(code = "TX-NEWER", date = 200L)
        val noCodeOne = transaction(code = null, date = 150L)
        val noCodeTwo = transaction(code = null, date = 125L)

        val olderId = transactionDao.insert(older)
        val persistedOlder = older.copy(id = olderId)
        val newerId = transactionDao.insert(newer)
        val persistedNewer = newer.copy(id = newerId)
        val noCodeOneId = transactionDao.insert(noCodeOne)
        val persistedNoCodeOne = noCodeOne.copy(id = noCodeOneId)
        val noCodeTwoId = transactionDao.insert(noCodeTwo)
        val persistedNoCodeTwo = noCodeTwo.copy(id = noCodeTwoId)

        assertEquals(persistedOlder, transactionDao.getById(olderId))
        assertEquals(persistedNewer, transactionDao.getByCode("TX-NEWER"))
        assertEquals(
            listOf(persistedNewer, persistedNoCodeOne, persistedNoCodeTwo, persistedOlder),
            transactionDao.observeAll().first(),
        )

        val updated = persistedOlder.copy(description = "updated", type = TransactionType.TRANSFER)
        assertEquals(1, transactionDao.update(updated))
        assertEquals(updated, transactionDao.getById(olderId))
        assertThrows(Exception::class.java) {
            runBlocking { transactionDao.insert(transaction(code = "TX-NEWER")) }
        }

        assertEquals(1, transactionDao.deleteById(olderId))
        assertNull(transactionDao.getById(olderId))
    }

    @Test
    fun categoriesSeedExactlyOnceAndDoNotOverwriteExistingValues() = runBlocking {
        val first = categoryDao.getAll()
        assertEquals(13, first.size)
        assertEquals(DefaultCategories.all.map { it.name }.toSet(), first.map { it.name }.toSet())

        val food = categoryDao.getByName("food")!!
        categoryDao.update(food.copy(icon = "custom_food", color = "#123456"))
        ExpenseTrackerDatabase.seedDefaultCategories(database.openHelper.writableDatabase)

        assertEquals(13, categoryDao.getAll().size)
        assertEquals("custom_food", categoryDao.getByName("Food")!!.icon)
        assertEquals("#123456", categoryDao.getByName("Food")!!.color)
    }

    @Test
    fun recipientRulesNormalizeLookupAndRejectEquivalentDuplicateNames() = runBlocking {
        val id = ruleDao.insert(RecipientRuleEntity.create(recipientName = "  NAIVAS  ", category = "Food"))
        val rule = ruleDao.findByRecipientName("naivas")

        assertNotNull(rule)
        assertEquals(id, rule!!.id)
        assertEquals("naivas", rule.normalizedRecipient)
        assertThrows(Exception::class.java) {
            runBlocking {
                ruleDao.insert(RecipientRuleEntity.create(recipientName = "naivas", category = "Shopping"))
            }
        }

        val updated = rule.copy(category = "Shopping")
        assertEquals(1, ruleDao.update(updated))
        assertEquals("Shopping", ruleDao.findByRecipientName(" NAIVAS ")!!.category)
        assertEquals(1, ruleDao.deleteById(id))
        assertNull(ruleDao.findByRecipientName("naivas"))
    }

    @Test
    fun entityAndDatabaseRejectInvalidAmountsAndUnknownTransactionTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            transaction(amount = -1L)
        }
        assertThrows(Exception::class.java) {
            database.openHelper.writableDatabase.execSQL(
                """INSERT INTO transactions (amount, type, category, date, source, needs_review, description, created_timestamp)
                    VALUES (-1, 'expense', 'Food', 1, 'manual', 0, '', 1)""",
            )
        }
        assertThrows(Exception::class.java) {
            database.openHelper.writableDatabase.execSQL(
                """INSERT INTO transactions (amount, type, category, date, source, needs_review, description, created_timestamp)
                    VALUES (1, 'refund', 'Food', 1, 'manual', 0, '', 1)""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipientRuleEntity(
                recipientName = "NAIVAS",
                normalizedRecipient = "NAIVAS",
                category = "Food",
            )
        }
        assertEquals(TransactionType.TRANSFER, TransactionType.valueOf("TRANSFER"))
    }

    @Test
    fun migrationFromVersionOnePreservesRecordsAndMapsLegacyValues() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            ExpenseTrackerDatabase::class.java,
        )
        val oldDb = helper.createDatabase(MIGRATION_TEST_NAME, 1)
        oldDb.setForeignKeyConstraintsEnabled(true)
        oldDb.execSQL(
            """INSERT INTO transactions (id, reference, amountMinor, type, category, description, source,
                transactionTimestamp, createdTimestamp) VALUES
                ('legacy-1', 'OLD-REF', 12345, 'EXPENSE', 'Food', 'old purchase', 'MPESA', 1000, 1500)""",
        )
        oldDb.execSQL(
            """INSERT INTO transactions (id, reference, amountMinor, type, category, description, source,
                transactionTimestamp, createdTimestamp) VALUES
                ('legacy-2', NULL, 2500, 'INCOME', 'Old custom category', 'old income', 'MANUAL', 2000, 2500)""",
        )
        oldDb.close()

        val migrated = helper.runMigrationsAndValidate(
            MIGRATION_TEST_NAME,
            2,
            true,
            ExpenseTrackerDatabase.MIGRATION_1_2,
        )
        val cursor = migrated.query(
            "SELECT amount, type, category, source, transaction_code, needs_review, description, created_timestamp, legacy_id " +
                "FROM transactions ORDER BY date ASC",
        )
        try {
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(12345L, cursor.getLong(0))
            assertEquals("expense", cursor.getString(1))
            assertEquals("Food", cursor.getString(2))
            assertEquals("mpesa", cursor.getString(3))
            assertEquals("OLD-REF", cursor.getString(4))
            assertEquals("old purchase", cursor.getString(6))
            assertEquals(1500L, cursor.getLong(7))
            assertEquals("legacy-1", cursor.getString(8))

            cursor.moveToNext()
            assertEquals("income", cursor.getString(1))
            assertEquals("Old custom category", cursor.getString(2))
            assertEquals("manual", cursor.getString(3))
            assertNull(cursor.getString(4))
            assertEquals("old income", cursor.getString(6))
            assertEquals(2500L, cursor.getLong(7))
            assertEquals("legacy-2", cursor.getString(8))
        } finally {
            cursor.close()
            migrated.close()
        }
    }

    private fun transaction(
        code: String? = null,
        date: Long = 1_000L,
        amount: Long = 12345L,
        description: String = "test transaction",
        type: TransactionType = TransactionType.EXPENSE,
    ) = TransactionEntity(
        amount = amount,
        type = type,
        category = "Food",
        recipient = "Test merchant",
        date = date,
        source = TransactionSource.MANUAL,
        transactionCode = code,
        description = description,
        createdTimestamp = 2_000L,
    )

    companion object {
        private const val MIGRATION_TEST_NAME = "stage2-migration"
    }
}
