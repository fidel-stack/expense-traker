package com.xyrospace.expensetracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val icon: String,
    val color: String,
) {
    init {
        require(name.isNotBlank()) { "Category names cannot be blank." }
        require(icon.isNotBlank()) { "Category icons must have a stable identifier." }
        require(HEX_COLOR.matches(color)) { "Category colors must be six-digit hex colors." }
    }

    companion object {
        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    }
}

object DefaultCategories {
    val all: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Food", icon = "restaurant", color = "#C62828"),
        CategoryEntity(name = "Transport", icon = "directions_bus", color = "#1565C0"),
        CategoryEntity(name = "Shopping", icon = "shopping_bag", color = "#6A1B9A"),
        CategoryEntity(name = "Bills and utilities", icon = "receipt_long", color = "#EF6C00"),
        CategoryEntity(name = "Airtime and data", icon = "phone_android", color = "#00838F"),
        CategoryEntity(name = "Health", icon = "medical_services", color = "#2E7D32"),
        CategoryEntity(name = "Entertainment", icon = "movie", color = "#AD1457"),
        CategoryEntity(name = "Education", icon = "school", color = "#283593"),
        CategoryEntity(name = "Rent", icon = "home", color = "#5D4037"),
        CategoryEntity(name = "Cash", icon = "payments", color = "#558B2F"),
        CategoryEntity(name = "Family and people", icon = "groups", color = "#7B1FA2"),
        CategoryEntity(name = "Other", icon = "more_horiz", color = "#546E7A"),
        CategoryEntity(name = "Uncategorized", icon = "help_outline", color = "#616161"),
    )

    fun seed(database: SupportSQLiteDatabase) {
        all.forEach { category ->
            database.execSQL(
                "INSERT OR IGNORE INTO categories (name, icon, color) VALUES (?, ?, ?)",
                arrayOf(category.name, category.icon, category.color),
            )
        }
    }
}
