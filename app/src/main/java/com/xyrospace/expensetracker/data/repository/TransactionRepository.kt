package com.xyrospace.expensetracker.data.repository

import com.xyrospace.expensetracker.data.local.TransactionDao
import com.xyrospace.expensetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {
    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    suspend fun getAll(): List<TransactionEntity> = transactionDao.getAll()

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun getByCode(code: String): TransactionEntity? = transactionDao.getByCode(code)

    suspend fun insert(transaction: TransactionEntity): Long = transactionDao.insert(transaction)

    suspend fun update(transaction: TransactionEntity): Int = transactionDao.update(transaction)

    suspend fun delete(id: Long): Int = transactionDao.deleteById(id)
}
