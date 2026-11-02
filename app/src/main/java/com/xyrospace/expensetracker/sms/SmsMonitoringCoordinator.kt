package com.xyrospace.expensetracker.sms

import com.xyrospace.expensetracker.data.preferences.SmsPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Orchestrates monitoring preference, live permission state, sender allowlisting and the future
 * parser boundary. Preferences are read from DataStore for every broadcast so disabling tracking,
 * revoking permission or removing a sender takes effect immediately without a restart.
 */
@Singleton
class SmsMonitoringCoordinator @Inject constructor(
    private val preferencesRepository: SmsPreferencesRepository,
    private val permissionChecker: SmsPermissionChecker,
    private val messageHandler: SmsMessageHandler,
) {
    suspend fun process(broadcast: IncomingSmsBroadcast) {
        val preferences = preferencesRepository.state.first()
        val decision = SmsIngestPolicy.evaluate(
            monitoringEnabled = preferences.monitoringEnabled,
            permissionGranted = permissionChecker.isGranted(),
            allowedSenders = preferences.allowedSenders,
            segments = broadcast.identities,
        )
        if (decision !is SmsIngestDecision.Eligible) return

        messageHandler.onEligibleMessage(
            EligibleSmsMessage(
                sender = decision.sender,
                receivedAtMillis = decision.receivedAtMillis,
                bodyReader = broadcast::readJoinedBody,
            ),
        )
    }
}
