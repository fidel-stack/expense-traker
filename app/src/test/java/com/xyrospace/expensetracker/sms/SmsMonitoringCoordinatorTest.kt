package com.xyrospace.expensetracker.sms

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xyrospace.expensetracker.data.preferences.SmsPreferencesRepository
import com.xyrospace.expensetracker.data.preferences.SmsSenderAddResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the privacy boundary: bodies must never be read for ignored messages, and the handler
 * is invoked at most once per broadcast. Synthetic text only; no real financial messages.
 */
class SmsMonitoringCoordinatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: SmsPreferencesRepository
    private var bodiesRead = 0

    @Before
    fun setUp() {
        bodiesRead = 0
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = SmsPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                File(temporaryFolder.root, "sms_preferences.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    private fun coordinator(
        permissionGranted: Boolean = true,
        handler: SmsMessageHandler,
    ) = SmsMonitoringCoordinator(
        preferencesRepository = repository,
        permissionChecker = SmsPermissionChecker { permissionGranted },
        messageHandler = handler,
    )

    private fun broadcast(sender: String?, vararg bodies: String): IncomingSmsBroadcast =
        IncomingSmsBroadcast(
            bodies.map { body ->
                IncomingSmsSegment(
                    senderAddress = sender,
                    receivedAtMillis = 1_700_000_000_000L,
                    bodyReader = {
                        bodiesRead += 1
                        body
                    },
                )
            },
        )

    @Test
    fun disabledMonitoringIgnoresMessagesAndNeverReadsBodies() = runBlocking {
        val handler = RecordingHandler()
        coordinator(handler = handler).process(broadcast("MPESA", "synthetic message"))

        assertEquals(0, handler.messages.size)
        assertEquals(0, bodiesRead)
    }

    @Test
    fun missingPermissionStopsProcessingEvenIfPreferenceIsOn() = runBlocking {
        repository.setMonitoringEnabled(true)
        val handler = RecordingHandler()
        coordinator(permissionGranted = false, handler = handler)
            .process(broadcast("MPESA", "synthetic message"))

        assertEquals(0, handler.messages.size)
        assertEquals(0, bodiesRead)
    }

    @Test
    fun unapprovedSenderNeverReachesHandlerOrMessageBody() = runBlocking {
        repository.setMonitoringEnabled(true)
        val handler = RecordingHandler()
        coordinator(handler = handler).process(broadcast("MPESA-ALERT", "synthetic message"))

        assertEquals(0, handler.messages.size)
        assertEquals(0, bodiesRead)
    }

    @Test
    fun multipartEligibleMessageIsAcknowledgedOnceWithoutReadingContent() = runBlocking {
        repository.setMonitoringEnabled(true)
        val handler = RecordingHandler()
        coordinator(handler = handler)
            .process(broadcast("mpesa", "part one ", "part two ", "part three"))

        assertEquals(1, handler.messages.size)
        assertEquals("MPESA", handler.messages.single().sender.senderId)
        // The Stage 5 handler discards the message, so the body is never materialized.
        assertEquals(0, bodiesRead)
    }

    @Test
    fun inconsistentMultipartSenderIsDiscardedWithoutReadingContent() = runBlocking {
        repository.setMonitoringEnabled(true)
        val handler = RecordingHandler()
        val multipart = IncomingSmsBroadcast(
            listOf(
                IncomingSmsSegment("MPESA", 1_000L) { bodiesRead += 1; "part one" },
                IncomingSmsSegment("KCB", 1_000L) { bodiesRead += 1; "part two" },
            ),
        )

        coordinator(handler = handler).process(multipart)

        assertEquals(0, handler.messages.size)
        assertEquals(0, bodiesRead)
    }

    @Test
    fun allowlistChangesApplyWithoutRestart() = runBlocking {
        repository.setMonitoringEnabled(true)
        val handler = RecordingHandler()
        val coordinator = coordinator(handler = handler)

        coordinator.process(broadcast("NEWBANK", "synthetic message"))
        assertEquals(0, handler.messages.size)

        assertEquals(SmsSenderAddResult.Added, repository.addSender("NEWBANK"))
        coordinator.process(broadcast("newbank", "synthetic message"))

        assertEquals(1, handler.messages.size)
        assertEquals("NEWBANK", handler.messages.single().sender.senderId)
        assertEquals(0, bodiesRead)
    }

    @Test
    fun removingSenderStopsProcessingImmediately() = runBlocking {
        repository.setMonitoringEnabled(true)
        repository.removeSender("MPESA")
        val handler = RecordingHandler()

        coordinator(handler = handler).process(broadcast("MPESA", "synthetic message"))

        assertEquals(0, handler.messages.size)
        assertEquals(0, bodiesRead)
    }

    private class RecordingHandler : SmsMessageHandler {
        val messages = mutableListOf<EligibleSmsMessage>()

        override suspend fun onEligibleMessage(message: EligibleSmsMessage) {
            messages += message
        }
    }
}
