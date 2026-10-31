package com.xyrospace.expensetracker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.ui.components.CategoryAvatar
import com.xyrospace.expensetracker.ui.components.incomeAmountColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TransactionHistoryScreen(
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TransactionHistoryContent(
        state = state,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSearch = viewModel::clearSearch,
        onDateFilterChange = viewModel::setDateFilter,
        onCustomDateRangeChange = viewModel::setCustomDateRange,
        onCategoryChange = viewModel::setCategory,
        onTypeChange = viewModel::setType,
        onResetFilters = viewModel::resetFilters,
        onRetry = viewModel::retry,
        onTransactionClick = onTransactionClick,
        modifier = modifier,
    )
}

@Composable
fun TransactionHistoryContent(
    state: HistoryUiState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDateFilterChange: (HistoryDateFilter) -> Unit,
    onCustomDateRangeChange: (LocalDate, LocalDate) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onTypeChange: (TransactionType?) -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerStep by remember { mutableStateOf(DatePickerStep.NONE) }
    var pendingStart by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }

    Column(modifier = modifier.fillMaxSize().testTag("screen_history")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            SearchField(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                onClear = onClearSearch,
            )
            Spacer(Modifier.height(8.dp))
            FilterChipRow(
                state = state,
                onSelectDateFilter = { filter ->
                    if (filter == HistoryDateFilter.CUSTOM) {
                        pendingStart = state.filters.customStart ?: today
                        pickerStep = DatePickerStep.START
                    } else {
                        onDateFilterChange(filter)
                    }
                },
                onCategoryChange = onCategoryChange,
                onTypeChange = onTypeChange,
                onResetFilters = onResetFilters,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = when {
                        state.isLoading && state.groups.isEmpty() -> "Loading transactions…"
                        state.resultCount == 1 -> "1 transaction"
                        else -> "${state.resultCount} transactions"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.searchQuery.isNotBlank()) {
                    TextButton(onClick = onClearSearch, modifier = Modifier.testTag("history_clear_search")) {
                        Text("Clear search")
                    }
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        when {
            state.errorMessage != null -> {
                MessageState(
                    title = "Couldn't load transactions",
                    body = state.errorMessage,
                    actionLabel = "Try again",
                    onAction = onRetry,
                )
            }
            state.isEmpty -> {
                MessageState(
                    title = if (state.hasActiveCriteria) "No matching transactions" else "No transactions yet",
                    body = if (state.hasActiveCriteria) {
                        "Try a different search term or adjust your filters."
                    } else {
                        "Transactions you add or import will appear here."
                    },
                    actionLabel = if (state.hasActiveCriteria) "Clear search and filters" else null,
                    onAction = {
                        onClearSearch()
                        onResetFilters()
                    },
                    modifier = Modifier.testTag("history_empty_state"),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("history_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "date-${group.date}") {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.transactions, key = { "transaction-${it.id}" }) { transaction ->
                            TransactionRow(
                                transaction = transaction,
                                category = state.categories.firstOrNull {
                                    it.name.equals(transaction.category, ignoreCase = true)
                                },
                                onClick = { onTransactionClick(transaction.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickerStep != DatePickerStep.NONE) {
        val initialDate = when (pickerStep) {
            DatePickerStep.START -> pendingStart ?: today
            DatePickerStep.END -> state.filters.customEnd ?: pendingStart ?: today
            DatePickerStep.NONE -> today
        }
        // A new key per step so the picker starts from the relevant date each time.
        androidx.compose.runtime.key(pickerStep) {
            LocalDatePickerDialog(
                initialDate = initialDate,
                minimumDate = if (pickerStep == DatePickerStep.END) pendingStart else null,
                onDismiss = {
                    pickerStep = DatePickerStep.NONE
                    pendingStart = null
                },
                onConfirm = { selected ->
                    if (pickerStep == DatePickerStep.START) {
                        pendingStart = selected
                        pickerStep = DatePickerStep.END
                    } else {
                        val start = pendingStart
                        if (start != null && !start.isAfter(selected)) {
                            onCustomDateRangeChange(start, selected)
                        }
                        pickerStep = DatePickerStep.NONE
                        pendingStart = null
                    }
                },
            )
        }
    }
}

private enum class DatePickerStep { NONE, START, END }

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().testTag("history_search"),
        singleLine = true,
        placeholder = { Text("Search recipient, note, category or code") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.testTag("history_search_clear")) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun FilterChipRow(
    state: HistoryUiState,
    onSelectDateFilter: (HistoryDateFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onTypeChange: (TransactionType?) -> Unit,
    onResetFilters: () -> Unit,
) {
    var dateMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var typeMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            FilterChip(
                selected = state.filters.dateFilter != HistoryDateFilter.ALL,
                onClick = { dateMenuOpen = true },
                label = { Text(state.filters.dateFilterSummary()) },
                leadingIcon = {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.testTag("history_filter_date"),
            )
            DropdownMenu(expanded = dateMenuOpen, onDismissRequest = { dateMenuOpen = false }) {
                HistoryDateFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label) },
                        onClick = {
                            dateMenuOpen = false
                            onSelectDateFilter(filter)
                        },
                        leadingIcon = {
                            if (state.filters.dateFilter == filter) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }

        Box {
            FilterChip(
                selected = state.filters.category != null,
                onClick = { categoryMenuOpen = true },
                label = { Text(state.filters.category ?: "All categories") },
                leadingIcon = {
                    Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.testTag("history_filter_category"),
            )
            DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("All categories") },
                    onClick = {
                        categoryMenuOpen = false
                        onCategoryChange(null)
                    },
                    leadingIcon = {
                        if (state.filters.category == null) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
                state.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            categoryMenuOpen = false
                            onCategoryChange(category.name)
                        },
                        leadingIcon = {
                            if (state.filters.category.equals(category.name, ignoreCase = true)) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }

        Box {
            FilterChip(
                selected = state.filters.type != null,
                onClick = { typeMenuOpen = true },
                label = {
                    Text(state.filters.type?.let(::transactionTypeLabel) ?: "All types")
                },
                leadingIcon = {
                    Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.testTag("history_filter_type"),
            )
            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                listOf<TransactionType?>(null, TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
                    .forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type?.let(::transactionTypeLabel) ?: "All types") },
                            onClick = {
                                typeMenuOpen = false
                                onTypeChange(type)
                            },
                            leadingIcon = {
                                if (state.filters.type == type) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                        )
                    }
            }
        }

        if (state.filters.hasActiveFilters) {
            TextButton(onClick = onResetFilters, modifier = Modifier.testTag("history_reset_filters")) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
) {
    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
        TransactionType.INCOME -> incomeAmountColor()
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("transaction_row_${transaction.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(category = category, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = transaction.recipient?.takeIf { it.isNotBlank() } ?: transaction.category,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (transaction.needsReview) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = "Needs review",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatHistoryAmount(transaction.type, transaction.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
                maxLines = 1,
            )
            Text(
                text = formatTransactionTime(transaction.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    minimumDate: LocalDate? = null,
) {
    val selectableDates = remember(minimumDate) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                minimumDate == null ||
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() >= minimumDate

            override fun isSelectableYear(year: Int): Boolean =
                minimumDate == null || year >= minimumDate.year
        }
    }
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState, showModeToggle = false)
    }
}
