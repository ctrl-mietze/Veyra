package ctrl.mietze.veyraroot

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The ways out of the running Android, with the ones this device cannot do left visible and greyed.
 *
 * Visible rather than hidden, because the answer to "why is Reboot to EDL not here" is not "it is not
 * supported" - it is that this phone has no root and no Shizuku shell at the moment, which is a state the
 * user can change. Each greyed row says which of the two is missing, since a grant in the KernelSU manager
 * and starting Shizuku are different fixes.
 *
 * What can be done is probed when the sheet opens and not before: both tiers are real commands, and asking
 * a device for a shell on every recomposition of a page would be paying for an answer nothing is reading.
 *
 * [notice] is a refusal the soft-restart shortcut already collected - it makes the same probe and asks for one
 * target, so there are cases where this sheet opens on something it would otherwise have to be told: a daemon
 * that answered and then refused. Read once, as the starting value of the line rather than as state that keeps
 * arriving, because a notice belongs to the attempt that produced it and not to the sheet.
 */
@Composable
internal fun RebootSheet(onDismiss: () -> Unit, notice: RecoveryOutcome? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Null while the probe is out, which is not the same as ShellTier.None: nothing is known yet, and the
    // rows say so rather than claiming the phone cannot do something.
    var tier by remember { mutableStateOf<ShellTier?>(null) }
    var confirming by remember { mutableStateOf<RebootTarget?>(null) }
    var refusal by remember { mutableStateOf(notice) }
    LaunchedEffect(Unit) {
        tier = currentShellTier(context)
    }

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.reboot_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    when (tier) {
                        null -> R.string.reboot_status_checking
                        ShellTier.Root -> R.string.reboot_status_root
                        ShellTier.Unprivileged -> R.string.reboot_status_cveyra
                        ShellTier.None -> R.string.reboot_status_none
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (tier == ShellTier.None) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.size(4.dp))
            // The six as one card, grouped the way the settings list groups its rows: a 2dp seam between them
            // and the group's own ends carrying the rest of the curve. Six separately-rounded cards were six
            // answers to the same question, and read as a list of things rather than a choice between them.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RebootTarget.entries.forEachIndexed { index, target ->
                    val refusalForRow = tier?.let { known -> rebootRefusalFor(known, target) }
                    RebootTargetRow(
                        target = target,
                        position = rebootRowPosition(index, RebootTarget.entries.size),
                        // Offered only once the probe has answered and said yes: a row that turns out to be
                        // refused is worse than one that was never offered.
                        enabled = tier != null && refusalForRow == null,
                        reason = refusalForRow,
                        onSelect = {
                            refusal = null
                            if (target.leavesAndroid) confirming = target else scope.launch {
                                refusal = runRebootTarget(context, target)
                            }
                        },
                    )
                }
            }
            refusal?.let { outcome ->
                if (!outcome.accepted) {
                    Text(
                        text = outcome.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    confirming?.let { target ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.reboot_confirm_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.reboot_confirm_body,
                        stringResource(target.label),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirming = null
                    scope.launch { refusal = runRebootTarget(context, target) }
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirming = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * One target, as a row of the sheet.
 *
 * The icon is the same for all six on purpose: they are one kind of action, and six different glyphs would
 * suggest six different kinds. The names separate them.
 *
 * It is the settings list's row, down to the `Card` and the shape it rests at: the same component, the same
 * paddings, the same 28dp glyph, and the same swell on press. A sheet of power actions is not a second list
 * with its own idea of what a row is - the reader has already learned this shape one screen over, and a second
 * curve for the same kind of thing is the kind of difference nobody can name and everybody notices.
 *
 * What each one does is **not** written underneath. It was, on the five that needed it, and it was removed for
 * the same reason a list of six rows does not need one: the names are already the whole of it - "Reboot to
 * Download" says what "Reboots into Download mode" said, one line later and twice the height. A *refusal* is a
 * different thing and stays, because the only question a row that cannot be pressed raises is what is missing.
 */
@Composable
private fun RebootTargetRow(
    target: RebootTarget,
    position: SettingsCardPosition,
    enabled: Boolean,
    reason: RebootRefusal?,
    onSelect: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        // A row this phone cannot do is dimmed and takes no tap, which is how the settings list says the same
        // thing - so the two lists agree about both halves of "not available", and neither needs an alpha of
        // its own to say it.
        enabled = enabled,
        onClick = {
            clickHaptic(view)
            onSelect()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(target.label),
                    style = MaterialTheme.typography.titleMedium,
                )
                // The one line a row can still carry, and the only one that has to earn its height: a row that
                // cannot be pressed has to say what is missing, or it sends people looking in the wrong place.
                reason?.let { why ->
                    Text(
                        text = stringResource(why.lineRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
