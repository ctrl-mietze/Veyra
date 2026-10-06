package ctrl.mietze.veyraroot

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuController {
    private const val PERMISSION_REQUEST_CODE = 0x5352

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /**
     * The binder is delivered to the app asynchronously after the Shizuku service starts.
     * Wait a short while in case the service is already up but the binder has not arrived yet.
     */
    suspend fun pingUntilRunning(timeoutMillis: Long = 3_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isRunning()) return true
            delay(100)
        }
        return isRunning()
    }

    fun isGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * What this app can do with Shizuku right now, as one of the three states that need different
     * words.
     *
     * Answered live rather than remembered, because the answer changes without this app doing anything:
     * Shizuku delivers its binder asynchronously, another starter can bring the service up, and the
     * user can grant or revoke permission from the Shizuku app itself.
     */
    internal fun availability(): ShizukuAvailability =
        shizukuAvailability(isRunning(), isGranted())

    suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        if (!isRunning()) return false
        return suspendCancellableCoroutine { continuation ->
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    // Refused is a warning rather than news: it is the answer that stops a run, and the
                    // dialog is the only other place it appears.
                    AppLog.record(
                        level = if (granted) AppLogLevel.Info else AppLogLevel.Warn,
                        tag = AppLogTags.SHIZUKU,
                        message = if (granted) "Permission granted" else "Permission refused",
                    )
                    continuation.resume(granted)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            AppLog.info(AppLogTags.SHIZUKU, "Asking Shizuku for permission")
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (error: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resumeWithException(error)
            }
        }
    }

    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder is not available")
        return RemoteProcess(IShizukuService.Stub.asInterface(binder).newProcess(cmd, env, dir))
    }

    /**
     * Runs a short command and returns its combined output. Used to read files the app
     * process cannot access directly because SELinux confines app UIDs away from the
     * shell-owned /data/local/tmp directory.
     */
    fun capture(cmd: Array<String>): String {
        val process = exec(cmd)
        return try {
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) stdout + stderr else ""
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    /** What a short command through the running Shizuku server reported. */
    data class ShellResult(val exitCode: Int, val output: String)

    /**
     * Runs [command] through the Shizuku server that is already running, with stderr merged into
     * the output so a caller reads one account of what happened.
     *
     * Shizuku hands the client a shell-owned process, so this cannot elevate on its own: a caller
     * that needs root asks KernelSU, as [KernelSuRuntime] does with `su -c`. It is still the cheap
     * way to run a privileged command once root exists, because no new transport has to be opened.
     */
    fun shell(command: String): ShellResult {
        val process = exec(arrayOf(SHIZUKU_SHELL, "-c", "$command 2>&1"))
        return try {
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            ShellResult(process.waitFor(), output)
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    /**
     * Stages [source] at [remotePath].
     *
     * The bytes go to a temporary file beside the destination and are moved into place only once the
     * remote size matches what was actually sent. Writing the destination directly is where a
     * truncated payload came from: `cat >` truncates before it copies, so an upload interrupted by a
     * dying Shizuku, a refused write, or a killed process left a partial file where a good one had
     * been, and the payload cache then executed it.
     */
    fun writeFile(remotePath: String, mode: String, source: InputStream) {
        require(isFileMode(mode)) { "Invalid file mode: $mode" }
        val tempPath = "$remotePath.shizuku-${UUID.randomUUID()}.tmp"
        val quotedPath = shellQuote(remotePath)
        val quotedTemp = shellQuote(tempPath)
        val upload = exec(arrayOf(SHIZUKU_SHELL, "-c", uploadCommand(quotedTemp)))
        try {
            val bytesCopied = try {
                source.use { input ->
                    upload.outputStream.use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "Failed to stage $remotePath during upload: ${uploadFailure(upload, error)}",
                    error,
                )
            }
            val uploadExit = upload.waitFor()
            check(uploadExit == 0) {
                "Failed to stage $remotePath during upload (exit $uploadExit)" +
                    stderrOf(upload).asSuffix()
            }
            val published = runShell(publishCommand(quotedPath, quotedTemp, mode, bytesCopied))
            check(published.exitCode == 0) {
                "Failed to publish $remotePath (exit ${published.exitCode})" + published.stderr.asSuffix()
            }
        } finally {
            if (upload.isAlive) upload.destroy()
            // A no-op once the temp file was moved, and the only cleanup if publishing was refused.
            cleanupTemp(quotedTemp)
        }
    }

    private data class ShellOutcome(val exitCode: Int, val stderr: String)

    private fun runShell(command: String): ShellOutcome {
        val process = exec(arrayOf(SHIZUKU_SHELL, "-c", command))
        return try {
            val exitCode = process.waitFor()
            ShellOutcome(exitCode, stderrOf(process))
        } finally {
            if (process.isAlive) process.destroy()
        }
    }

    private fun cleanupTemp(quotedTemp: String) {
        runCatching { runShell("rm -f $quotedTemp") }
    }

    /** Why an upload failed: what the remote shell said, or the local failure when it said nothing. */
    private fun uploadFailure(process: Process, error: Throwable): String {
        if (process.isAlive) process.destroy()
        runCatching { process.waitFor() }
        return stderrOf(process).ifBlank { error.message ?: error.javaClass.simpleName }
    }

    private fun stderrOf(process: Process): String = runCatching {
        process.errorStream.bufferedReader().use { it.readText() }.trim()
    }.getOrDefault("")

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream()) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream()) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()
        override fun exitValue(): Int = remote.exitValue()

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = remote.alive()
    }
}

private const val SHIZUKU_SHELL = "/system/bin/sh"

private val FILE_MODE = Regex("[0-7]{3,4}")

/** Whether [mode] is an octal permission a remote `chmod` can be given without quoting it. */
internal fun isFileMode(mode: String): Boolean = FILE_MODE.matches(mode)

/**
 * The three states a Shizuku-backed app can be in, which are not two.
 *
 * **Running** and **usable** are separate facts, and collapsing them is what made the settings row
offer to "Start Shizuku now" on a device where the service was already up: the row's only signal was
 * whether a start attempt was in flight, so a running Shizuku looked exactly like a stopped one. Every
 * start attempt then took the already-running exit and reported it in a dialog - which is true, and is
 * also the row's own missing state, said once, to one person, instead of on the screen.
 */
internal enum class ShizukuAvailability {
    /** No binder: nothing to use and something to start. */
    NotRunning,

    /** The service is up and this app is not allowed to use it: the missing piece is a grant. */
    WithoutPermission,

    /** Up and allowed. There is nothing left for a start button to do. */
    Ready,
}

/**
 * Which of the three, from Shizuku's two independent answers.
 *
 * Pure, so the case that was wrong - running without permission - can be pinned by a test rather than
 * discovered by tapping a row and reading a dialog.
 */
internal fun shizukuAvailability(running: Boolean, granted: Boolean): ShizukuAvailability = when {
    !running -> ShizukuAvailability.NotRunning
    granted -> ShizukuAvailability.Ready
    else -> ShizukuAvailability.WithoutPermission
}

/** What turning the *use Shizuku* preference on should actually do, from what Shizuku is saying. */
internal enum class ShizukuModeEnable {
    /** Nothing in the way: store the preference. */
    Enable,

    /** A grant is the only thing missing, so ask for it and store the preference if it lands. */
    RequestPermission,

    /** There is no service to use: say what is missing instead of storing a preference that cannot work. */
    ExplainMissing,
}

/**
 * The rule, as one pure decision.
 *
 * It used to be a wait: enabling pinged the binder for up to three seconds and then guessed from the
 * answer, which is the same race with a longer fuse - and it stored the preference *before* asking for
 * the permission, so a refused prompt left the app preferring a transport it was not allowed to use and
 * a switch saying that preference was on. The state is the input here, so there is nothing to wait for
 * and nothing to guess.
 */
internal fun shizukuModeEnableRoute(availability: ShizukuAvailability): ShizukuModeEnable = when (
    availability
) {
    ShizukuAvailability.Ready -> ShizukuModeEnable.Enable
    ShizukuAvailability.WithoutPermission -> ShizukuModeEnable.RequestPermission
    ShizukuAvailability.NotRunning -> ShizukuModeEnable.ExplainMissing
}

/**
 * Single-quotes [value] for the remote shell, closing and reopening the quote around any quote in
 * it. A payload path is built from a profile id, which the feed controls.
 */
internal fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

/**
 * The upload half: copies stdin to the temporary path and never names the destination, so nothing
 * short of the publish step can touch the file the app is about to execute.
 */
internal fun uploadCommand(quotedTemp: String): String = "rm -f $quotedTemp && cat > $quotedTemp"

/**
 * The publish half: refuses a short file, then modes it and moves it over the destination. The size
 * is compared against what the local side actually sent, not against the manifest, so a truncated
 * transfer is caught even when the declared size is wrong.
 */
internal fun publishCommand(
    quotedPath: String,
    quotedTemp: String,
    mode: String,
    expectedBytes: Long,
): String = listOf(
    "set -e",
    "trap 'rm -f $quotedTemp' EXIT HUP INT TERM",
    "actual=$(/system/bin/wc -c < $quotedTemp)",
    "if [ \"${'$'}actual\" -ne $expectedBytes ]; then " +
        "echo \"staged size mismatch: expected $expectedBytes, got ${'$'}actual\" >&2; exit 1; fi",
    "chmod $mode $quotedTemp",
    "mv -f $quotedTemp $quotedPath",
).joinToString("\n")

private fun String.asSuffix(): String = if (isBlank()) "" else ": $this"
