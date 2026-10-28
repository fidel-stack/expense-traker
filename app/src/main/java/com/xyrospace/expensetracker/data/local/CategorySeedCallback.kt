package com.xyrospace.expensetracker.data.local

import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase

class CategorySeedCallback : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        DefaultCategories.seed(db)
        DatabaseIntegrityTriggers.install(db)
    }
}
