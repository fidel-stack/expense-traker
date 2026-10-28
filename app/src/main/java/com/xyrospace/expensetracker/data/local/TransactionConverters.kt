package com.xyrospace.expensetracker.data.local

import androidx.room.TypeConverter
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType

class TransactionConverters {
    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.storageValue

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType =
        TransactionType.entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown transaction type: $value")

    @TypeConverter
    fun transactionSourceToString(value: TransactionSource): String = value.storageValue

    @TypeConverter
    fun stringToTransactionSource(value: String): TransactionSource =
        TransactionSource.entries.firstOrNull { it.storageValue == value }
            ?: error("Unknown transaction source: $value")
}
