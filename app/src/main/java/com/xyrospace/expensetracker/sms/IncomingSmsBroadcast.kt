package com.xyrospace.expensetracker.sms

/**
 * One decoded SMS segment. The body is exposed only through [IncomingSmsSegment.readBody], which
 * is not called anywhere before the sender passes the allowlist check.
 */
class IncomingSmsSegment(
    val senderAddress: String?,
    val receivedAtMillis: Long,
    private val bodyReader: () -> String?,
) {
    fun readBody(): String? = bodyReader()
}

/**
 * A single `SMS_RECEIVED` broadcast. A multipart message arrives as several segments; they are
 * processed as one unit so segments are never handled more than once.
 */
class IncomingSmsBroadcast(private val segments: List<IncomingSmsSegment>) {
    val identities: List<SmsSegmentIdentity> = segments.map {
        SmsSegmentIdentity(senderAddress = it.senderAddress, receivedAtMillis = it.receivedAtMillis)
    }

    /** Reconstructed content stays in memory and is never persisted. */
    fun readJoinedBody(): String = segments.joinToString(separator = "") { it.readBody().orEmpty() }
}
