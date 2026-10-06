package ctrl.mietze.veyraroot

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb

/**
 * The verdict colours, as the integers the system notification needs.
 *
 * A notification is drawn by Android, not by this app, so it cannot read a Compose theme - and this app has no
 * colours of its own to read: its palette is generated at runtime from the user's accent, or taken from the
 * wallpaper. So the theme hands the four colours over once per composition, and everything outside a
 * composable reads them from here.
 *
 * The fallbacks are the semantic hues a person would name if asked - green, amber, red - and they exist for
 * the one process that can post a notification without ever having drawn a screen: the boot gate's. Its run
 * does not use this notification, so what they are really for is the case where the app's own screen was
 * never composed and a receiver still has something to say.
 */
internal object VerdictTint {

    private const val FALLBACK_RUNNING = 0xFFB26A00.toInt()
    private const val FALLBACK_SUCCEEDED = 0xFF2E6B2E.toInt()
    private const val FALLBACK_ROOT_ONLY = 0xFF6B5E2E.toInt()
    private const val FALLBACK_FAILED = 0xFFB3261E.toInt()
    private const val FALLBACK_NEUTRAL = 0xFF5A5A5A.toInt()

    @Volatile private var running = FALLBACK_RUNNING
    @Volatile private var succeeded = FALLBACK_SUCCEEDED
    @Volatile private var rootOnly = FALLBACK_ROOT_ONLY
    @Volatile private var failed = FALLBACK_FAILED
    @Volatile private var neutral = FALLBACK_NEUTRAL

    /**
     * Takes the current scheme's roles.
     *
     * The same roles [verdictColors] uses on screen, so the shade and the card cannot disagree: the accent
     * for success, tertiary for a run in flight, secondary for root without KernelSU, and the error role for
     * a failure.
     */
    fun update(scheme: ColorScheme) {
        succeeded = scheme.primary.toArgb()
        running = scheme.tertiary.toArgb()
        rootOnly = scheme.secondary.toArgb()
        failed = scheme.error.toArgb()
        neutral = scheme.onSurfaceVariant.toArgb()
    }

    fun of(verdict: RunVerdict): Int = when (verdict) {
        RunVerdict.Idle, RunVerdict.Stopped -> neutral
        RunVerdict.Running -> running
        RunVerdict.Succeeded -> succeeded
        RunVerdict.RootOnly -> rootOnly
        RunVerdict.Failed -> failed
    }
}
