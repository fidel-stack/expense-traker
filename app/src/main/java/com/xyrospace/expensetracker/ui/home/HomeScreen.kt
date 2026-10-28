package com.xyrospace.expensetracker.ui.home

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionType
import java.time.YearMonth

@Composable
fun HomeScreen(
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    HomeDashboardContent(
        state = state,
        onPreviousMonth = viewModel::selectPreviousMonth,
        onNextMonth = viewModel::selectNextMonth,
        onViewAll = onViewAll,
        modifier = modifier,
    )
}

@Composable
fun HomeDashboardContent(
    state: HomeDashboardUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    today: YearMonth = YearMonth.now(),
) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("home_dashboard_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            MonthSelector(
                month = state.selectedMonth,
                canGoNext = state.selectedMonth < today,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )
        }
        item {
            SummaryCards(
                income = state.incomeMinor,
                expenses = state.expensesMinor,
                net = state.netIncomeMinor,
            )
        }
        if (state.uncategorizedReviewCount > 0) {
            item { ReviewBanner(state.uncategorizedReviewCount) }
        }
        item {
            SectionTitle("Spending breakdown")
            Spacer(Modifier.height(8.dp))
            SpendingDonut(
                categories = state.categorySpending,
                totalExpenses = state.expensesMinor,
            )
        }
        if (state.categorySpending.isNotEmpty()) {
            item { SectionTitle("By category") }
            items(state.categorySpending, key = { it.category.id }) { spending ->
                CategorySpendingRow(spending)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionTitle("Recent transactions")
                OutlinedButton(onClick = onViewAll) { Text("View all") }
            }
        }
        if (state.recentTransactions.isEmpty()) {
            item {
                Text(
                    text = if (state.isEmptyMonth) "No financial activity for ${state.selectedMonth.displayName()}." else "No transactions for ${state.selectedMonth.displayName()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(state.recentTransactions, key = { it.id }) { transaction ->
                RecentTransactionRow(transaction, state.categories)
            }
        }
        state.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            IconButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Next month")
            }
        }
    }
}

@Composable
private fun SummaryCards(income: Long, expenses: Long, net: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCard("Net income", net, Modifier.weight(1f), kind = SummaryKind.NET)
        SummaryCard("Income", income, Modifier.weight(1f), kind = SummaryKind.INCOME)
        SummaryCard("Expenses", expenses, Modifier.weight(1f), kind = SummaryKind.EXPENSE)
    }
}

private enum class SummaryKind { NET, INCOME, EXPENSE }

@Composable
private fun SummaryCard(label: String, amount: Long, modifier: Modifier, kind: SummaryKind) {
    val amountColor = when {
        kind != SummaryKind.NET -> MaterialTheme.colorScheme.onSurface
        amount > 0L -> Color(0xFF2E7D32)
        amount < 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (kind) {
        SummaryKind.NET -> Icons.Filled.AccountBalanceWallet
        SummaryKind.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
        SummaryKind.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp)) {
            Icon(icon, contentDescription = null, tint = amountColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatKes(amount, showSign = kind == SummaryKind.NET),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReviewBanner(count: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "$count transaction${if (count == 1) "" else "s"} need${if (count == 1) "s" else ""} category review",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun SpendingDonut(categories: List<DashboardCategorySpending>, totalExpenses: Long) {
    val distribution = if (categories.isEmpty()) "No expense spending this month" else categories.joinToString {
        "${it.category.name}: ${formatPercentage(it.percentageBasisPoints)}"
    }
    val emptyTrackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = "Monthly spending distribution. $distribution" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 24.dp.toPx()
            val diameter = minOf(size.width, size.height)
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f + strokeWidth / 2f,
                (size.height - diameter) / 2f + strokeWidth / 2f,
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter - strokeWidth, diameter - strokeWidth)
            if (totalExpenses <= 0L || categories.isEmpty()) {
                drawArc(
                    color = emptyTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                )
            } else {
                var startAngle = -90f
                categories.forEach { item ->
                    val sweep = (item.amountMinor.toDouble() / totalExpenses.toDouble() * 360.0).toFloat()
                    if (sweep > 0f) {
                        drawArc(
                            color = parseCategoryColor(item.category.color),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(strokeWidth, cap = StrokeCap.Butt),
                        )
                        startAngle += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatKes(totalExpenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategorySpendingRow(spending: DashboardCategorySpending) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(spending.category, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(spending.category.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatPercentage(spending.percentageBasisPoints), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatKes(spending.amountMinor), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentTransactionRow(transaction: TransactionEntity, categories: List<CategoryEntity>) {
    val category = categories.firstOrNull { it.name.equals(transaction.category, ignoreCase = true) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val (amount, tint, icon) = when (transaction.type) {
        TransactionType.INCOME -> Triple(formatKes(transaction.amount, true), Color(0xFF2E7D32), Icons.AutoMirrored.Filled.TrendingUp)
        TransactionType.EXPENSE -> Triple("−${formatKes(transaction.amount)}", errorColor, Icons.AutoMirrored.Filled.TrendingDown)
        TransactionType.TRANSFER -> Triple(formatKes(transaction.amount), onSurfaceVariant, Icons.Filled.SwapHoriz)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category != null) CategoryIcon(category, 40.dp) else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.recipient?.takeIf { it.isNotBlank() } ?: transaction.category, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${transaction.category} · ${formatTransactionDate(transaction.date)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.SemiBold)
            Icon(icon, contentDescription = when (transaction.type) {
                TransactionType.INCOME -> "Income"
                TransactionType.EXPENSE -> "Expense"
                TransactionType.TRANSFER -> "Transfer"
            }, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CategoryIcon(category: CategoryEntity, size: androidx.compose.ui.unit.Dp) {
    val color = parseCategoryColor(category.color)
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (category.icon) {
            "restaurant" -> Icons.Filled.Restaurant
            "directions_bus" -> Icons.Filled.DirectionsBus
            "shopping_bag" -> Icons.Filled.ShoppingBag
            "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
            "phone_android" -> Icons.Filled.PhoneAndroid
            "medical_services" -> Icons.Filled.MedicalServices
            "movie" -> Icons.Filled.Movie
            "school" -> Icons.Filled.School
            "home" -> Icons.Filled.Home
            "payments" -> Icons.Filled.Payments
            "groups" -> Icons.Filled.Groups
            "more_horiz" -> Icons.Filled.MoreHoriz
            else -> Icons.AutoMirrored.Filled.HelpOutline
        }
        Icon(icon, contentDescription = category.name, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

private fun parseCategoryColor(hex: String): Color = try {
    Color(AndroidColor.parseColor(hex))
} catch (_: IllegalArgumentException) {
    Color.Gray
}
