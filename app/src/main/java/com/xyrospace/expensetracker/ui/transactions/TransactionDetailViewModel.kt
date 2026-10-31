package com.xyrospace.expensetracker.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.repository.CategoryRepository
import com.xyrospace.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionDetailUiState(
    val transaction: TransactionEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val transactionId: Long = checkNotNull(savedStateHandle[AddTransactionViewModel.TRANSACTION_ID_ARG]) {
        "A transaction ID is required to view transaction details."
    }

    private data class RecordState(
        val transaction: TransactionEntity? = null,
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
    )

    private data class ActionState(
        val isDeleting: Boolean = false,
        val deleted: Boolean = false,
        val errorMessage: String? = null,
    )

    private val actions = MutableStateFlow(ActionState())

    private val record = transactionRepository.observeById(transactionId)
        .map { RecordState(transaction = it, isLoading = false) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(
                RecordState(
                    isLoading = false,
                    errorMessage = error.message ?: "Couldn't load this transaction.",
                ),
            )
        }

    val uiState: StateFlow<TransactionDetailUiState> = combine(
        record,
        categoryRepository.observeAll(),
        actions,
    ) { recordState, categories, actionState ->
        TransactionDetailUiState(
            transaction = recordState.transaction,
            categories = categories,
            isLoading = recordState.isLoading,
            isDeleting = actionState.isDeleting,
            deleted = actionState.deleted,
            errorMessage = actionState.errorMessage ?: recordState.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionDetailUiState(),
    )

    fun delete() {
        val current = uiState.value
        if (current.isDeleting || current.deleted) return
        actions.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                transactionRepository.delete(transactionId)
                actions.update { it.copy(isDeleting = false, deleted = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actions.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = error.message
                            ?: "Couldn't delete this transaction. Please try again.",
                    )
                }
            }
        }
    }

    fun clearError() {
        actions.update { it.copy(errorMessage = null) }
    }
}
