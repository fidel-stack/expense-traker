package com.xyrospace.expensetracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(
    tableName = "recipient_rules",
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
        Index(value = ["normalized_recipient"], unique = true),
        Index(value = ["category"]),
    ],
)
data class RecipientRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recipient_name") val recipientName: String,
    @ColumnInfo(name = "normalized_recipient") val normalizedRecipient: String,
    val category: String,
) {
    init {
        require(recipientName.isNotBlank()) { "Recipient names cannot be blank." }
        require(normalizedRecipient == RecipientNameNormalizer.normalize(recipientName)) {
            "normalizedRecipient must be the lowercase, trimmed recipient name."
        }
        require(category.isNotBlank()) { "A recipient rule must reference a category." }
    }

    companion object {
        fun create(id: Long = 0, recipientName: String, category: String) = RecipientRuleEntity(
            id = id,
            recipientName = recipientName.trim(),
            normalizedRecipient = RecipientNameNormalizer.normalize(recipientName),
            category = category,
        )
    }
}

object RecipientNameNormalizer {
    /** Lowercase with Locale.ROOT and trim outer whitespace; internal whitespace is preserved. */
    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
}
