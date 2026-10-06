package ctrl.mietze.veyraroot

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Asks KernelSU itself whether root is live, by running `su`.
 *
 * [NativeProbe] reads `/sys/module/kernelsu` and `/proc/modules`, and Samsung's SELinux policy denies
 * both to app domains once the modules are loaded and the system has locked down - so a device with
 * root working perfectly can still be told, in the app's own words, that KernelSU is not installed.
 * That answer is worse than no answer: it is what makes a boot automation decide a rooted boot needs
 * rooting again.
 *
 * The app is the KernelSU manager the payload crowns, and KernelSU's own compatibility layer is what
 * answers `su` for the callers it allows. So a shell that answers `uid=0` is KernelSU saying it is
 * live, which is a direct answer rather than an inference from files a policy may hide.
 *
 * Absence is reported as absence, never as success: an app that is not the granted manager cannot run
 * `su` at all, and that is indistinguishable from a device with no root - which is the safe way round,
 * because the caller's next step is an install that needs the same allowance anyway.
 */
internal object SuProbe {

    /** Why the last probe answered no, for the log and for the app's own diagnostics. */
    internal enum class Failure { NONE, DENIED, ABSENT, ERROR }

    @Volatile
    var lastFailure: Failure = Failure.NONE
        private set

    /**
     * Runs `su -c id`, off the main thread.
     *
     * A denied or absent `su` is the expected outcome on a device without root, so nothing here
     * throws: the probe reports false and records why, and the caller decides what that means.
     */
    fun isActive(): Boolean {
        lastFailure = Failure.NONE
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append('\n')
                }
            }
            // Capped, because the probe is a step inside a run rather than a thing worth waiting on,
            // and a `su` that never answers is a no.
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                lastFailure = Failure.ERROR
                return false
            }
            val rooted = process.exitValue() == 0 && output.contains("uid=0")
            Log.d(TAG, "su probe: exit=${process.exitValue()} rooted=$rooted")
            if (!rooted) lastFailure = Failure.DENIED
            rooted
        } catch (error: Throwable) {
            // A refused start is how a non-manager app sees this: KernelSU's layer only answers
            // callers it allows, and everyone else gets ENOENT or EACCES from the exec itself.
            Log.d(TAG, "su probe unavailable: ${error.javaClass.simpleName}")
            lastFailure = Failure.ABSENT
            false
        }
    }

    private const val TAG = "RootMyGalaxySu"
    private const val PROBE_TIMEOUT_SECONDS = 3L
}
