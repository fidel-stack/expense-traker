package com.xyrospace.expensetracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.xyrospace.expensetracker.sms.AllowedSender
import com.xyrospace.expensetracker.sms.SmsAllowlist
import com.xyrospace.expensetracker.sms.SmsSenderId
import com.xyrospace.expensetracker.sms.SmsSenderValidation
import com.xyrospace.expensetracker.sms.SmsSenderValidationError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class SmsPreferencesState(
    val monitoringEnabled: Boolean = false,
    val permissionRequested: Boolean = false,
    val allowedSenders: List<AllowedSender> = emptyList(),
)

sealed interface SmsSenderAddResult {
    data object Added : SmsSenderAddResult
    data object Duplicate : SmsSenderAddResult
    data class Rejected(val reason: SmsSenderValidationError) : SmsSenderAddResult
}

/**
 * Local, on-device SMS preferences. Only the monitoring opt-in, the permission-request marker and
 * the sender allowlist are stored; message content is never written here.
 *
 * The allowlist is seeded exactly once (guarded by [Keys.AllowlistInitialized]), so later launches
 * never restore senders the user removed and an intentionally empty allowlist stays empty.
 */
@Singleton
class SmsPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val MonitoringEnabled = booleanPreferencesKey("sms_monitoring_enabled")
        val PermissionRequested = booleanPreferencesKey("sms_permission_requested")
        val AllowlistInitialized = booleanPreferencesKey("sms_allowlist_initialized")
        val AllowedSenders = stringSetPreferencesKey("sms_allowed_senders")
    }

    /** Cold: guarantees one-time initialization, then reflects persisted preferences. */
    val state: Flow<SmsPreferencesState> = flow {
        ensureAllowlistInitialized()
        emitAll(dataStore.data.map(::toState))
    }.distinctUntilChanged()

    suspend fun ensureAllowlistInitialized() {
        // Avoid a disk write on every collected message once seeding has happened.
        if (dataStore.data.first()[Keys.AllowlistInitialized] == true) return
        dataStore.edit { preferences ->
            if (preferences[Keys.AllowlistInitialized] == true) return@edit
            preferences[Keys.AllowedSenders] =
                SmsAllowlist.encodeAll(SmsAllowlist.defaultSenders())
            preferences[Keys.AllowlistInitialized] = true
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.MonitoringEnabled] = enabled }
    }

    suspend fun markPermissionRequested() {
        dataStore.edit { preferences -> preferences[Keys.PermissionRequested] = true }
    }

    /** Validates, trims and de-duplicates by normalized identity before persisting. */
    suspend fun addSender(rawSenderId: String): SmsSenderAddResult {
        val validation = SmsSenderId.validateSender(rawSenderId)
        if (validation is SmsSenderValidation.Invalid) {
            return SmsSenderAddResult.Rejected(validation.error)
        }
        val candidate = (validation as SmsSenderValidation.Valid).sender
        ensureAllowlistInitialized()

        var duplicate = false
        dataStore.edit { preferences ->
            val current = SmsAllowlist.decodeAll(preferences[Keys.AllowedSenders].orEmpty())
            if (SmsAllowlist.containsSender(current, candidate.senderId)) {
                duplicate = true
                return@edit
            }
            preferences[Keys.AllowedSenders] =
                SmsAllowlist.encodeAll(SmsAllowlist.addSender(current, candidate))
        }
        return if (duplicate) SmsSenderAddResult.Duplicate else SmsSenderAddResult.Added
    }

    suspend fun removeSender(senderId: String) {
        ensureAllowlistInitialized()
        dataStore.edit { preferences ->
            val current = SmsAllowlist.decodeAll(preferences[Keys.AllowedSenders].orEmpty())
            preferences[Keys.AllowedSenders] =
                SmsAllowlist.encodeAll(SmsAllowlist.removeSender(current, senderId))
        }
    }

    private fun toState(preferences: Preferences): SmsPreferencesState {
        val initialized = preferences[Keys.AllowlistInitialized] == true
        val senders = if (initialized) {
            SmsAllowlist.decodeAll(preferences[Keys.AllowedSenders].orEmpty())
        } else {
            SmsAllowlist.defaultSenders()
        }
        return SmsPreferencesState(
            monitoringEnabled = preferences[Keys.MonitoringEnabled] == true,
            permissionRequested = preferences[Keys.PermissionRequested] == true,
            allowedSenders = senders,
        )
    }
}
