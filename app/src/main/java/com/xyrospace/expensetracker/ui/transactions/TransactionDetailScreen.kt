package com.xyrospace.expensetracker.ui.transactions

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionType
import com.xyrospace.expensetracker.ui.components.CategoryAvatar
import com.xyrospace.expensetracker.ui.components.incomeAmountColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm", Locale.getDefault())

@Composable
fun TransactionDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    TransactionDetailContent(
        state = state,
        onBack = onNavigateBack,
        onEdit = onEdit,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
fun TransactionDetailContent(
    state: TransactionDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val transaction = state.transaction

    Column(modifier = modifier.fillMaxSize().testTag("screen_transaction_detail")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to history")
            }
            Text(
                text = "Transaction details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            transaction == null -> DetailMessage(
                title = "Transaction not found",
                body = state.errorMessage ?: "This transaction may have been deleted.",
                onBack = onBack,
            )
            else -> {
                val category = state.categories.firstOrNull {
                    it.name.equals(transaction.category, ignoreCase = true)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    AmountCard(transaction)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            DetailRow("Category") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryAvatar(category = category, size = 24.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(category?.name ?: transaction.category)
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            DetailRow("Type") { Text(transactionTypeLabel(transaction.type)) }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            DetailRow("Recipient or source") {
                                Text(transaction.recipient?.takeIf { it.isNotBlank() } ?: "Not provided")
                            }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            DetailRow("Date") {
                                Text(
                                    detailDateFormatter
                                        .withZone(ZoneId.systemDefault())
                                        .format(Instant.ofEpochMilli(transaction.date)),
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            DetailRow("Note") {
                                Text(transaction.description.ifBlank { "No note" })
                            }
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            DetailRow("Source") { Text(transactionSourceLabel(transaction.source)) }
                            transaction.transactionCode?.let { code ->
                                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                                DetailRow("Reference") { Text(code) }
                            }
                        }
                    }

                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = { onEdit(transaction.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("detail_edit"),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit transaction")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.isDeleting,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("detail_delete"),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isDeleting) "Deleting…" else "Delete transaction",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this transaction?") },
            text = { Text("Delete this transaction? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag("cancel_delete"),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AmountCard(transaction: TransactionEntity) {
    val amountColor = when (transaction.type) {
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
        TransactionType.INCOME -> incomeAmountColor()
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = transactionTypeLabel(transaction.type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatHistoryAmount(transaction.type, transaction.amount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = transaction.recipient?.takeIf { it.isNotBlank() } ?: transaction.category,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Box(Modifier.weight(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            ) {
                value()
            }
        }
    }
}

@Composable
private fun DetailMessage(title: String, body: String, onBack: () -> Unit) {
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
        Button(onClick = onBack) { Text("Back to history") }
    }
}
