package com.xyrospace.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives new incoming SMS notifications. The receiver itself performs no filtering decisions:
 * it validates the broadcast action, decodes metadata with the platform API and hands the message
 * to [SmsMonitoringCoordinator], which gates on preference, permission and sender allowlist before
 * any content is touched.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: SmsMonitoringCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val broadcast = SmsBroadcastReader.read(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                coordinator.process(broadcast)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Discard unsafe or malformed messages without exposing their content.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
