package com.xyrospace.expensetracker.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xyrospace.expensetracker.data.local.CategoryEntity

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["name"],
            childColumns = ["category"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["category"]),
        Index(value = ["transaction_code"], unique = true),
        Index(value = ["legacy_id"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val recipient: String? = null,
    val date: Long,
    val source: TransactionSource,
    @ColumnInfo(name = "transaction_code") val transactionCode: String? = null,
    @ColumnInfo(name = "needs_review", defaultValue = "0") val needsReview: Boolean = false,
    val description: String = "",
    @ColumnInfo(name = "created_timestamp") val createdTimestamp: Long,
    @ColumnInfo(name = "legacy_id") val legacyId: String? = null,
) {
    init {
        require(amount >= 0) { "Transaction amounts must be non-negative minor units." }
        require(category.isNotBlank()) { "A transaction must reference a category." }
        require(transactionCode == null || transactionCode.isNotBlank()) {
            "A transaction code must be null or non-blank."
        }
    }
}

enum class TransactionType(val storageValue: String) {
    EXPENSE("expense"),
    INCOME("income"),
    TRANSFER("transfer"),
}

enum class TransactionSource(val storageValue: String) {
    MPESA("mpesa"),
    BANK("bank"),
    MANUAL("manual"),
}
