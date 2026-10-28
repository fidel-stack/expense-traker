package com.xyrospace.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientRuleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: RecipientRuleEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(rule: RecipientRuleEntity): Int

    @Query("DELETE FROM recipient_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM recipient_rules ORDER BY recipient_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RecipientRuleEntity>>

    @Query("SELECT * FROM recipient_rules ORDER BY recipient_name COLLATE NOCASE ASC")
    suspend fun getAll(): List<RecipientRuleEntity>

    @Query("SELECT * FROM recipient_rules WHERE normalized_recipient = :normalizedName LIMIT 1")
    suspend fun findByNormalizedRecipient(normalizedName: String): RecipientRuleEntity?

    suspend fun findByRecipientName(name: String): RecipientRuleEntity? =
        findByNormalizedRecipient(RecipientNameNormalizer.normalize(name))
}
