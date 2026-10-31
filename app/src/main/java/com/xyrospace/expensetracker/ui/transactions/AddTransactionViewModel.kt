package com.xyrospace.expensetracker.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.data.repository.CategoryRepository
import com.xyrospace.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs both manual entry and editing. Editing loads by database ID and reuses the same
 * validation and form state, so no separate implementation is duplicated.
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>(TRANSACTION_ID_ARG)

    private val _uiState = MutableStateFlow(
        TransactionFormState(
            transactionId = editingId,
            date = LocalDate.now(),
            isLoading = editingId != null,
        ),
    )
    val uiState: StateFlow<TransactionFormState> = _uiState.asStateFlow()

    init {
        observeCategories()
        editingId?.let(::loadTransaction)
    }

    private fun observeCategories() {
        viewModelScope.launch {
            categoryRepository.observeAll()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Couldn't load categories.")
                    }
                }
                .collect { categories ->
                    _uiState.update { state ->
                        val selected = resolveCategoryName(state.category, categories)
                            ?: resolveCategoryName(UNCATEGORIZED_CATEGORY, categories)
                            ?: categories.firstOrNull()?.name
                            ?: ""
                        state.copy(categories = categories, category = selected)
                    }
                }
        }
    }

    private fun loadTransaction(id: Long) {
        viewModelScope.launch {
            try {
                val transaction = transactionRepository.getById(id)
                if (transaction == null) {
                    _uiState.update { it.copy(isLoading = false, isNotFound = true) }
                    return@launch
                }
                _uiState.update { state ->
                    state.copy(
                        transactionId = transaction.id,
                        existing = transaction,
                        amountInput = minorUnitsToInput(transaction.amount),
                        type = transaction.type,
                        category = resolveCategoryName(transaction.category, state.categories)
                            ?: transaction.category,
                        recipient = transaction.recipient.orEmpty(),
                        date = Instant.ofEpochMilli(transaction.date)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate(),
                        note = transaction.description,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Couldn't load this transaction.",
                    )
                }
            }
        }
    }

    fun retryLoad() {
        val id = _uiState.value.transactionId ?: return
        _uiState.update { it.copy(isLoading = true, isNotFound = false, errorMessage = null) }
        loadTransaction(id)
    }

    fun onAmountChange(value: String) {
        _uiState.update {
            it.copy(amountInput = value, errors = it.errors.copy(amount = null))
        }
    }

    fun onTypeChange(type: TransactionType) {
        if (type == TransactionType.TRANSFER) return
        _uiState.update {
            if (!it.canChangeType) it else it.copy(type = type)
        }
    }

    fun onCategoryChange(category: String) {
        _uiState.update {
            it.copy(category = category, errors = it.errors.copy(category = null))
        }
    }

    fun onRecipientChange(value: String) {
        _uiState.update { it.copy(recipient = value) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isSaved || state.isLoading || state.isNotFound) return

        val validation = state.validate()
        val amountMinor = validation.amountMinor
        val categoryName = validation.categoryName
        if (validation.errors.hasErrors || amountMinor == null || categoryName == null) {
            _uiState.update { it.copy(errors = validation.errors) }
            return
        }

        _uiState.update {
            it.copy(isSaving = true, errors = validation.errors, errorMessage = null)
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val existing = state.existing
                if (existing != null) {
                    // Re-read so the edit applies to the current record, preserving its ID.
                    val current = transactionRepository.getById(existing.id)
                        ?: throw IllegalStateException("This transaction no longer exists.")
                    val updated = transactionRepository.update(
                        buildEditedTransaction(current, state, amountMinor, categoryName, now),
                    )
                    if (updated == 0) throw IllegalStateException("This transaction no longer exists.")
                } else {
                    transactionRepository.insert(
                        buildNewTransaction(state, amountMinor, categoryName, now),
                    )
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message
                            ?: "Couldn't save this transaction. Please try again.",
                    )
                }
            }
        }
    }

    /** Consumes the one-shot save signal; manual entry starts fresh, editing keeps its data. */
    fun consumeSavedState() {
        _uiState.update { state ->
            if (state.isEditing) {
                state.copy(isSaved = false)
            } else {
                state.copy(
                    amountInput = "",
                    recipient = "",
                    note = "",
                    date = LocalDate.now(),
                    type = TransactionType.EXPENSE,
                    category = resolveCategoryName(UNCATEGORIZED_CATEGORY, state.categories)
                        ?: state.categories.firstOrNull()?.name.orEmpty(),
                    errors = TransactionFormErrors(),
                    isSaved = false,
                    errorMessage = null,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        const val TRANSACTION_ID_ARG = "transactionId"
    }
}
