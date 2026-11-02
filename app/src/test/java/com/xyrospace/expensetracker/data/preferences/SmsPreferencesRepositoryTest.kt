package com.xyrospace.expensetracker.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xyrospace.expensetracker.sms.SmsAllowlist
import com.xyrospace.expensetracker.sms.SmsSenderValidationError
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SmsPreferencesRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: SmsPreferencesRepository

    @Before
    fun setUp() {
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

    @Test
    fun firstReadSeedsDefaultsAndShipsDisabled() = runBlocking {
        val state = repository.state.first()

        assertEquals(17, state.allowedSenders.size)
        assertEquals(17, state.allowedSenders.map { it.senderId }.toSet().size)
        assertFalse(state.monitoringEnabled)
        assertFalse(state.permissionRequested)
    }

    @Test
    fun removingDefaultsIsNotUndoneByLaterReads() = runBlocking {
        repository.removeSender("MPESA")

        assertTrue(repository.state.first().allowedSenders.none { it.senderId == "MPESA" })
        // Re-collecting represents a later launch; seeding must not restore removed senders.
        assertTrue(repository.state.first().allowedSenders.none { it.senderId == "MPESA" })
        assertEquals(16, repository.state.first().allowedSenders.size)
    }

    @Test
    fun addedSenderPersistsAndDuplicatesAreRejected() = runBlocking {
        assertEquals(SmsSenderAddResult.Added, repository.addSender("  NEWBANK  "))

        val added = repository.state.first().allowedSenders.first { it.senderId == "NEWBANK" }
        assertEquals("NEWBANK", added.displayName)
        assertEquals(SmsSenderAddResult.Duplicate, repository.addSender("newbank"))
        assertEquals(18, repository.state.first().allowedSenders.size)
    }

    @Test
    fun invalidSendersAreRejectedWithoutChangingTheAllowlist() = runBlocking {
        assertEquals(
            SmsSenderAddResult.Rejected(SmsSenderValidationError.EMPTY),
            repository.addSender("   "),
        )
        assertEquals(
            SmsSenderAddResult.Rejected(SmsSenderValidationError.WILDCARD_PATTERN),
            repository.addSender("MP*"),
        )
        assertEquals(17, repository.state.first().allowedSenders.size)
    }

    @Test
    fun intentionallyEmptyAllowlistStaysEmpty() = runBlocking {
        SmsAllowlist.defaultSenders().forEach { repository.removeSender(it.senderId) }

        assertTrue(repository.state.first().allowedSenders.isEmpty())
        assertTrue(repository.state.first().allowedSenders.isEmpty())
    }

    @Test
    fun monitoringPreferenceAndPermissionMarkerPersist() = runBlocking {
        repository.setMonitoringEnabled(true)
        repository.markPermissionRequested()

        val state = repository.state.first()
        assertTrue(state.monitoringEnabled)
        assertTrue(state.permissionRequested)
    }

    @Test
    fun disablingMonitoringPersists() = runBlocking {
        repository.setMonitoringEnabled(true)
        repository.setMonitoringEnabled(false)

        assertFalse(repository.state.first().monitoringEnabled)
    }
}
