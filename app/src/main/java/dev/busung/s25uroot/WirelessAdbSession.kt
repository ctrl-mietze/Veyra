package ctrl.mietze.veyraroot

import android.content.Context
import android.os.SystemClock
import java.io.Closeable
import java.io.File

/**
 * A live connection to the device's own adbd over wireless debugging.
 *
 * Opening one is the whole bring-up: wireless debugging on if it is off, the published port, and
 * authentication with the key this app paired. Commands run as `u:r:shell:s0`, which is what a run
 * needs from a transport - and unlike the app's own process, that context can reach the places a
 * payload is staged in.
 *
 * The failures are named rather than wrapped in one error, because the three of them need different
 * responses from whoever is reading: a missing key means pairing has to happen again, a missing port
 * means the setting is off or adbd has not published it yet, and a refused certificate means the
 * device has forgotten this app and pairing has to happen again *on the device side*.
 */
class WirelessAdbSession private constructor(
    private val client: LocalAdbClient,
) : Closeable {

    /** Pushes a local file to the device, optionally marking it executable. */
    fun push(localFile: File, remotePath: String, executable: Boolean = false) {
        client.push(localFile, remotePath)
        if (executable) {
            val chmod = client.shell("chmod 755 ${shellQuote(remotePath)}")
            check(chmod.exitCode == 0) { "chmod 755 $remotePath failed: ${chmod.output}" }
        }
    }

    /** Streams a remote shell-readable file into app-owned storage through ADB sync. */
    fun pull(remotePath: String, localFile: File) {
        client.pull(remotePath, localFile)
    }

    /** Runs a command in the shell context and returns its result. */
    fun shell(command: String): LocalAdbClient.ShellResult = client.shell(command)

    /**
     * Runs [command] as root through the staged helper's daemon.
     *
     * The ADB shell is `u:r:shell:s0`, which SELinux denies for privileged work - `setprop ctl.*`,
     * mounting a module - so root still has to come from the helper that the exploit left running.
     * The daemon runs the command through a double-quoted shell, which is why it is escaped here
     * rather than quoted: a command containing a quote must not be able to end the quoting and run
     * something of its own.
     */
    fun shellAsRoot(
        command: String,
        helperPath: String = DEFAULT_HELPER_PATH,
    ): LocalAdbClient.ShellResult {
        val escaped = command
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`")
        return client.shell("$helperPath -c \"$escaped\"")
    }

    /**
     * Runs [command] in one shell that stays open for its whole life, reporting output as it arrives.
     *
     * adbd kills a backgrounded process the moment its shell stream closes, so a command that runs for
     * minutes has to own an open shell. That is exactly how the helper's payload mode runs: it forks
     * the work into its own session and supervises it in the foreground, so streaming its stdout is
     * real progress rather than a log file polled after the fact.
     */
    fun runStreaming(
        command: String,
        overallTimeoutMs: Long = LocalAdbClient.DEFAULT_STREAM_TIMEOUT_MS,
        stallTimeoutMs: Long = LocalAdbClient.DEFAULT_STALL_TIMEOUT_MS,
        shouldStop: () -> Boolean = { false },
        onOutput: (String) -> Unit,
    ): String {
        val accumulated = StringBuilder()
        client.shellStreaming(
            command = command,
            overallTimeoutMs = overallTimeoutMs,
            stallTimeoutMs = stallTimeoutMs,
            shouldStop = shouldStop,
        ) { chunk ->
            accumulated.append(chunk)
            onOutput(accumulated.toString())
        }
        return accumulated.toString()
    }

    /** Removes a remote file, ignoring the result. */
    fun remove(remotePath: String) {
        client.shell("rm -f ${shellQuote(remotePath)}")
    }

    /** Reads a remote file, or an empty string when it is not there. */
    fun readLog(remotePath: String): String =
        client.shell("cat ${shellQuote(remotePath)} 2>/dev/null").output

    override fun close() {
        runCatching { client.close() }
    }

    companion object {
        /** Where the verified late-load leaves the helper this session runs commands through. */
        /**
         * The helper a wireless run stages, by the path that run pushed it to.
         *
         * Kept in step with the constant of the same meaning in `InstallViewModel`, which is the only
         * thing that writes it: this is where a shell finds it, not where it is put.
         */
        const val DEFAULT_HELPER_PATH = "/data/local/tmp/rmgnext-ksud-helper"

        /**
         * Enables wireless debugging if it is off, finds the port, and authenticates.
         *
         * Throws when it cannot, because every caller treats that as "this transport is unavailable"
         * rather than as something to continue past.
         */
        fun open(context: Context, portDiscoveryTimeoutMs: Long = 60_000): WirelessAdbSession {
            // A stored pairing flag must never lead to a fresh identity being generated silently: if
            // the key is gone, the flag is stale and the honest answer is that pairing is required.
            if (!AdbCredentialStore.hasStoredKey(context)) {
                AppPreferences.setAdbPaired(context, false)
                error("ADB_CREDENTIAL_MISSING: pair with wireless debugging first")
            }

            if (!AdbPairing.isWirelessAdbEnabled(context)) {
                check(AdbPairing.enableWirelessAdb(context)) {
                    "Wireless debugging is off and cannot be turned on without WRITE_SECURE_SETTINGS"
                }
            }

            val port = discoverPort(context, portDiscoveryTimeoutMs)
            check(port > 0) { "Wireless debugging did not publish a port to connect to" }

            val client = LocalAdbClient("127.0.0.1", port, AdbKeyManager(context))
            client.connect()
            AppLog.info(
                AppLogTags.WIRELESS_ADB,
                "Connected to the device's own adbd on port $port",
            )
            return WirelessAdbSession(client)
        }

        /**
         * Waits for a port, polling, because wireless debugging publishes one only once it is up.
         *
         * The first lookup is made after the setting is turned on but before adbd has necessarily
         * finished starting, so a single attempt would report a device with no transport on the one
         * boot where it was about to have one.
         */
        private fun discoverPort(context: Context, timeoutMs: Long): Int {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                val port = AdbPairing.discoverConnectPort(context, timeoutMs = PORT_LOOKUP_MILLIS)
                if (port > 0) return port
                Thread.sleep(RETRY_INTERVAL_MILLIS)
            }
            return -1
        }

        private const val PORT_LOOKUP_MILLIS = 10_000L
        private const val RETRY_INTERVAL_MILLIS = 2_000L
    }
}
