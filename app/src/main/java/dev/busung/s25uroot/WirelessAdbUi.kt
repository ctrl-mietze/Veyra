package ctrl.mietze.veyraroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun WirelessAdbDialog(
    snapshot: WirelessAdbSnapshot?,
    busy: Boolean,
    writeSecureSettingsMissing: Boolean,
    onPair: (forceRepair: Boolean) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onTest: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.wireless_adb_dialog_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                when (snapshot?.authState) {
                                    WirelessAdbAuthState.Valid -> Icons.Rounded.CheckCircle
                                    WirelessAdbAuthState.SavedUnverified -> Icons.Rounded.Link
                                    else -> Icons.Rounded.Info
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                snapshot?.let { wirelessAdbStateLabel(it.authState) }
                                    ?: stringResource(R.string.reboot_status_checking),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (snapshot != null) {
                            Text(
                                snapshot.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            snapshot.fingerprint?.let { fingerprint ->
                                Text(
                                    stringResource(R.string.wireless_adb_fingerprint) + ": " + fingerprint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.wireless_adb_pair_help),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (writeSecureSettingsMissing) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.wireless_adb_permission_note),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.adb_pair_working),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                WirelessAdbAction(
                    icon = Icons.Rounded.Link,
                    label = stringResource(
                        if (snapshot?.keyPresent == true) {
                            R.string.wireless_adb_pair_again
                        } else {
                            R.string.wireless_adb_pair
                        },
                    ),
                    enabled = !busy,
                    onClick = { onPair(snapshot?.keyPresent == true) },
                )
                if (writeSecureSettingsMissing) {
                    WirelessAdbAction(
                        icon = Icons.Rounded.Security,
                        label = stringResource(R.string.grant_action),
                        enabled = !busy,
                        onClick = onGrantPermission,
                    )
                }
                WirelessAdbAction(
                    icon = Icons.Rounded.Settings,
                    label = stringResource(R.string.adb_pair_open_developer_options),
                    enabled = !busy,
                    onClick = onOpenDeveloperOptions,
                )
                WirelessAdbAction(
                    icon = Icons.Rounded.Terminal,
                    label = stringResource(R.string.wireless_adb_test),
                    enabled = !busy,
                    onClick = onTest,
                )
                if (snapshot?.keyPresent == true) {
                    TextButton(
                        enabled = !busy,
                        onClick = onForget,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.wireless_adb_forget))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun WirelessAdbAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
internal fun wirelessAdbStateLabel(state: WirelessAdbAuthState): String = stringResource(
    when (state) {
        WirelessAdbAuthState.NoCredential -> R.string.settings_wireless_adb_none
        WirelessAdbAuthState.SavedUnverified -> R.string.settings_wireless_adb_unverified
        WirelessAdbAuthState.Valid -> R.string.settings_wireless_adb_valid
        WirelessAdbAuthState.PairingRejected -> R.string.settings_wireless_adb_rejected
        WirelessAdbAuthState.PortUnavailable -> R.string.settings_wireless_adb_port_missing
        WirelessAdbAuthState.PermissionRequired -> R.string.settings_wireless_adb_no_permission
        WirelessAdbAuthState.ConnectionFailed -> R.string.settings_wireless_adb_failed
    },
)
