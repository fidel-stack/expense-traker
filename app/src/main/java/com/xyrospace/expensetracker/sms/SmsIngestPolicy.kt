package com.xyrospace.expensetracker.sms

/**
 * Metadata for one received SMS segment. It deliberately carries no body or PDU reference so
 * gating decisions can never read message content.
 */
data class SmsSegmentIdentity(
    val senderAddress: String?,
    val receivedAtMillis: Long,
)

enum class SmsIngestIgnoreReason {
    MONITORING_DISABLED,
    PERMISSION_NOT_GRANTED,
    NO_SEGMENTS,
    SENDER_MISSING,
    INCONSISTENT_SENDER,
    SENDER_NOT_ALLOWED,
}

sealed interface SmsIngestDecision {
    data class Eligible(
        val sender: AllowedSender,
        val receivedAtMillis: Long,
    ) : SmsIngestDecision

    data class Ignored(val reason: SmsIngestIgnoreReason) : SmsIngestDecision
}

/**
 * Pure decision step for an incoming SMS broadcast. Callers must run this before any message body
 * is read; the inputs contain sender metadata only.
 */
object SmsIngestPolicy {
    fun evaluate(
        monitoringEnabled: Boolean,
        permissionGranted: Boolean,
        allowedSenders: List<AllowedSender>,
        segments: List<SmsSegmentIdentity>,
    ): SmsIngestDecision {
        if (!monitoringEnabled) return SmsIngestDecision.Ignored(SmsIngestIgnoreReason.MONITORING_DISABLED)
        if (!permissionGranted) return SmsIngestDecision.Ignored(SmsIngestIgnoreReason.PERMISSION_NOT_GRANTED)
        if (segments.isEmpty()) return SmsIngestDecision.Ignored(SmsIngestIgnoreReason.NO_SEGMENTS)

        val normalizedSenders = LinkedHashSet<String>()
        for (segment in segments) {
            val normalized = SmsSenderId.normalizeOrNull(segment.senderAddress)
                ?: return SmsIngestDecision.Ignored(SmsIngestIgnoreReason.SENDER_MISSING)
            normalizedSenders += normalized
        }
        // A multipart message must come from exactly one sender; ambiguous segments are discarded.
        if (normalizedSenders.size != 1) {
            return SmsIngestDecision.Ignored(SmsIngestIgnoreReason.INCONSISTENT_SENDER)
        }

        val sender = SmsSenderFilter.findAllowedSender(normalizedSenders.single(), allowedSenders)
            ?: return SmsIngestDecision.Ignored(SmsIngestIgnoreReason.SENDER_NOT_ALLOWED)

        return SmsIngestDecision.Eligible(
            sender = sender,
            receivedAtMillis = segments.maxOf { it.receivedAtMillis },
        )
    }
}
