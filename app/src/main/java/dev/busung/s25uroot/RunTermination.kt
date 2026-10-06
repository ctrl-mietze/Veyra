package ctrl.mietze.veyraroot

import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * What is known about a payload process after the app asked it to stop.
 *
 * The distinction is worth a type because a payload that did not stop is a payload that is still
 * writing to the kernel, and everything the app would offer next assumes nothing is. A retry in the
 * same boot starts a second payload on top of the first; an exit status read from a process that is
 * still alive is not an ending at all; and a failure reported as a verdict is a verdict about a run
 * whose ending nobody watched.
 */
internal enum class Termination {
    /** The process is gone. Its exit status, if there is one, is about a run that finished. */
    Confirmed,

    /**
     * Alive after both attempts to stop it. Something is running that this app can no longer control,
     * and the only thing that clears it is a restart.
     */
    Unconfirmed,
    ;

    /**
     * Whether a retry in this boot can be offered.
     *
     * Only after a confirmed stop, because what a retry would add is a second payload and the phone
     * cannot carry two. Restarting is the answer that clears it, and that is what the screen offers
     * instead.
     */
    val inBootRetryIsSafe: Boolean get() = this == Confirmed
}

/** How long the app gives a payload to die after asking it to, before it says it could not. */
internal const val TERMINATION_GRACE_MILLIS = 3_000L

/**
 * Asks the process to stop and reports whether it actually did.
 *
 * The app's own helper dies on `destroy()`. A process started through Shizuku does not necessarily:
 * that call is a binder round trip to another app's daemon, so a daemon that has died, a binder that
 * has gone away, or a child that ignores the signal all leave the payload running while the app
 * believes it stopped it. The stop is therefore two attempts with a bounded wait after each, and the
 * answer is "confirmed" only when the process was observed dead.
 */
internal suspend fun Process.stopConfirmed(
    graceMillis: Long = TERMINATION_GRACE_MILLIS,
): Termination {
    if (!aliveNow()) return Termination.Confirmed
    runCatching { destroy() }
    if (awaitExit(graceMillis)) return Termination.Confirmed
    runCatching { destroyForcibly() }
    return if (awaitExit(graceMillis)) Termination.Confirmed else Termination.Unconfirmed
}

/**
 * Whether the process left within [timeoutMillis]. The last reading decides, so a process that exits
 * while the wait is running is still a confirmed stop rather than a lucky guess.
 */
private suspend fun Process.awaitExit(timeoutMillis: Long): Boolean {
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    while (true) {
        if (!aliveNow()) return true
        if (SystemClock.elapsedRealtime() >= deadline) return false
        delay(TERMINATION_POLL_MILLIS)
    }
}

/**
 * Whether the process is alive, with a binder that has gone away read as gone.
 *
 * A remote process answers this over the same binder as everything else, so the read itself can throw
 * once Shizuku has died - and a payload behind a dead binder is one this app will never reach again.
 * Failing the run there instead would report a dead transport as a failed exploit.
 */
private fun Process.aliveNow(): Boolean = runCatching { isAlive }.getOrDefault(false)

/** How often the wait above re-reads the process. */
private const val TERMINATION_POLL_MILLIS = 100L
