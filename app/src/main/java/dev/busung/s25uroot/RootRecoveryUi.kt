package ctrl.mietze.veyraroot

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class RecoveryMessage(
    val title: String,
    val detail: String,
    val failure: Boolean,
    /**
     * True when the read-only protection this app set up in this boot is why it failed.
     *
     * The one refusal with a fix in this app, so the dialog that reports it also names the switch and
     * offers the way to it rather than leaving the person to remember where it lives.
     */
    val readOnlyWall: Boolean = false,
)

/**
 * The post-root repair actions, in Advanced mode.
 *
 * They are a settings group like every other one - the same rows, the same two-point gaps, the same
 * shared corners - because they are actions *about* the app's state, and a second visual language for
 * three rows only made them look like something else was going on. What separates them is that a tap
 * does not run one: it opens a dialog that says what the action costs, and the dialog's own button is
 * what starts it. Holding was the older confirmation and it was the wrong one, because a hold is
 * invisible until it succeeds, so nothing on the screen said these rows behaved differently from
 * every other row beside them.
 *
 * Root is not asked about up front: the action itself asks for a root shell and reports the refusal,
 * because the cheap in-process probe for KernelSU can answer no on a device where root is usable.
 */
@Composable
internal fun RootRecoverySection(
    onBootRootModeChanged: (Boolean) -> Unit,
    /** False when runs are told not to load KernelSU, which is what these actions consume. */
    kernelSuLoadingEnabled: Boolean = true,
    /** Opens a settings card by its target: this section lives in the list that holds that card. */
    onOpenSetting: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<RecoveryTool?>(null) }
    var confirming by remember { mutableStateOf<RecoveryTool?>(null) }
    var message by remember { mutableStateOf<RecoveryMessage?>(null) }

    fun report(tool: RecoveryTool, outcome: RecoveryOutcome) {
        message = RecoveryMessage(
            title = context.getString(tool.titleRes()),
            // What was accepted differs between the actions: two of them restart the Android runtime
            // and one restarts the phone, so the accepted message is the action's own.
            detail = if (outcome.accepted) context.getString(tool.acceptedRes()) else outcome.detail,
            failure = !outcome.accepted,
            readOnlyWall = outcome.readOnlyWall,
        )
    }

    fun run(tool: RecoveryTool) {
        if (running != null) return
        running = tool
        scope.launch {
            val outcome = runRecoveryAction(context, tool)
            // The stored state is the one the screen follows, so a refusal puts root on boot back
            // on screen as well as on disk, and an accepted one leaves both off.
            if (tool == RecoveryTool.RebootAndUnroot) {
                onBootRootModeChanged(AppPreferences.bootRootMode(context))
            }
            report(tool, outcome)
            running = null
        }
    }

    confirming?.let { tool ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = { Icon(tool.icon(), contentDescription = null) },
            title = { Text(stringResource(tool.titleRes())) },
            text = { Text(stringResource(tool.confirmRes())) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    confirming = null
                    run(tool)
                }) {
                    Text(stringResource(tool.actionRes()))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    message?.let { shown ->
        AlertDialog(
            onDismissRequest = { message = null },
            icon = {
                Icon(
                    if (shown.failure) Icons.Rounded.Warning else Icons.Rounded.Shield,
                    contentDescription = null,
                )
            },
            title = { Text(shown.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shown.detail)
                    if (shown.readOnlyWall) {
                        ReadOnlyWallNotice(
                            onOpenSetting = {
                                message = null
                                onOpenSetting(SettingsTarget.PartitionReadOnly)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    message = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    // No explanatory paragraph of its own: the rows say what they do, and the dialog says what each
    // one costs. What is left is the list itself, in the same shape as the groups above it.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RecoveryTool.entries.forEachIndexed { index, tool ->
            SettingsCard(
                icon = tool.icon(),
                title = stringResource(tool.titleRes()),
                description = stringResource(tool.summaryRes()),
                position = when (index) {
                    0 -> SettingsCardPosition.Top
                    RecoveryTool.entries.lastIndex -> SettingsCardPosition.Bottom
                    else -> SettingsCardPosition.Middle
                },
                busy = running == tool,
                enabled = kernelSuLoadingEnabled,
                // The dependency is stated on the row rather than only in the refusal dialog: with
                // loading off these actions cannot ever run, and a card that looks live and then
                // refuses is the shape of bug this screen has already had once.
                value = if (kernelSuLoadingEnabled) {
                    ""
                } else {
                    stringResource(R.string.recovery_needs_kernel_su)
                },
                onClick = { confirming = tool },
            )
        }
    }
}

/**
 * One repair action as a button, with the section's own confirmation and report.
 *
 * The run screen offers *Restart userspace* after an install that just loaded KernelSU, and it offers
 * it as this rather than as a second dialog of its own: the action costs the same thing wherever it is
 * started from - every running app closes - so the confirmation says the same thing and the outcome is
 * reported the same way.
 */
@Composable
internal fun RecoveryActionButton(
    tool: RecoveryTool,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBootRootModeChanged: (Boolean) -> Unit = {},
    /**
     * Opens a settings card by its target, for a refusal this app's own protection caused.
     *
     * Passed in rather than assumed: this button is used from the run screen, which is a different
     * window - so the jump is a fresh intent there and a scroll when it is the settings page itself.
     */
    onOpenSetting: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<RecoveryMessage?>(null) }

    FilledTonalButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            confirming = true
        },
        modifier = modifier,
        enabled = enabled && !running,
    ) {
        if (running) {
            LoadingIndicator(modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(tool.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(tool.icon(), contentDescription = null) },
            title = { Text(stringResource(tool.titleRes())) },
            text = { Text(stringResource(tool.confirmRes())) },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    confirming = false
                    running = true
                    scope.launch {
                        val outcome = runRecoveryAction(context, tool)
                        if (tool == RecoveryTool.RebootAndUnroot) {
                            onBootRootModeChanged(AppPreferences.bootRootMode(context))
                        }
                        message = RecoveryMessage(
                            title = context.getString(tool.titleRes()),
                            detail = if (outcome.accepted) {
                                context.getString(tool.acceptedRes())
                            } else {
                                outcome.detail
                            },
                            failure = !outcome.accepted,
                            readOnlyWall = outcome.readOnlyWall,
                        )
                        running = false
                    }
                }) {
                    Text(stringResource(tool.actionRes()))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    message?.let { shown ->
        AlertDialog(
            onDismissRequest = { message = null },
            icon = {
                Icon(
                    if (shown.failure) Icons.Rounded.Warning else Icons.Rounded.Shield,
                    contentDescription = null,
                )
            },
            title = { Text(shown.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shown.detail)
                    if (shown.readOnlyWall) {
                        ReadOnlyWallNotice(
                            onOpenSetting = {
                                message = null
                                onOpenSetting(SettingsTarget.PartitionReadOnly)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    message = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

private fun RecoveryTool.icon(): ImageVector = when (this) {
    RecoveryTool.ReloadModules -> Icons.Rounded.Refresh
    RecoveryTool.RestartZygote -> Icons.Rounded.RestartAlt
    RecoveryTool.SoftReboot -> Icons.Rounded.Memory
    RecoveryTool.RebootAndUnroot -> Icons.Rounded.Warning
}

private fun RecoveryTool.titleRes(): Int = when (this) {
    RecoveryTool.ReloadModules -> R.string.recovery_reload_modules
    RecoveryTool.RestartZygote -> R.string.recovery_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_unroot
}

private fun RecoveryTool.summaryRes(): Int = when (this) {
    RecoveryTool.ReloadModules -> R.string.recovery_reload_modules_summary
    RecoveryTool.RestartZygote -> R.string.recovery_restart_zygote_summary
    RecoveryTool.SoftReboot -> R.string.recovery_soft_reboot_summary
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_unroot_summary
}

private fun RecoveryTool.confirmRes(): Int = when (this) {
    RecoveryTool.ReloadModules -> R.string.recovery_confirm_reload_modules
    RecoveryTool.RestartZygote -> R.string.recovery_confirm_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_confirm_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_confirm_reboot_unroot
}

private fun RecoveryTool.actionRes(): Int = when (this) {
    RecoveryTool.ReloadModules -> R.string.recovery_action_reload_modules
    RecoveryTool.RestartZygote -> R.string.recovery_action_restart_zygote
    RecoveryTool.SoftReboot -> R.string.recovery_action_soft_reboot
    RecoveryTool.RebootAndUnroot -> R.string.recovery_action_reboot_unroot
}

private fun RecoveryTool.acceptedRes(): Int = when (this) {
    RecoveryTool.ReloadModules -> R.string.recovery_modules_reloaded
    RecoveryTool.RebootAndUnroot -> R.string.recovery_reboot_scheduled
    else -> R.string.recovery_scheduled
}
