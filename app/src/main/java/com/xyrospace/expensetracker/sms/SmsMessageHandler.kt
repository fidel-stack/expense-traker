package com.xyrospace.expensetracker.sms

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A message that passed monitoring, permission and allowlist checks.
 *
 * [readBody] is the only supported way to access content. Stage 5 never calls it: the placeholder
 * handler discards eligible messages until a parser stage is added in the future.
 */
class EligibleSmsMessage internal constructor(
    val sender: AllowedSender,
    val receivedAtMillis: Long,
    private val bodyReader: () -> String,
) {
    fun readBody(): String = bodyReader()
}

/** Integration point for the future on-device financial message parser. */
interface SmsMessageHandler {
    suspend fun onEligibleMessage(message: EligibleSmsMessage)
}

/**
 * Stage 5 acknowledgement handler: it intentionally ignores the message, so no raw SMS content is
 * read, logged or persisted. A later stage binds a parser implementation here without changing the
 * receiver, permission or allowlist architecture.
 */
@Singleton
class DiscardingSmsMessageHandler @Inject constructor() : SmsMessageHandler {
    override suspend fun onEligibleMessage(message: EligibleSmsMessage) = Unit
}
