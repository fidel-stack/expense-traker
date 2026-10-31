package com.xyrospace.expensetracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.data.repository.CategoryRepository
import com.xyrospace.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val searchQuery: String = "",
    val filters: TransactionHistoryFilters = TransactionHistoryFilters(),
    val groups: List<TransactionDateGroup> = emptyList(),
    val resultCount: Int = 0,
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()

    val isEmpty: Boolean get() = !isLoading && errorMessage == null && groups.isEmpty()

    val hasActiveCriteria: Boolean get() = filters.hasActiveFilters || isSearching
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private data class ResultState(
        val groups: List<TransactionDateGroup> = emptyList(),
        val resultCount: Int = 0,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val searchText = MutableStateFlow("")
    private val filters = MutableStateFlow(TransactionHistoryFilters())
    private val refreshTrigger = MutableStateFlow(0)

    private val results = combine(
        searchText.debounce(300).distinctUntilChanged(),
        filters,
        refreshTrigger,
    ) { query, filter, _ ->
        query to filter
    }.flatMapLatest { (query, filter) ->
        // Resolve ranges freshly so "Today"/"This month" follow the device clock.
        val range = filter.resolveDateRange(LocalDate.now(), ZoneId.systemDefault())
        transactionRepository.observeFiltered(
            startInclusive = range?.startInclusive,
            endExclusive = range?.endExclusive,
            category = filter.category,
            type = filter.type?.storageValue,
            search = normalizedSearchTerm(query),
        ).map { transactions ->
            ResultState(
                groups = groupTransactionsByLocalDate(transactions, LocalDate.now()),
                resultCount = transactions.size,
            )
        }.onStart {
            emit(ResultState(isLoading = true))
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(
                ResultState(
                    errorMessage = error.message ?: "Couldn't load your transactions.",
                ),
            )
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        searchText,
        filters,
        results,
        categoryRepository.observeAll(),
    ) { query, filter, result, categories ->
        HistoryUiState(
            searchQuery = query,
            filters = filter,
            groups = result.groups,
            resultCount = result.resultCount,
            categories = categories,
            isLoading = result.isLoading,
            errorMessage = result.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun onSearchQueryChange(query: String) {
        searchText.value = query
    }

    fun clearSearch() {
        searchText.value = ""
    }

    fun setDateFilter(filter: HistoryDateFilter) {
        filters.value = if (filter == HistoryDateFilter.CUSTOM) {
            filters.value.copy(dateFilter = filter)
        } else {
            filters.value.copy(
                dateFilter = filter,
                customStart = null,
                customEnd = null,
            )
        }
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        if (start.isAfter(end)) return
        filters.value = filters.value.copy(
            dateFilter = HistoryDateFilter.CUSTOM,
            customStart = start,
            customEnd = end,
        )
    }

    fun setCategory(category: String?) {
        filters.value = filters.value.copy(category = category)
    }

    fun setType(type: TransactionType?) {
        filters.value = filters.value.copy(type = type)
    }

    fun resetFilters() {
        filters.value = TransactionHistoryFilters()
    }

    fun retry() {
        refreshTrigger.value += 1
    }
}
