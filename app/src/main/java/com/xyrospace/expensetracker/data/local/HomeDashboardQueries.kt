package com.xyrospace.expensetracker.data.local

data class MonthlyAmountRow(
    val type: String,
    val category: String,
    val amount: Long,
)

data class CategoryExpenseRow(
    val category: String,
    val amount: Long,
)
