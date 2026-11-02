package com.xyrospace.expensetracker.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsIngestPolicyTest {

    private val allowedSenders = SmsAllowlist.defaultSenders()

    private fun identity(sender: String?, timestamp: Long = 1_700_000_000_000L) =
        SmsSegmentIdentity(senderAddress = sender, receivedAtMillis = timestamp)

    private fun reasonOf(decision: SmsIngestDecision): SmsIngestIgnoreReason {
        assertTrue("Expected the message to be ignored, was $decision", decision is SmsIngestDecision.Ignored)
        return (decision as SmsIngestDecision.Ignored).reason
    }

    @Test
    fun disabledMonitoringIgnoresAllowedSenders() {
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = false,
            permissionGranted = true,
            allowedSenders = allowedSenders,
            segments = listOf(identity("MPESA")),
        )
        assertEquals(SmsIngestIgnoreReason.MONITORING_DISABLED, reasonOf(decision))
    }

    @Test
    fun missingPermissionIgnoresMessagesEvenWhenPreferenceIsEnabled() {
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = true,
            permissionGranted = false,
            allowedSenders = allowedSenders,
            segments = listOf(identity("MPESA")),
        )
        assertEquals(SmsIngestIgnoreReason.PERMISSION_NOT_GRANTED, reasonOf(decision))
    }

    @Test
    fun unapprovedSendersAreIgnored() {
        listOf("MPESA-ALERT", "FAKE-MPESA", "UNKNOWNBANK").forEach { sender ->
            val decision = SmsIngestPolicy.evaluate(
                monitoringEnabled = true,
                permissionGranted = true,
                allowedSenders = allowedSenders,
                segments = listOf(identity(sender)),
            )
            assertEquals(sender, SmsIngestIgnoreReason.SENDER_NOT_ALLOWED, reasonOf(decision))
        }
    }

    @Test
    fun missingSendersAreIgnored() {
        listOf(null, "", "   ").forEach { sender ->
            val decision = SmsIngestPolicy.evaluate(
                monitoringEnabled = true,
                permissionGranted = true,
                allowedSenders = allowedSenders,
                segments = listOf(identity(sender)),
            )
            assertEquals(SmsIngestIgnoreReason.SENDER_MISSING, reasonOf(decision))
        }
    }

    @Test
    fun emptyBroadcastIsIgnored() {
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = true,
            permissionGranted = true,
            allowedSenders = allowedSenders,
            segments = emptyList(),
        )
        assertEquals(SmsIngestIgnoreReason.NO_SEGMENTS, reasonOf(decision))
    }

    @Test
    fun multipartSegmentsFromDifferentSendersAreDiscarded() {
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = true,
            permissionGranted = true,
            allowedSenders = allowedSenders,
            segments = listOf(identity("MPESA"), identity("KCB")),
        )
        assertEquals(SmsIngestIgnoreReason.INCONSISTENT_SENDER, reasonOf(decision))
    }

    @Test
    fun allowedMultipartMessageReportsOneSenderAndLatestTimestamp() {
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = true,
            permissionGranted = true,
            allowedSenders = allowedSenders,
            segments = listOf(
                identity(" mpesa ", timestamp = 1_000L),
                identity("MPESA", timestamp = 5_000L),
            ),
        )

        assertTrue(decision is SmsIngestDecision.Eligible)
        val eligible = decision as SmsIngestDecision.Eligible
        assertEquals("MPESA", eligible.sender.senderId)
        assertEquals(5_000L, eligible.receivedAtMillis)
    }

    @Test
    fun removedSenderIsRejectedAfterAllowlistChange() {
        val withoutMpesa = SmsAllowlist.removeSender(allowedSenders, "MPESA")
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = true,
            permissionGranted = true,
            allowedSenders = withoutMpesa,
            segments = listOf(identity("MPESA")),
        )
        assertEquals(SmsIngestIgnoreReason.SENDER_NOT_ALLOWED, reasonOf(decision))
    }
}
