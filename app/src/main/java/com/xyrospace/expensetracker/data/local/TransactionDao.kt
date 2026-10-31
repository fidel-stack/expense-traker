package com.xyrospace.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xyrospace.expensetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    /**
     * Reactive search and filter query. All bounds are half-open (`>= start`, `< end`).
     * A null argument disables that predicate, so filters combine without string SQL building.
     * LIKE/INSTR matching is case-insensitive for ASCII text and uses parameter binding only.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE (:startInclusive IS NULL OR date >= :startInclusive)
             AND (:endExclusive IS NULL OR date < :endExclusive)
             AND (:category IS NULL OR category = :category COLLATE NOCASE)
             AND (:type IS NULL OR type = :type)
             AND (:search IS NULL
                  OR INSTR(LOWER(IFNULL(recipient, '')), LOWER(:search)) > 0
                  OR INSTR(LOWER(description), LOWER(:search)) > 0
                  OR INSTR(LOWER(category), LOWER(:search)) > 0
                  OR INSTR(LOWER(IFNULL(transaction_code, '')), LOWER(:search)) > 0)
           ORDER BY date DESC, id DESC""",
    )
    fun observeFiltered(
        startInclusive: Long?,
        endExclusive: Long?,
        category: String?,
        type: String?,
        search: String?,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transaction_code = :code LIMIT 1")
    suspend fun getByCode(code: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query(
        """SELECT type, category, SUM(amount) AS amount FROM transactions
           WHERE type IN ('income', 'expense') AND date >= :startInclusive AND date < :endExclusive
           GROUP BY type, category""",
    )
    fun observeMonthlyAmountsByTypeAndCategory(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<MonthlyAmountRow>>

    @Query(
        """SELECT * FROM transactions WHERE date >= :startInclusive AND date < :endExclusive
           ORDER BY date DESC, id DESC LIMIT 5""",
    )
    fun observeRecentTransactions(startInclusive: Long, endExclusive: Long): Flow<List<TransactionEntity>>

    @Query(
        """SELECT COUNT(*) FROM transactions
           WHERE type = 'expense' AND (needs_review = 1 OR category = 'Uncategorized')
             AND date >= :startInclusive AND date < :endExclusive""",
    )
    fun observeMonthlyUncategorizedReviewCount(startInclusive: Long, endExclusive: Long): Flow<Int>
}
