package ctrl.mietze.veyraroot

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

internal fun clickHaptic(
    view: View,
    sound: UiSoundKind? = UiSoundKind.Button,
) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
    sound?.let { UiSoundEngine.play(view.context, it) }
}
