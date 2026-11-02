package com.xyrospace.expensetracker.sms

import java.util.Locale

/**
 * An allowlisted SMS sender.
 *
 * [displayName] preserves the casing the user (or the default list) supplied, while [senderId]
 * is the normalized identity used for exact matching.
 */
data class AllowedSender(
    val displayName: String,
    val senderId: String,
) {
    init {
        require(SmsSenderId.normalize(displayName) == senderId) {
            "senderId must be the normalized display name."
        }
        require(SmsSenderId.isWellFormed(senderId)) { "senderId is not a valid sender identifier." }
    }
}

enum class SmsSenderValidationError {
    EMPTY,
    TOO_LONG,
    WILDCARD_PATTERN,
    INVALID_CHARACTERS,
}

sealed interface SmsSenderValidation {
    data class Valid(val sender: AllowedSender) : SmsSenderValidation
    data class Invalid(val error: SmsSenderValidationError) : SmsSenderValidation
}

/**
 * Deterministic, locale-independent sender handling.
 *
 * Matching is exact after normalization: surrounding whitespace is trimmed and casing is folded
 * with [Locale.ROOT]. Internal whitespace and punctuation are preserved, so no substring, wildcard
 * or fuzzy comparison can make `MPESA-ALERT`, `FAKE-MPESA` or `KCB_BANK_EXTRA` match an allowlisted
 * identity.
 */
object SmsSenderId {
    const val MAX_LENGTH = 32

    private const val WILDCARDS = "*?%"
    private val ALLOWED_PUNCTUATION = charArrayOf(
        ' ', '-', '_', '.', '&', '+', '\'', '/', '(', ')',
    )

    fun normalize(value: String): String = value.trim().uppercase(Locale.ROOT)

    fun normalizeOrNull(value: String?): String? =
        value?.let(::normalize)?.takeIf { it.isNotEmpty() }

    /** Structural check used for persisted identifiers. */
    fun isWellFormed(senderId: String): Boolean =
        senderId.isNotEmpty() &&
            senderId.length <= MAX_LENGTH &&
            senderId.none { it in WILDCARDS } &&
            senderId.all { it.isLetterOrDigit() || it in ALLOWED_PUNCTUATION } &&
            senderId.any { it.isLetterOrDigit() }

    /** Validates user input for a new allowlist entry, preserving its display name. */
    fun validateSender(rawSenderId: String): SmsSenderValidation {
        val displayName = rawSenderId.trim()
        return when {
            displayName.isEmpty() ->
                SmsSenderValidation.Invalid(SmsSenderValidationError.EMPTY)

            displayName.length > MAX_LENGTH ->
                SmsSenderValidation.Invalid(SmsSenderValidationError.TOO_LONG)

            displayName.any { it in WILDCARDS } ->
                SmsSenderValidation.Invalid(SmsSenderValidationError.WILDCARD_PATTERN)

            displayName.none { it.isLetterOrDigit() } ||
                displayName.any { !it.isLetterOrDigit() && it !in ALLOWED_PUNCTUATION } ->
                SmsSenderValidation.Invalid(SmsSenderValidationError.INVALID_CHARACTERS)

            else -> SmsSenderValidation.Valid(
                AllowedSender(
                    displayName = displayName,
                    senderId = normalize(displayName),
                ),
            )
        }
    }
}

/** Exact, case-insensitive after normalization, allowlist lookup. */
object SmsSenderFilter {
    fun findAllowedSender(
        rawSenderId: String?,
        allowedSenders: List<AllowedSender>,
    ): AllowedSender? {
        val normalized = SmsSenderId.normalizeOrNull(rawSenderId) ?: return null
        return allowedSenders.firstOrNull { it.senderId == normalized }
    }

    fun isAllowed(rawSenderId: String?, allowedSenders: List<AllowedSender>): Boolean =
        findAllowedSender(rawSenderId, allowedSenders) != null
}

/**
 * Pure allowlist operations shared by the repository and its tests.
 *
 * Identity is the normalized sender ID. Display names are kept for presentation and encoded
 * alongside the identity so persistence never loses the user's casing.
 */
object SmsAllowlist {
    private const val ENTRY_SEPARATOR = '\u001F'

    /**
     * The documented Kenyan financial sender IDs. `EQUITY BANK` appears twice in the source list
     * and is intentionally stored once by [defaultSenders].
     */
    private val DEFAULT_DISPLAY_NAMES = listOf(
        "MPESA",
        "CoopBank",
        "CO-OPBANK",
        "EQUITY BANK",
        "EQUITEL",
        "KCB",
        "KCB_BANK",
        "KCBBANK",
        "ABSA",
        "ABSA_BANK",
        "NCBA",
        "NCBABANK",
        "StanChart",
        "FamilyBank",
        "DTB",
        "Stanbic",
        "I&M Bank",
    )

    fun defaultSenders(): List<AllowedSender> {
        val seen = mutableSetOf<String>()
        return DEFAULT_DISPLAY_NAMES.mapNotNull { displayName ->
            val sender = (SmsSenderId.validateSender(displayName) as? SmsSenderValidation.Valid)
                ?.sender ?: return@mapNotNull null
            if (seen.add(sender.senderId)) sender else null
        }
    }

    fun containsSender(senders: List<AllowedSender>, senderId: String): Boolean {
        val normalized = SmsSenderId.normalizeOrNull(senderId) ?: return false
        return senders.any { it.senderId == normalized }
    }

    fun addSender(senders: List<AllowedSender>, sender: AllowedSender): List<AllowedSender> =
        if (containsSender(senders, sender.senderId)) senders else sort(senders + sender)

    fun removeSender(senders: List<AllowedSender>, senderId: String): List<AllowedSender> {
        val normalized = SmsSenderId.normalizeOrNull(senderId) ?: return senders
        return senders.filterNot { it.senderId == normalized }
    }

    fun sort(senders: List<AllowedSender>): List<AllowedSender> = senders.sortedWith(
        Comparator<AllowedSender> { first, second ->
            val byDisplayName = String.CASE_INSENSITIVE_ORDER.compare(
                first.displayName,
                second.displayName,
            )
            if (byDisplayName != 0) byDisplayName else first.senderId.compareTo(second.senderId)
        },
    )

    fun encodeAll(senders: List<AllowedSender>): Set<String> =
        senders.map { "${it.senderId}$ENTRY_SEPARATOR${it.displayName}" }.toSet()

    fun decodeAll(encoded: Set<String>): List<AllowedSender> = sort(
        encoded.mapNotNull { entry ->
            val parts = entry.split(ENTRY_SEPARATOR, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val validation = SmsSenderId.validateSender(parts[1])
            (validation as? SmsSenderValidation.Valid)
                ?.sender
                ?.takeIf { it.senderId == parts[0] }
        },
    )
}
