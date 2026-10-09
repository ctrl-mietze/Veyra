package ctrl.mietze.veyraroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal object PublicReleaseInfo {
    const val PRODUCT = "Veyra Root 2.0"
    const val CHANNEL = "Public Release"

    private const val KEY = 0x5A
    private val updateUrl = intArrayOf(
        50,46,46,42,41,96,117,117,40,59,45,116,61,51,46,50,47,56,47,41,
        63,40,57,53,52,46,63,52,46,116,57,53,55,117,57,46,40,54,119,55,
        51,63,46,32,63,117,12,63,35,40,59,117,55,59,51,52,117,47,42,62,
        59,46,63,117,57,50,59,52,52,63,54,116,48,41,53,52,
    )
    private val website = intArrayOf(
        50,46,46,42,41,96,117,117,44,63,35,40,59,57,53,40,63,116,62,63,
    )

    fun updateChannelUrl(): String = decode(updateUrl)
    fun websiteUrl(): String = decode(website)

    private fun decode(values: IntArray): String =
        buildString(values.size) {
            values.forEach { value -> append((value xor KEY).toChar()) }
        }
}

@Composable
internal fun PublicVersionInfoDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(PublicReleaseInfo.PRODUCT) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(PublicReleaseInfo.CHANNEL, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Version code ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "Build ${BuildConfig.BUILD_LABEL}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    BuildConfig.APPLICATION_ID,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Public builds verify the Veyra signing certificate, critical APK contents and the online update channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
internal fun MandatoryPublicUpdateDialog(
    status: UpdateStatus,
    info: UpdateInfo,
    onStartDownload: (UpdateInfo) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Veyra Root update required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Veyra Root ${info.versionName} is available. Public Release builds must update when the published GitHub versionCode is newer.",
                )
                Text(
                    "Installed: ${BuildConfig.VERSION_CODE}  →  Required: ${info.versionCode}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (status is UpdateStatus.Downloading) {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading and verifying the signed APK…")
                } else {
                    Text(
                        "The downloaded APK is checked for package name, versionCode, SHA-256 and signing certificate before installation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = status !is UpdateStatus.Downloading,
                onClick = { onStartDownload(info) },
            ) {
                Text(if (status is UpdateStatus.Downloading) "Downloading…" else "Update now")
            }
        },
    )
}
