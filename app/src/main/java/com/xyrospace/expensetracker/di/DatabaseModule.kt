package com.xyrospace.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.xyrospace.expensetracker.data.local.CategoryDao
import com.xyrospace.expensetracker.data.local.CategorySeedCallback
import com.xyrospace.expensetracker.data.local.ExpenseTrackerDatabase
import com.xyrospace.expensetracker.data.local.RecipientRuleDao
import com.xyrospace.expensetracker.data.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseTrackerDatabase =
        Room.databaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java,
            "expense-tracker.db",
        )
            .addMigrations(ExpenseTrackerDatabase.MIGRATION_1_2)
            .addCallback(CategorySeedCallback())
            .build()

    @Provides
    fun provideTransactionDao(database: ExpenseTrackerDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun provideCategoryDao(database: ExpenseTrackerDatabase): CategoryDao =
        database.categoryDao()

    @Provides
    fun provideRecipientRuleDao(database: ExpenseTrackerDatabase): RecipientRuleDao =
        database.recipientRuleDao()
}
