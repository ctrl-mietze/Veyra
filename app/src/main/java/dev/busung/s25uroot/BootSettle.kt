package ctrl.mietze.veyraroot

import android.os.SystemClock
import java.util.Locale
import kotlin.math.abs

/**
 * How long after a boot a run waits before the exploit starts.
 *
 * The wait is measured from the boot, not from the moment the run was asked for: a device that has
 * already been up longer than the requirement waits not at all, and one that was rebooted ten seconds
 * ago waits the rest. That is the point of the gate, because what it is protecting is the state of a
 * freshly booted device, and a race attempted while the system is still settling fails for reasons the
 * payload cannot fix.
 *
 * The default is not zero. The exploit this app runs has a racy stage that a cold device makes worse,
 * and the wait costs two minutes once per boot against a failed attempt that costs the whole run. It
 * is a floor rather than a hard block: [InstallViewModel.skipBootSettle] ends the wait on the user's
 * word, because someone who knows their device just booted cleanly is better informed than a constant.
 *
 * The payload keeps a window of its own over the same boot, and it is told this run's value rather
 * than a constant of its own: see [payloadQuietWindowSeconds]. Two gates are fine when one of them is
 * derived from the other, and were not fine while both were settings nobody had compared.
 */
internal object BootSettle {
    /** What a manual run waits for unless it is told otherwise. */
    const val DEFAULT_SECONDS = 120

    /**
     * What an automatic run waits for, and deliberately not [DEFAULT_SECONDS].
     *
     * The two floors have different owners. The manual one is a person's setting, and its whole point
     * is that the person watching can lower or raise it; the automatic one is this app's own claim
     * about how settled a device has to be before it may act unattended, and it is shorter because an
     * automatic run has already waited out the part of the boot that precedes `BOOT_COMPLETED`. If the
     * automatic path read the manual setting, then turning automation on and finding it too slow would
     * change what a manual run does next time - one decision quietly rewriting another.
     */
    const val AUTO_ROOT_DEFAULT_SECONDS = 60

    /** The name the payload reads the window below under. */
    const val PAYLOAD_QUIET_WINDOW_ENV = "P0_MIN_BOOT_UPTIME_SEC"

    /**
     * The payload's own compiled window for this, and the most an environment may ask for.
     *
     * The payload waits this out before it touches the kernel, for the same reason the app waits: a
     * cold device makes its racy stage worse. It is the ceiling rather than a setting because the
     * only thing the app has to say about it is that this boot is further along than the payload
     * assumes - and an override that could *raise* it would be a way to make a run hang for minutes
     * on a device whose owner cannot see the number.
     */
    const val PAYLOAD_QUIET_WINDOW_MAX_SECONDS = 120

    /**
     * What the payload still waits when the settle gate was overridden.
     *
     * Not zero. The window protects the same racy stage whoever counts it, and someone overriding a
     * two minute pause is asking for seconds rather than for none; thirty is short enough that
     * nothing appears stuck and long enough that the allocator has stopped moving.
     */
    const val PAYLOAD_QUIET_WINDOW_OVERRIDE_SECONDS = 30

    /**
     * The window to hand the payload, from this run's own settle decision.
     *
     * Derived rather than configured, because the app's gate and the payload's are one decision
     * about one boot. A settle of `Off` means the payload does not wait either, and a settle longer
     * than the ceiling is already satisfied by the app's own wait before the payload starts - which
     * is what makes the two agree instead of stacking.
     */
    fun payloadQuietWindowSeconds(requiredSeconds: Int, overridden: Boolean): Int =
        if (overridden) {
            PAYLOAD_QUIET_WINDOW_OVERRIDE_SECONDS
        } else {
            minOf(normalize(requiredSeconds), PAYLOAD_QUIET_WINDOW_MAX_SECONDS)
        }

    /**
     * What the setting offers. Rounded to these rather than free-form: a value nobody tested is not a
     * better one, and a round number is what makes the choice reviewable.
     */
    val allowedSeconds = listOf(0, 30, 60, 90, 120, 180, 300, 600)

    /** The offered value nearest to [seconds], so a stored number is always one of them. */
    fun normalize(seconds: Int): Int =
        allowedSeconds.minByOrNull { abs(it - seconds) } ?: DEFAULT_SECONDS

    /**
     * Milliseconds still to wait, given the boot's elapsed time.
     *
     * Returns zero rather than a negative number once the boot is old enough, so a caller can test the
     * result rather than having to know which side of the subtraction it is on.
     */
    fun remainingMillis(requiredSeconds: Int, elapsedRealtimeMillis: Long): Long =
        (normalize(requiredSeconds) * 1_000L - elapsedRealtimeMillis).coerceAtLeast(0L)

    /** Time since boot, which is what the gate is measured against and what survives a deep sleep. */
    fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    /**
     * `1:42` for a countdown.
     *
     * Rounded up, so a wait never reads `0:00` while it is still waiting: the last second of a
     * countdown is a second, and showing zero during it would say the run had started when it had not.
     */
    fun formatRemaining(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
        // The locale is pinned because `%d` is not: `Formatter` renders an integer in the default
        // locale's own digits, so on a device set to Arabic, Persian, Bengali or Devanagari a countdown
        // would read `١:٤٢`. A number counting down to a moment is a reading rather than prose, and the
        // one thing it must not do is change shape with a locale setting - the same reason the log
        // timestamps and the export filenames are pinned.
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    /** The setting's own label for a value, as the chooser and the run plan show it. */
    fun label(seconds: Int): String {
        val normalized = normalize(seconds)
        val minutes = normalized / 60
        val rest = normalized % 60
        return when {
            normalized == 0 -> "Off"
            minutes == 0 -> "$rest s"
            rest == 0 -> "$minutes min"
            else -> "$minutes min $rest s"
        }
    }
}
