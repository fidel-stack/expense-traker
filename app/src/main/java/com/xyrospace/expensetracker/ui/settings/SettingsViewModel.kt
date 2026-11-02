package com.xyrospace.expensetracker.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xyrospace.expensetracker.data.preferences.SmsPreferencesRepository
import com.xyrospace.expensetracker.data.preferences.SmsSenderAddResult
import com.xyrospace.expensetracker.sms.AllowedSender
import com.xyrospace.expensetracker.sms.SmsPermissionManager
import com.xyrospace.expensetracker.sms.SmsPermissionStatus
import com.xyrospace.expensetracker.sms.SmsSenderId
import com.xyrospace.expensetracker.sms.SmsSenderValidationError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddSenderDialogState(
    val input: String = "",
    val errorMessage: String? = null,
)

data class SettingsUiState(
    val isLoading: Boolean = true,
    val monitoringPreferenceEnabled: Boolean = false,
    val permissionStatus: SmsPermissionStatus = SmsPermissionStatus.NOT_GRANTED,
    val permissionRequested: Boolean = false,
    val allowedSenders: List<AllowedSender> = emptyList(),
    val showPrivacyDisclosure: Boolean = false,
    val showPermissionBlockedDialog: Boolean = false,
    val addSenderDialog: AddSenderDialogState? = null,
    val senderPendingRemoval: AllowedSender? = null,
    val errorMessage: String? = null,
) {
    /**
     * Effective tracking state: the persisted opt-in never substitutes for the live Android
     * permission, so a revoked permission turns tracking off in the UI and in the receiver.
     */
    val isMonitoringActive: Boolean
        get() = monitoringPreferenceEnabled && permissionStatus == SmsPermissionStatus.GRANTED

    val permissionStatusLabel: String
        get() = when (permissionStatus) {
            SmsPermissionStatus.GRANTED -> "Granted"
            SmsPermissionStatus.NOT_GRANTED -> "Not granted"
            SmsPermissionStatus.DENIED_PERMANENTLY -> "Blocked by Android"
            SmsPermissionStatus.RESTRICTED -> "Restricted"
        }

    val canOpenAppSettings: Boolean
        get() = permissionStatus == SmsPermissionStatus.NOT_GRANTED ||
            permissionStatus == SmsPermissionStatus.DENIED_PERMANENTLY ||
            permissionStatus == SmsPermissionStatus.RESTRICTED
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: SmsPreferencesRepository,
    private val permissionManager: SmsPermissionManager,
) : ViewModel() {

    private data class DialogState(
        val showPrivacyDisclosure: Boolean = false,
        val showPermissionBlocked: Boolean = false,
        val addSender: AddSenderDialogState? = null,
        val pendingRemoval: AllowedSender? = null,
    )

    private val permissionStatus = MutableStateFlow(SmsPermissionStatus.NOT_GRANTED)
    private val dialogs = MutableStateFlow(DialogState())
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.state,
        permissionStatus,
        dialogs,
        errorMessage,
    ) { preferences, status, dialog, error ->
        SettingsUiState(
            isLoading = false,
            monitoringPreferenceEnabled = preferences.monitoringEnabled,
            permissionStatus = status,
            permissionRequested = preferences.permissionRequested,
            allowedSenders = preferences.allowedSenders,
            showPrivacyDisclosure = dialog.showPrivacyDisclosure,
            showPermissionBlockedDialog = dialog.showPermissionBlocked,
            addSenderDialog = dialog.addSender,
            senderPendingRemoval = dialog.pendingRemoval,
            errorMessage = error,
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(SettingsUiState(isLoading = false, errorMessage = "Couldn't load SMS settings."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        refreshPermissionStatus(activity = null)
    }

    /** Re-reads Android's permission state; called on resume so Settings changes are reflected. */
    fun refreshPermissionStatus(activity: Activity?) {
        viewModelScope.launch {
            val requestAttempted = try {
                preferencesRepository.state.first().permissionRequested
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            permissionStatus.value = permissionManager.status(activity, requestAttempted)
        }
    }

    fun onMonitoringToggled(requested: Boolean) {
        if (!requested) {
            setMonitoringPreference(false)
            return
        }
        when (uiState.value.permissionStatus) {
            SmsPermissionStatus.GRANTED -> setMonitoringPreference(true)
            SmsPermissionStatus.NOT_GRANTED -> dialogs.update { it.copy(showPrivacyDisclosure = true) }
            // The system will not show a dialog again; point the user at app settings instead.
            SmsPermissionStatus.DENIED_PERMANENTLY,
            SmsPermissionStatus.RESTRICTED,
            -> dialogs.update { it.copy(showPermissionBlocked = true) }
        }
    }

    fun onPrivacyDisclosureAccepted() {
        dialogs.update { it.copy(showPrivacyDisclosure = false) }
        viewModelScope.launch {
            try {
                preferencesRepository.markPermissionRequested()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorMessage.value = "Couldn't update SMS settings. Please try again."
            }
        }
    }

    fun dismissPrivacyDisclosure() {
        dialogs.update { it.copy(showPrivacyDisclosure = false) }
    }

    fun onPermissionResult(granted: Boolean, activity: Activity?) {
        viewModelScope.launch {
            try {
                preferencesRepository.markPermissionRequested()
                if (granted) preferencesRepository.setMonitoringEnabled(true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorMessage.value = "Couldn't update SMS tracking. Please try again."
            }
            permissionStatus.value = permissionManager.status(activity, requestAttempted = true)
        }
    }

    fun dismissPermissionBlockedDialog() {
        dialogs.update { it.copy(showPermissionBlocked = false) }
    }

    fun appSettingsIntent(): Intent = permissionManager.appSettingsIntent()

    fun onAddSenderClick() {
        dialogs.update { it.copy(addSender = AddSenderDialogState()) }
    }

    fun onAddSenderInputChange(input: String) {
        dialogs.update { current ->
            val dialog = current.addSender ?: return@update current
            current.copy(addSender = dialog.copy(input = input, errorMessage = null))
        }
    }

    fun dismissAddSenderDialog() {
        dialogs.update { it.copy(addSender = null) }
    }

    fun confirmAddSender() {
        val input = dialogs.value.addSender?.input ?: return
        viewModelScope.launch {
            try {
                when (val result = preferencesRepository.addSender(input)) {
                    SmsSenderAddResult.Added -> dialogs.update { it.copy(addSender = null) }
                    SmsSenderAddResult.Duplicate -> dialogs.update { current ->
                        current.copy(
                            addSender = current.addSender?.copy(
                                errorMessage = "This sender is already allowed.",
                            ),
                        )
                    }
                    is SmsSenderAddResult.Rejected -> dialogs.update { current ->
                        current.copy(
                            addSender = current.addSender?.copy(
                                errorMessage = result.reason.userMessage(),
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                dialogs.update { current ->
                    current.copy(
                        addSender = current.addSender?.copy(
                            errorMessage = "Couldn't save this sender. Please try again.",
                        ),
                    )
                }
            }
        }
    }

    fun onRemoveSenderClick(sender: AllowedSender) {
        dialogs.update { it.copy(pendingRemoval = sender) }
    }

    fun dismissRemoveSenderDialog() {
        dialogs.update { it.copy(pendingRemoval = null) }
    }

    fun confirmRemoveSender() {
        val sender = dialogs.value.pendingRemoval ?: return
        viewModelScope.launch {
            try {
                preferencesRepository.removeSender(sender.senderId)
                dialogs.update { it.copy(pendingRemoval = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                dialogs.update { it.copy(pendingRemoval = null) }
                errorMessage.value = "Couldn't remove this sender. Please try again."
            }
        }
    }

    private fun setMonitoringPreference(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesRepository.setMonitoringEnabled(enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorMessage.value = "Couldn't update SMS tracking. Please try again."
            }
        }
    }
}

private fun SmsSenderValidationError.userMessage(): String = when (this) {
    SmsSenderValidationError.EMPTY -> "Enter a sender ID."
    SmsSenderValidationError.TOO_LONG ->
        "Sender IDs can be at most ${SmsSenderId.MAX_LENGTH} characters."
    SmsSenderValidationError.WILDCARD_PATTERN ->
        "Wildcards aren't allowed. Enter the exact sender ID."
    SmsSenderValidationError.INVALID_CHARACTERS ->
        "Use letters, numbers, spaces, or - _ . & + ' / only."
}
