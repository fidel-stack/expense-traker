package com.xyrospace.expensetracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.ui.components.CategoryAvatar
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())

@Composable
fun TransactionFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.consumeSavedState()
            onSaved()
        }
    }

    TransactionFormContent(
        state = state,
        onBack = onNavigateBack,
        onAmountChange = viewModel::onAmountChange,
        onTypeChange = viewModel::onTypeChange,
        onCategoryChange = viewModel::onCategoryChange,
        onRecipientChange = viewModel::onRecipientChange,
        onDateChange = viewModel::onDateChange,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::save,
        onRetryLoad = viewModel::retryLoad,
        modifier = modifier,
    )
}

@Composable
fun TransactionFormContent(
    state: TransactionFormState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onDateChange: (java.time.LocalDate) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(if (state.isEditing) "screen_edit_transaction" else "screen_add"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isEditing) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (state.isEditing) "Edit transaction" else "Add transaction",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.isNotFound -> FormMessage(
                title = "Transaction not found",
                body = "It may have been deleted. Return to history to continue.",
                actionLabel = "Back to history",
                onAction = onBack,
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth().testTag("form_amount"),
                    label = { Text("Amount") },
                    prefix = { Text("KES") },
                    singleLine = true,
                    isError = state.errors.amount != null,
                    supportingText = {
                        Text(state.errors.amount ?: "Enter the amount in Kenyan shillings.")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !state.isSaving,
                )

                Column {
                    Text("Transaction type", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    if (state.canChangeType) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.type == TransactionType.EXPENSE,
                                onClick = { onTypeChange(TransactionType.EXPENSE) },
                                label = { Text("Expense") },
                                modifier = Modifier.testTag("form_type_expense"),
                                enabled = !state.isSaving,
                            )
                            FilterChip(
                                selected = state.type == TransactionType.INCOME,
                                onClick = { onTypeChange(TransactionType.INCOME) },
                                label = { Text("Income") },
                                modifier = Modifier.testTag("form_type_income"),
                                enabled = !state.isSaving,
                            )
                        }
                    } else {
                        Text(
                            text = "Transfer — transfers keep their original type when edited.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { showCategoryMenu = true },
                            modifier = Modifier.fillMaxWidth().testTag("form_category"),
                            enabled = !state.isSaving,
                        ) {
                            val selected = state.categories.firstOrNull {
                                it.name.equals(state.category, ignoreCase = true)
                            }
                            CategoryAvatar(category = selected, size = 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = state.category.ifBlank { "Choose a category" },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                        ) {
                            state.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        showCategoryMenu = false
                                        onCategoryChange(category.name)
                                    },
                                    leadingIcon = { CategoryAvatar(category = category, size = 24.dp) },
                                )
                            }
                        }
                    }
                    if (state.errors.category != null) {
                        Text(
                            text = state.errors.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = state.recipient,
                    onValueChange = onRecipientChange,
                    modifier = Modifier.fillMaxWidth().testTag("form_recipient"),
                    label = { Text("Recipient or source (optional)") },
                    placeholder = { Text("e.g. Naivas, Uber, Equity Bank") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )

                Column {
                    Text("Date", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().testTag("form_date"),
                        enabled = !state.isSaving,
                    ) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(state.date.format(formDateFormatter), modifier = Modifier.weight(1f))
                    }
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth().testTag("form_note"),
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. October salary payment") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                state.existing?.let { existing ->
                    Text(
                        text = buildString {
                            append("Source: ${state.sourceLabel}")
                            existing.transactionCode?.let { append(" · Reference: $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("form_error"),
                    )
                    if (state.isEditing && state.existing == null) {
                        androidx.compose.material3.TextButton(onClick = onRetryLoad) {
                            Text("Try again")
                        }
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("form_save"),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Saving…")
                    } else {
                        Text(if (state.isEditing) "Save changes" else "Save transaction")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            initialDate = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                showDatePicker = false
                onDateChange(date)
            },
        )
    }
}

@Composable
private fun FormMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
