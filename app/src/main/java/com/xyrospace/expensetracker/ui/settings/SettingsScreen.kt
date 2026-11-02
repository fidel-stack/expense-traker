package com.xyrospace.expensetracker.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xyrospace.expensetracker.sms.AllowedSender
import com.xyrospace.expensetracker.sms.SmsPermissionStatus

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onPermissionResult(granted, activity)
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsContent(
        state = state,
        onMonitoringToggled = viewModel::onMonitoringToggled,
        onPrivacyDisclosureAccepted = {
            viewModel.onPrivacyDisclosureAccepted()
            permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        },
        onPrivacyDisclosureDismissed = viewModel::dismissPrivacyDisclosure,
        onOpenAppSettings = {
            viewModel.dismissPermissionBlockedDialog()
            context.startActivity(viewModel.appSettingsIntent())
        },
        onPermissionBlockedDismissed = viewModel::dismissPermissionBlockedDialog,
        onAddSenderClick = viewModel::onAddSenderClick,
        onAddSenderInputChange = viewModel::onAddSenderInputChange,
        onAddSenderConfirm = viewModel::confirmAddSender,
        onAddSenderDismiss = viewModel::dismissAddSenderDialog,
        onRemoveSenderClick = viewModel::onRemoveSenderClick,
        onRemoveSenderConfirm = viewModel::confirmRemoveSender,
        onRemoveSenderDismiss = viewModel::dismissRemoveSenderDialog,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onMonitoringToggled: (Boolean) -> Unit,
    onPrivacyDisclosureAccepted: () -> Unit,
    onPrivacyDisclosureDismissed: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPermissionBlockedDismissed: () -> Unit,
    onAddSenderClick: () -> Unit,
    onAddSenderInputChange: (String) -> Unit,
    onAddSenderConfirm: () -> Unit,
    onAddSenderDismiss: () -> Unit,
    onRemoveSenderClick: (AllowedSender) -> Unit,
    onRemoveSenderConfirm: () -> Unit,
    onRemoveSenderDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().testTag("screen_settings")) {
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("settings_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                state.errorMessage?.let { message ->
                    item { ErrorCard(message) }
                }
                item { SectionHeader("SMS Tracking") }
                item {
                    SmsTrackingCard(
                        state = state,
                        onMonitoringToggled = onMonitoringToggled,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }
                item { PrivacyCard() }
                item { SectionHeader("Allowed SMS Senders") }
                item {
                    Text(
                        text = "Manage which institutions can have their messages processed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.allowedSenders.isEmpty()) {
                    item { EmptyAllowlistCard() }
                }
                items(state.allowedSenders, key = { it.senderId }) { sender ->
                    SenderRow(sender = sender, onRemove = { onRemoveSenderClick(sender) })
                }
                item {
                    OutlinedButton(
                        onClick = onAddSenderClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_sender_button"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Sender")
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    state.addSenderDialog?.let { dialog ->
        AddSenderDialog(
            dialog = dialog,
            onInputChange = onAddSenderInputChange,
            onConfirm = onAddSenderConfirm,
            onDismiss = onAddSenderDismiss,
        )
    }
    state.senderPendingRemoval?.let { sender ->
        RemoveSenderDialog(
            sender = sender,
            onConfirm = onRemoveSenderConfirm,
            onDismiss = onRemoveSenderDismiss,
        )
    }
    if (state.showPrivacyDisclosure) {
        PrivacyDisclosureDialog(
            onAccept = onPrivacyDisclosureAccepted,
            onDismiss = onPrivacyDisclosureDismissed,
        )
    }
    if (state.showPermissionBlockedDialog) {
        PermissionBlockedDialog(
            onOpenSettings = onOpenAppSettings,
            onDismiss = onPermissionBlockedDismissed,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SmsTrackingCard(
    state: SettingsUiState,
    onMonitoringToggled: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Automatic SMS Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.isMonitoringActive) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.isMonitoringActive) {
                            activeColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag("sms_monitoring_status"),
                    )
                }
                Switch(
                    checked = state.isMonitoringActive,
                    onCheckedChange = onMonitoringToggled,
                    modifier = Modifier.testTag("sms_monitoring_switch"),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Automatically identify supported M-Pesa and bank transaction messages. " +
                    "Only approved senders are processed. Your SMS messages remain private and " +
                    "are never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Permission status",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.permissionStatusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = permissionStatusColor(state.permissionStatus),
                    modifier = Modifier.testTag("sms_permission_status"),
                )
            }
            PermissionExplanation(state = state, onOpenAppSettings = onOpenAppSettings)
        }
    }
}

@Composable
private fun permissionStatusColor(status: SmsPermissionStatus) = when (status) {
    SmsPermissionStatus.GRANTED -> MaterialTheme.colorScheme.primary
    SmsPermissionStatus.NOT_GRANTED -> MaterialTheme.colorScheme.onSurfaceVariant
    SmsPermissionStatus.DENIED_PERMANENTLY,
    SmsPermissionStatus.RESTRICTED,
    -> MaterialTheme.colorScheme.error
}

@Composable
private fun PermissionExplanation(
    state: SettingsUiState,
    onOpenAppSettings: () -> Unit,
) {
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    when (state.permissionStatus) {
        SmsPermissionStatus.GRANTED -> Text(
            text = "SMS access is allowed. Tracking runs only while the switch above is on.",
            style = MaterialTheme.typography.bodySmall,
            color = bodyColor,
        )
        SmsPermissionStatus.NOT_GRANTED -> {
            Text(
                text = if (state.permissionRequested) {
                    "SMS access wasn't granted. You can try again with the switch above or allow " +
                        "it in Android settings. Manual entry keeps working."
                } else {
                    "SMS access isn't granted. Manual entry works without it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor,
            )
            AppSettingsButton(onOpenAppSettings)
        }
        SmsPermissionStatus.DENIED_PERMANENTLY -> {
            Text(
                text = "SMS access is blocked by Android, so the permission dialog can no longer " +
                    "be shown. Allow SMS permission in app settings to turn on tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor,
            )
            AppSettingsButton(onOpenAppSettings)
        }
        SmsPermissionStatus.RESTRICTED -> {
            Text(
                text = "SMS access is restricted on this device or by your installer. Automatic " +
                    "tracking can't be enabled here; manual entry keeps working.",
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor,
            )
            AppSettingsButton(onOpenAppSettings)
        }
    }
}

@Composable
private fun AppSettingsButton(onOpenAppSettings: () -> Unit) {
    TextButton(
        onClick = onOpenAppSettings,
        modifier = Modifier.testTag("sms_open_app_settings"),
    ) {
        Text("Open app settings")
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Only messages from allowed senders are eligible for processing. " +
                        "Message contents are processed on your device and are never stored in " +
                        "their original form or uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyAllowlistCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(
            text = "No senders are allowed. Messages from every sender will be ignored until you " +
                "add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun SenderRow(sender: AllowedSender, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = senderIcon(sender.senderId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sender.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sender.displayName != sender.senderId) {
                Text(
                    text = sender.senderId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("sender_id_${sender.senderId}"),
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag("remove_sender_${sender.senderId}"),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove ${sender.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
        )
    }
}

@Composable
private fun AddSenderDialog(
    dialog: AddSenderDialogState,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val error = dialog.errorMessage
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_sender_dialog"),
        title = { Text("Add sender") },
        text = {
            Column {
                Text(
                    text = "Enter the exact sender ID shown on messages from this institution.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dialog.input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    isError = error != null,
                    label = { Text("Sender ID") },
                    placeholder = { Text("e.g. MPESA") },
                    supportingText = error?.let { message -> { Text(message) } },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_sender_input"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("add_sender_confirm"),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RemoveSenderDialog(
    sender: AllowedSender,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("remove_sender_dialog"),
        title = { Text("Remove ${sender.displayName}?") },
        text = {
            Text(
                "Future messages from ${sender.displayName} will be ignored. Transactions you " +
                    "have already saved are not affected.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("remove_sender_confirm"),
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PrivacyDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sms_privacy_dialog"),
        title = { Text("Turn on SMS tracking?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Expense Tracker needs SMS access to recognize supported M-Pesa and " +
                        "bank transaction messages.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DisclosurePoint("Only messages from senders you allow are processed. Messages from everyone else are ignored immediately.")
                DisclosurePoint("Raw message bodies are never stored, logged, or shared.")
                DisclosurePoint("All processing happens on this device. Nothing is uploaded.")
                DisclosurePoint("You can turn tracking off or change allowed senders at any time.")
                Text(
                    text = "SMS sender IDs can be spoofed, so review automatically detected " +
                        "transactions before relying on them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                modifier = Modifier.testTag("sms_permission_continue"),
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun DisclosurePoint(text: String) {
    Row {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermissionBlockedDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sms_permission_blocked_dialog"),
        title = { Text("SMS access is blocked") },
        text = {
            Text(
                "Android is no longer showing the SMS permission dialog for this app. Open app " +
                    "settings and allow SMS permission to turn on tracking. Manual entry keeps " +
                    "working either way.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("sms_open_settings_confirm"),
            ) {
                Text("Open app settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun senderIcon(senderId: String): ImageVector =
    if (senderId == "MPESA" || senderId == "EQUITEL") Icons.Filled.Sms else Icons.Filled.AccountBalance

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
