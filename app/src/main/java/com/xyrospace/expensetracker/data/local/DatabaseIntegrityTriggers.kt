package com.xyrospace.expensetracker.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseIntegrityTriggers {
    fun install(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TRIGGER IF NOT EXISTS transactions_validate_insert
            BEFORE INSERT ON transactions
            BEGIN
                SELECT CASE WHEN NEW.amount < 0
                    THEN RAISE(ABORT, 'transaction amount must be non-negative') END;
                SELECT CASE WHEN NEW.type NOT IN ('expense', 'income', 'transfer')
                    THEN RAISE(ABORT, 'invalid transaction type') END;
                SELECT CASE WHEN NEW.source NOT IN ('mpesa', 'bank', 'manual')
                    THEN RAISE(ABORT, 'invalid transaction source') END;
            END""".trimIndent(),
        )
        database.execSQL(
            """CREATE TRIGGER IF NOT EXISTS transactions_validate_update
            BEFORE UPDATE OF amount, type, source ON transactions
            BEGIN
                SELECT CASE WHEN NEW.amount < 0
                    THEN RAISE(ABORT, 'transaction amount must be non-negative') END;
                SELECT CASE WHEN NEW.type NOT IN ('expense', 'income', 'transfer')
                    THEN RAISE(ABORT, 'invalid transaction type') END;
                SELECT CASE WHEN NEW.source NOT IN ('mpesa', 'bank', 'manual')
                    THEN RAISE(ABORT, 'invalid transaction source') END;
            END""".trimIndent(),
        )
    }
}
