package com.xyrospace.expensetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xyrospace.expensetracker.data.model.TransactionEntity

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, RecipientRuleEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(TransactionConverters::class)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recipientRuleDao(): RecipientRuleDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT COLLATE NOCASE NOT NULL,
                        icon TEXT NOT NULL,
                        color TEXT NOT NULL
                    )""".trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)",
                )
                DefaultCategories.seed(database)
                // Preserve user-created category names already present on legacy transactions.
                database.execSQL(
                    """INSERT OR IGNORE INTO categories (name, icon, color)
                    SELECT DISTINCT TRIM(category), 'category', '#607D8B'
                    FROM transactions
                    WHERE TRIM(category) <> ''""".trimIndent(),
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount INTEGER NOT NULL CHECK(amount >= 0),
                        type TEXT NOT NULL CHECK(type IN ('expense', 'income', 'transfer')),
                        category TEXT NOT NULL,
                        recipient TEXT,
                        date INTEGER NOT NULL,
                        source TEXT NOT NULL CHECK(source IN ('mpesa', 'bank', 'manual')),
                        transaction_code TEXT,
                        needs_review INTEGER NOT NULL DEFAULT 0,
                        description TEXT NOT NULL,
                        created_timestamp INTEGER NOT NULL,
                        legacy_id TEXT,
                        FOREIGN KEY(category) REFERENCES categories(name)
                            ON UPDATE CASCADE ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                // Preserve every old row. If legacy references collide, retain the first code;
                // later rows keep their original ID as legacy_id and require review.
                database.execSQL(
                    """INSERT INTO transactions_new (
                        amount, type, category, recipient, date, source,
                        transaction_code, needs_review, description, created_timestamp, legacy_id
                    )
                    SELECT CASE WHEN old.amountMinor = -9223372036854775808
                            THEN 9223372036854775807 ELSE ABS(old.amountMinor) END,
                        CASE old.type
                            WHEN 'INCOME' THEN 'income'
                            WHEN 'TRANSFER' THEN 'transfer'
                            ELSE 'expense'
                        END,
                        CASE WHEN EXISTS (SELECT 1 FROM categories c WHERE c.name = TRIM(old.category) COLLATE NOCASE)
                            THEN (SELECT c.name FROM categories c WHERE c.name = TRIM(old.category) COLLATE NOCASE LIMIT 1)
                            ELSE 'Uncategorized' END,
                        NULL, old.transactionTimestamp,
                        CASE old.source WHEN 'MPESA' THEN 'mpesa' WHEN 'BANK' THEN 'bank' ELSE 'manual' END,
                        CASE WHEN old.reference IS NOT NULL AND NOT EXISTS (
                            SELECT 1 FROM transactions prior
                            WHERE prior.reference = old.reference AND prior.rowid < old.rowid
                        ) THEN old.reference ELSE NULL END,
                        CASE WHEN (old.reference IS NOT NULL AND EXISTS (
                            SELECT 1 FROM transactions prior
                            WHERE prior.reference = old.reference AND prior.rowid < old.rowid
                        )) OR old.amountMinor < 0 OR old.type NOT IN ('INCOME', 'EXPENSE', 'TRANSFER')
                            THEN 1 ELSE 0 END,
                        old.description, old.createdTimestamp, old.id
                    FROM transactions old""".trimIndent(),
                )
                database.execSQL("DROP TABLE transactions")
                database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                database.execSQL("CREATE INDEX index_transactions_date ON transactions(date)")
                database.execSQL("CREATE INDEX index_transactions_category ON transactions(category)")
                database.execSQL(
                    "CREATE UNIQUE INDEX index_transactions_transaction_code ON transactions(transaction_code)",
                )
                database.execSQL("CREATE UNIQUE INDEX index_transactions_legacy_id ON transactions(legacy_id)")
                database.execSQL(
                    """CREATE TRIGGER IF NOT EXISTS transactions_legacy_id_immutable
                    BEFORE UPDATE OF legacy_id ON transactions
                    WHEN OLD.legacy_id IS NOT NULL AND NEW.legacy_id IS NOT OLD.legacy_id
                    BEGIN
                        SELECT RAISE(ABORT, 'legacy transaction id is immutable');
                    END""".trimIndent(),
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS recipient_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recipient_name TEXT NOT NULL,
                        normalized_recipient TEXT NOT NULL,
                        category TEXT NOT NULL,
                        FOREIGN KEY(category) REFERENCES categories(name)
                            ON UPDATE CASCADE ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX index_recipient_rules_normalized_recipient ON recipient_rules(normalized_recipient)",
                )
                database.execSQL("CREATE INDEX index_recipient_rules_category ON recipient_rules(category)")
                DatabaseIntegrityTriggers.install(database)
            }
        }

        fun seedDefaultCategories(database: SupportSQLiteDatabase) {
            DefaultCategories.seed(database)
        }
    }
}
