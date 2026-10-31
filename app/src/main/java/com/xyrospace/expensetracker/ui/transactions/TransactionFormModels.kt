package com.xyrospace.expensetracker.ui.transactions

import com.xyrospace.expensetracker.data.local.CategoryEntity
import com.xyrospace.expensetracker.data.model.TransactionEntity
import com.xyrospace.expensetracker.data.model.TransactionSource
import com.xyrospace.expensetracker.data.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

const val UNCATEGORIZED_CATEGORY = "Uncategorized"

private val AMOUNT_PATTERN = Regex("^(\\d+)(?:\\.(\\d{1,2}))?$")

sealed interface ParsedAmount {
    data class Valid(val minorUnits: Long) : ParsedAmount
    data class Invalid(val message: String) : ParsedAmount
}

/**
 * Parses user-entered KES into exact minor units. Monetary values never pass through
 * Float or Double, and values beyond the Long range are rejected instead of wrapping.
 */
fun parseKesAmount(raw: String): ParsedAmount {
    val text = raw.trim()
    if (text.isEmpty()) return ParsedAmount.Invalid("Enter an amount.")
    val match = AMOUNT_PATTERN.matchEntire(text)
        ?: return ParsedAmount.Invalid("Enter a valid amount with up to two decimal places.")
    val whole = match.groupValues[1]
    val fraction = match.groupValues[2]
    return try {
        val decimal = BigDecimal("$whole.${fraction.padEnd(2, '0')}")
        val minorUnits = decimal.movePointRight(2).longValueExact()
        if (minorUnits <= 0L) {
            ParsedAmount.Invalid("Amount must be greater than zero.")
        } else {
            ParsedAmount.Valid(minorUnits)
        }
    } catch (_: NumberFormatException) {
        ParsedAmount.Invalid("Enter a valid amount with up to two decimal places.")
    } catch (_: ArithmeticException) {
        ParsedAmount.Invalid("Amount is too large.")
    }
}

fun minorUnitsToInput(minorUnits: Long): String =
    BigDecimal.valueOf(minorUnits).movePointLeft(2).toPlainString()

data class TransactionFormErrors(
    val amount: String? = null,
    val category: String? = null,
) {
    val hasErrors: Boolean get() = amount != null || category != null
}

data class TransactionFormState(
    val transactionId: Long? = null,
    val existing: TransactionEntity? = null,
    val amountInput: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "",
    val recipient: String = "",
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val errors: TransactionFormErrors = TransactionFormErrors(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isNotFound: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean get() = transactionId != null

    /** Transfers keep their transfer semantics instead of being converted to income or expense. */
    val canChangeType: Boolean
        get() = existing?.type != TransactionType.TRANSFER

    val sourceLabel: String
        get() = transactionSourceLabel(existing?.source ?: TransactionSource.MANUAL)
}

data class TransactionFormValidation(
    val amountMinor: Long? = null,
    val categoryName: String? = null,
    val errors: TransactionFormErrors = TransactionFormErrors(),
)

fun resolveCategoryName(name: String, categories: List<CategoryEntity>): String? =
    categories.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.name

fun TransactionFormState.validate(): TransactionFormValidation {
    val amountResult = parseKesAmount(amountInput)
    val canonicalCategory = resolveCategoryName(category, categories)
        ?: resolveCategoryName(UNCATEGORIZED_CATEGORY, categories)
    val amountError = (amountResult as? ParsedAmount.Invalid)?.message
    val categoryError = if (canonicalCategory == null) "Choose a category." else null
    return TransactionFormValidation(
        amountMinor = (amountResult as? ParsedAmount.Valid)?.minorUnits,
        categoryName = if (categoryError == null) canonicalCategory else null,
        errors = TransactionFormErrors(amount = amountError, category = categoryError),
    )
}

fun computeNeedsReview(type: TransactionType, categoryName: String): Boolean =
    type == TransactionType.EXPENSE && categoryName.equals(UNCATEGORIZED_CATEGORY, ignoreCase = true)

/** Uses the current instant for today, and local noon for back-dated entries. */
fun dateTimestampFor(date: LocalDate, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return if (date == today) {
        nowMillis
    } else {
        date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
    }
}

/** Keeps the original time of day when the edited date is unchanged. */
fun editedTimestamp(
    originalMillis: Long,
    date: LocalDate,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val originalDate = Instant.ofEpochMilli(originalMillis).atZone(zoneId).toLocalDate()
    return if (originalDate == date) originalMillis else dateTimestampFor(date, nowMillis, zoneId)
}

fun buildNewTransaction(
    state: TransactionFormState,
    amountMinor: Long,
    categoryName: String,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): TransactionEntity = TransactionEntity(
    amount = amountMinor,
    type = state.type,
    category = categoryName,
    recipient = state.recipient.trim().ifEmpty { null },
    date = dateTimestampFor(state.date, nowMillis, zoneId),
    source = TransactionSource.MANUAL,
    // Manual entries have no external reference; codes are never generated.
    transactionCode = null,
    needsReview = computeNeedsReview(state.type, categoryName),
    description = state.note.trim(),
    createdTimestamp = nowMillis,
)

fun buildEditedTransaction(
    existing: TransactionEntity,
    state: TransactionFormState,
    amountMinor: Long,
    categoryName: String,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): TransactionEntity {
    val resolvedType = if (existing.type == TransactionType.TRANSFER) {
        TransactionType.TRANSFER
    } else {
        state.type
    }
    return existing.copy(
        id = existing.id,
        amount = amountMinor,
        type = resolvedType,
        category = categoryName,
        recipient = state.recipient.trim().ifEmpty { null },
        date = editedTimestamp(existing.date, state.date, nowMillis, zoneId),
        // Source, external code, legacy id, and creation time are preserved from the original record.
        source = existing.source,
        transactionCode = existing.transactionCode,
        needsReview = computeNeedsReview(resolvedType, categoryName),
        description = state.note.trim(),
        createdTimestamp = existing.createdTimestamp,
        legacyId = existing.legacyId,
    )
}

fun categoryInitials(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
