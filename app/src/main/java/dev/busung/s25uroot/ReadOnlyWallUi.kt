package ctrl.mietze.veyraroot

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * What the read-only wall looks like wherever it is reported.
 *
 * It appears in three places - the run screen's failure card and both repair dialogs - and it is one
 * composable because of what it has to get right: the switch is named from its *own* label rather than
 * from a copy of the words, and the button that goes to it is the same button in all three. Written out
 * at each site, a rename of the setting would leave one of them naming a switch that no longer exists,
 * which is worse than saying nothing: it is a link to the wrong row.
 *
 * The lock rather than a warning icon on purpose. Nothing here is broken and nothing needs undoing - a
 * guard that the app's own run put up is working, and it is standing in the way of one thing the user
 * meant to do. A warning triangle would say the opposite.
 */
@Composable
internal fun ReadOnlyWallNotice(
    /** Where "Open setting" goes. The caller owns that, because the target is not always in this window. */
    onOpenSetting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Colours come from whatever this is drawn on rather than from the theme: the failure card
            // is an error container and the dialogs are surfaces, so a fixed palette would be one of the
            // three reading wrong. LocalContentColor is the one thing all three set.
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LocalContentColor.current,
            )
            Text(
                // The switch's own label is substituted in, so this sentence cannot name a setting that
                // was renamed or removed.
                stringResource(
                    R.string.read_only_wall_notice,
                    stringResource(R.string.partition_read_only),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
        }
        TextButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onOpenSetting()
            },
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        ) {
            Text(stringResource(R.string.action_open_setting))
        }
    }
}
