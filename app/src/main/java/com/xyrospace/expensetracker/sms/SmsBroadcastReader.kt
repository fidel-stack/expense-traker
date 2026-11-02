package com.xyrospace.expensetracker.sms

import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

/**
 * Turns an Android SMS broadcast into metadata-only segments using the platform SMS decoder.
 *
 * `Telephony.Sms.Intents.getMessagesFromIntent` is used instead of a hand-written PDU parser. Only
 * originating address and timestamp are read here; `messageBody` is captured in a lazily invoked
 * lambda and is therefore never accessed during reading or sender validation.
 */
internal object SmsBroadcastReader {
    fun read(intent: Intent?): IncomingSmsBroadcast? {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return null

        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (_: Exception) {
            // A malformed broadcast is discarded; no PDU content is logged or retained.
            return null
        } ?: return null

        val segments = messages.filterNotNull().map { message ->
            IncomingSmsSegment(
                senderAddress = message.originatingAddress ?: message.displayOriginatingAddress,
                receivedAtMillis = message.timestampMillis,
                bodyReader = { runCatching { message.messageBody }.getOrNull() },
            )
        }
        if (segments.isEmpty()) return null
        return IncomingSmsBroadcast(segments)
    }
}
