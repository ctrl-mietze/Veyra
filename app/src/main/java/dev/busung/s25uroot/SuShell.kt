package ctrl.mietze.veyraroot

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * A root shell the app opens for itself, by asking KernelSU's `su` directly.
 *
 * [KernelSuRuntime.rootShell] originally had one route: the Shizuku server, which is a fine route and
 * the quiet one, but it is not always there. It needs Shizuku running *and* this app granted by it, and
 * when either is missing a device with KernelSU loaded and working perfectly gets told, in the app's own
 * words, that "KernelSU root is not available for this boot" - a sentence that is false about the phone
 * and useless to whoever reads it, because the thing that is actually missing is a door this app can
 * open for itself.
 *
 * That door is `su`. KernelSU's own userspace answers `su` for the apps its manager has approved, which
 * for this app is the ordinary state of affairs after a run: the payload's install is what crowns a
 * manager, and the manager is where the user grants root to an app. Asking it directly needs no
 * transport, no binder and no other app running.
 *
 * Two things are deliberately different from a Shizuku command here. The first is that a read can block
 * forever - if KernelSU is waiting for the user to answer its own grant prompt, `su` has not written a
 * byte and will not until it is answered - so the output is read on its own thread and the wait is
 * bounded, and a run that does not answer in time is reported as *no answer* rather than as a failure.
 * The second is that absence is never reported as success: a `su` that cannot be started, or one that
 * exits without root, is a no, and the caller falls through to its own refusal.
 */
internal object SuShell {

    /**
     * Runs [command] as root, or returns null when no root shell answered in time.
     *
     * Null is the only failure this reports, and it means "this route did not run it" - never "it ran
     * and failed", which comes back as a result with its own exit code. The distinction matters
     * because the callers treat a missing shell and a failed command differently: one is a refusal to
     * act, the other is an action that was attempted.
     */
    fun run(
        command: String,
        timeoutSeconds: Long = COMMAND_TIMEOUT_SECONDS,
    ): ShizukuController.ShellResult? {
        val process = start(command) ?: return null
        val output = StringBuilder()
        // Read on its own thread: the pipe is what blocks, not the process, and a `su` waiting on a
        // grant prompt writes nothing at all, so reading inline would wait on a decision forever.
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        val answered = runCatching { process.waitFor(timeoutSeconds, TimeUnit.SECONDS) }
            .getOrDefault(false)
        if (!answered) {
            Log.i(TAG, "su did not answer within ${timeoutSeconds}s; treating it as no root shell")
            runCatching { process.destroyForcibly() }
            reader.join(READER_GRACE_MILLIS)
            return null
        }
        reader.join(READER_GRACE_MILLIS)
        val exitCode = runCatching { process.exitValue() }.getOrNull() ?: return null
        return ShizukuController.ShellResult(exitCode, synchronized(output) { output.toString().trim() })
    }

    /** Whether this app can get a root shell of its own right now. */
    fun isRoot(): Boolean = isRootAnswer(run(ID_COMMAND, PROBE_TIMEOUT_SECONDS))

    private fun start(command: String): Process? = try {
        ProcessBuilder("su", "-c", "$command 2>&1")
            .redirectErrorStream(true)
            .start()
    } catch (error: Throwable) {
        // How a device with no `su` at all reports itself, and also how KernelSU's layer reports a
        // caller it refuses: the exec itself fails. Neither is an error worth a stack trace.
        Log.d(TAG, "su unavailable: ${error.javaClass.simpleName}")
        null
    }

    private const val TAG = "RootMyGalaxySuShell"
    private const val ID_COMMAND = "id"

    /**
     * The probe's window. Bounded tightly, because the probe runs on paths the user is not watching -
     * a boot start asks this question with nobody looking at the screen - and an unanswered `su` there
     * must not hold up a token start for longer than the answer is worth.
     */
    private const val PROBE_TIMEOUT_SECONDS = 8L

    /**
     * The actions here are short by construction; a longer wait is a shell that is not coming back.
     *
     * Internal rather than private because a caller with a shorter patience for one particular command
     * - a listing asked for while a screen is opening - passes its own, and wanting the default is how
     * it says so.
     */
    internal const val COMMAND_TIMEOUT_SECONDS = 30L
    private const val READER_GRACE_MILLIS = 500L
}

/**
 * Whether an answer to `id` says this shell is root.
 *
 * The exit code and the `uid=0` are both required, and both are the same test the Shizuku route makes:
 * `su` that exits 0 without printing root is not root, and a root `id` is not worth reading if the
 * command itself failed. Null - nothing answered - is a no.
 */
internal fun isRootAnswer(answer: ShizukuController.ShellResult?): Boolean =
    answer != null && answer.exitCode == 0 && answer.output.contains("uid=0")
