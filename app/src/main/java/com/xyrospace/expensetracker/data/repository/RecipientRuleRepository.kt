package com.xyrospace.expensetracker.data.repository

import com.xyrospace.expensetracker.data.local.RecipientNameNormalizer
import com.xyrospace.expensetracker.data.local.RecipientRuleDao
import com.xyrospace.expensetracker.data.local.RecipientRuleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipientRuleRepository @Inject constructor(
    private val recipientRuleDao: RecipientRuleDao,
) {
    fun observeAll(): Flow<List<RecipientRuleEntity>> = recipientRuleDao.observeAll()

    suspend fun getAll(): List<RecipientRuleEntity> = recipientRuleDao.getAll()

    suspend fun findByRecipientName(name: String): RecipientRuleEntity? =
        recipientRuleDao.findByNormalizedRecipient(RecipientNameNormalizer.normalize(name))

    suspend fun insert(recipientName: String, category: String): Long =
        recipientRuleDao.insert(RecipientRuleEntity.create(recipientName = recipientName, category = category))

    suspend fun update(rule: RecipientRuleEntity): Int = recipientRuleDao.update(rule)

    suspend fun delete(id: Long): Int = recipientRuleDao.deleteById(id)
}
