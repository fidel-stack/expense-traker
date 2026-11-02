package com.xyrospace.expensetracker.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.xyrospace.expensetracker.sms.AllowedSender
import com.xyrospace.expensetracker.sms.SmsAllowlist
import com.xyrospace.expensetracker.sms.SmsPermissionStatus
import com.xyrospace.expensetracker.ui.theme.ExpenseTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mpesa = AllowedSender(displayName = "MPESA", senderId = "MPESA")

    private fun state(
        status: SmsPermissionStatus = SmsPermissionStatus.NOT_GRANTED,
        monitoringPreference: Boolean = false,
        senders: List<AllowedSender> = listOf(mpesa),
        addSenderDialog: AddSenderDialogState? = null,
        pendingRemoval: AllowedSender? = null,
    ) = SettingsUiState(
        isLoading = false,
        monitoringPreferenceEnabled = monitoringPreference,
        permissionStatus = status,
        allowedSenders = senders,
        addSenderDialog = addSenderDialog,
        senderPendingRemoval = pendingRemoval,
    )

    private fun show(
        state: SettingsUiState,
        onMonitoringToggled: (Boolean) -> Unit = {},
        onAddSenderClick: () -> Unit = {},
        onAddSenderInputChange: (String) -> Unit = {},
        onAddSenderConfirm: () -> Unit = {},
        onAddSenderDismiss: () -> Unit = {},
        onRemoveSenderClick: (AllowedSender) -> Unit = {},
        onRemoveSenderConfirm: () -> Unit = {},
        onRemoveSenderDismiss: () -> Unit = {},
        onOpenAppSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            ExpenseTrackerTheme {
                SettingsContent(
                    state = state,
                    onMonitoringToggled = onMonitoringToggled,
                    onPrivacyDisclosureAccepted = {},
                    onPrivacyDisclosureDismissed = {},
                    onOpenAppSettings = onOpenAppSettings,
                    onPermissionBlockedDismissed = {},
                    onAddSenderClick = onAddSenderClick,
                    onAddSenderInputChange = onAddSenderInputChange,
                    onAddSenderConfirm = onAddSenderConfirm,
                    onAddSenderDismiss = onAddSenderDismiss,
                    onRemoveSenderClick = onRemoveSenderClick,
                    onRemoveSenderConfirm = onRemoveSenderConfirm,
                    onRemoveSenderDismiss = onRemoveSenderDismiss,
                )
            }
        }
    }

    @Test
    fun monitoringSwitchIsOffAndPermissionIsReportedSeparately() {
        show(state())

        composeRule.onNodeWithTag("sms_monitoring_switch").assertIsOff()
        composeRule.onNodeWithTag("sms_monitoring_status").assertExists()
        composeRule.onNodeWithText("Not granted").assertExists()
        composeRule.onNodeWithTag("sms_open_app_settings").assertExists()
    }

    @Test
    fun monitoringCannotBeActiveWhenPermissionIsNotGranted() {
        show(state(status = SmsPermissionStatus.NOT_GRANTED, monitoringPreference = true))

        composeRule.onNodeWithTag("sms_monitoring_switch").assertIsOff()
        composeRule.onNodeWithTag("sms_monitoring_status").assertExists()
    }

    @Test
    fun allowlistShowsConfiguredSenderWithRemoveAction() {
        show(state())

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("MPESA"))
        composeRule.onNodeWithTag("remove_sender_MPESA").assertExists()
    }

    @Test
    fun addSenderDialogAsksForConfirmationBeforeSaving() {
        var input by mutableStateOf("")
        var confirmed = false
        show(
            state = state(addSenderDialog = AddSenderDialogState(input)),
            onAddSenderInputChange = { input = it },
            onAddSenderConfirm = { confirmed = true },
        )

        composeRule.onNodeWithTag("add_sender_input").performTextInput("NEWBANK")
        composeRule.onNodeWithTag("add_sender_confirm").performClick()

        assertTrue(confirmed)
        assertEquals("NEWBANK", input)
    }

    @Test
    fun duplicateSenderMessageIsShownInTheDialog() {
        show(
            state(
                addSenderDialog = AddSenderDialogState(
                    input = "mpesa",
                    errorMessage = "This sender is already allowed.",
                ),
            ),
        )

        composeRule.onNodeWithText("This sender is already allowed.").assertExists()
    }

    @Test
    fun removeSenderRequiresConfirmationAndExplainsTheEffect() {
        var pendingRemoval by mutableStateOf<AllowedSender?>(null)
        var confirmed = false
        show(
            state = state(pendingRemoval = pendingRemoval),
            onRemoveSenderClick = { pendingRemoval = it },
            onRemoveSenderConfirm = { confirmed = true },
        )

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("MPESA"))
        composeRule.onNodeWithTag("remove_sender_MPESA").performClick()
        composeRule.onNodeWithTag("remove_sender_dialog").assertExists()
        assertFalse(confirmed)

        composeRule.onNodeWithText("Remove MPESA?").assertExists()
        composeRule.onNodeWithTag("remove_sender_confirm").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun privacyDisclosureExplainsLocalProcessingBeforePermissionRequest() {
        // The disclosure is owned by the view model; render that state directly to validate copy.
        show(state().copy(showPrivacyDisclosure = true))

        composeRule.onNodeWithTag("sms_privacy_dialog").assertExists()
        composeRule.onNodeWithText(
            "Raw message bodies are never stored, logged, or shared.",
        ).assertExists()
        composeRule.onNodeWithTag("sms_permission_continue").assertExists()
    }

    @Test
    fun defaultsProvideSeventeenSenderRows() {
        show(state(senders = SmsAllowlist.defaultSenders()))
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Stanbic"))
        composeRule.onNodeWithTag("sender_id_STANBIC").assertExists()
    }
}
