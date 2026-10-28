package com.xyrospace.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrospace.expensetracker.data.local.CategoryExpenseRow
import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.repository.CategoryRepository
import com.xyrospace.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _uiState = MutableStateFlow(HomeDashboardUiState())
    val uiState: StateFlow<HomeDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedMonth.flatMapLatest { month ->
                _uiState.update {
                    it.copy(selectedMonth = month, isLoading = true, errorMessage = null)
                }
                val range = month.epochMillisRange()
                combine(
                    transactionRepository.observeMonthlyAmountsByTypeAndCategory(
                        range.startInclusive,
                        range.endExclusive,
                    ),
                    transactionRepository.observeRecentTransactions(
                        range.startInclusive,
                        range.endExclusive,
                    ),
                    transactionRepository.observeMonthlyUncategorizedReviewCount(
                        range.startInclusive,
                        range.endExclusive,
                    ),
                    categoryRepository.observeAll(),
                ) { byType, recent, reviewCount, categories ->
                    val income = byType.asSequence()
                        .filter { it.type == "income" }
                        .fold(0L) { total, row -> safeAdd(total, row.amount) }
                    val expenses = byType.asSequence()
                        .filter { it.type == "expense" }
                        .fold(0L) { total, row -> safeAdd(total, row.amount) }
                    val amountsByCategory = linkedMapOf<String, Long>()
                    byType.asSequence()
                        .filter { it.type == "expense" }
                        .forEach { row ->
                            amountsByCategory[row.category] =
                                safeAdd(amountsByCategory[row.category] ?: 0L, row.amount)
                        }
                    val byCategory = amountsByCategory
                        .map { (category, amount) -> CategoryExpenseRow(category, amount) }
                        .sortedWith(compareByDescending<CategoryExpenseRow> { it.amount }.thenBy { it.category })
                    HomeDashboardUiState(
                        selectedMonth = month,
                        incomeMinor = income,
                        expensesMinor = expenses,
                        categorySpending = buildCategorySpending(byCategory, categories, expenses),
                        categories = categories,
                        recentTransactions = recent,
                        uncategorizedReviewCount = reviewCount,
                        isLoading = false,
                    )
                }.catch { error ->
                    if (error is CancellationException) throw error
                    emit(
                        HomeDashboardUiState(
                            selectedMonth = month,
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't load this month's finances.",
                        ),
                    )
                }
            }.collect { state ->
                _uiState.update { current ->
                    if (state.selectedMonth == _selectedMonth.value) state else current
                }
            }
        }
    }

    fun selectPreviousMonth() {
        _selectedMonth.value = adjacentMonth(_selectedMonth.value, -1, YearMonth.now())
    }

    fun selectNextMonth() {
        _selectedMonth.value = adjacentMonth(_selectedMonth.value, 1, YearMonth.now())
    }
}
