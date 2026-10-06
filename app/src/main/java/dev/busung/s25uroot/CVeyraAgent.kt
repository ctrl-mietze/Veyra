package ctrl.mietze.veyraroot

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * CVeyra's persistent uid=2000 shell broker.
 *
 * Pairing is only needed to create the broker. The broker itself is a detached shell-owned process
 * listening on loopback, so it survives the Veyra app process being killed and does not require
 * wireless debugging to stay enabled after start.
 */
internal object CVeyraAgent {
    private const val PREFS = "cveyra_agent"
    private const val TOKEN = "token"
    private const val PORT = "port"
    private const val MIN_PORT = 37100
    private const val PORT_SPAN = 9000
    private const val EXIT_MARKER = "__CVEYRA_EXIT__="
    private const val MAX_OUTPUT_BYTES = 4 * 1024 * 1024

    // Destructive agent bootstrap (pid/handler directory) must be single-flight.
    private val ensureLock = ReentrantLock()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun token(context: Context): String {
        val stored = prefs(context).getString(TOKEN, null)
        if (!stored.isNullOrBlank()) return stored
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val generated = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(TOKEN, generated).commit()
        return generated
    }

    private fun port(context: Context): Int {
        val stored = prefs(context).getInt(PORT, -1)
        if (stored in MIN_PORT until MIN_PORT + PORT_SPAN) return stored
        return rotatePort(context)
    }

    private fun rotatePort(context: Context): Int {
        val value = MIN_PORT + SecureRandom().nextInt(PORT_SPAN)
        prefs(context).edit().putInt(PORT, value).commit()
        return value
    }

    private fun agentId(context: Context): String = token(context).take(16)
    private fun agentDir(context: Context): String = "/data/local/tmp/.cveyra-${agentId(context)}"
    private fun pidPath(context: Context): String = "${agentDir(context)}/agent.pid"

    /** Fast probe of an already-running broker. No ADB or settings changes and no noisy log. */
    fun isRunning(context: Context): Boolean =
        shellInternal(context, "id", timeoutMillis = 1_500, logFailure = false)
            ?.let { it.exitCode == 0 && it.output.contains("uid=2000") } == true

    /**
     * Ensures the detached broker exists. Existing broker first; authenticated local ADB only when
     * it has to be created again.
     */
    fun ensure(context: Context): Boolean = ensureLock.withLock {
        if (isRunning(context)) return@withLock true
        if (!AdbCredentialStore.hasStoredKey(context) || !AppPreferences.adbPaired(context)) {
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "CVeyra agent cannot start: no saved local-ADB identity/pairing",
            )
            return@withLock false
        }

        repeat(3) { attempt ->
            val selectedPort = if (attempt == 0) port(context) else rotatePort(context)
            val started = runCatching {
                TemporaryWirelessAdb.useBlocking(
                    context = context,
                    settleMillis = 500,
                    onLog = { line -> AppLog.debug(AppLogTags.WIRELESS_ADB, line) },
                ) {
                    WirelessAdbSession.open(
                        context,
                        portDiscoveryTimeoutMs = 20_000,
                    ).use { session ->
                        val identity = session.shell("id")
                        check(identity.exitCode == 0 && identity.output.contains("uid=2000")) {
                            "local ADB did not return the shell identity: ${identity.output}"
                        }
                        val start = session.shell(startCommand(context, selectedPort))
                        AppLog.info(
                            AppLogTags.WIRELESS_ADB,
                            "CVeyra agent start port=$selectedPort rc=${start.exitCode}",
                        )
                        check(start.exitCode == 0) {
                            "agent bootstrap returned ${start.exitCode}: ${start.output}"
                        }
                    }
                }
                true
            }.getOrElse { error ->
                AppLog.warn(
                    AppLogTags.WIRELESS_ADB,
                    "CVeyra agent start attempt ${attempt + 1}/3 failed: " +
                        "${error.javaClass.simpleName}: ${error.message}",
                )
                false
            }

            if (started) {
                repeat(10) {
                    if (isRunning(context)) return@withLock true
                    Thread.sleep(150)
                }
                AppLog.warn(
                    AppLogTags.WIRELESS_ADB,
                    "CVeyra agent bootstrap returned success but the loopback broker did not answer",
                )
            }
        }

        CVeyraPreferences.setRuntimeState(
            context,
            running = false,
            rootBroker = false,
            backend = CVeyraBackend.None,
        )
        false
    }

    /**
     * Runs one command through the detached uid=2000 broker.
     *
     * Authentication is a 256-bit secret stored only in Veyra's private data and in the shell-owned
     * mode-0700 handler. The TCP listener is loopback-only; the token still prevents another local app
     * from turning a guessed port into a shell.
     */
    fun shell(
        context: Context,
        command: String,
        timeoutMillis: Int = 30_000,
    ): ShizukuController.ShellResult? =
        shellInternal(context, command, timeoutMillis, logFailure = true)

    private fun shellInternal(
        context: Context,
        command: String,
        timeoutMillis: Int,
        logFailure: Boolean,
    ): ShizukuController.ShellResult? {
        val selectedPort = port(context)
        val auth = token(context)
        val encoded = Base64.encodeToString(command.toByteArray(), Base64.NO_WRAP)
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", selectedPort), 900)
                socket.soTimeout = timeoutMillis
                val writer = socket.getOutputStream().bufferedWriter()
                writer.append(auth).append('\n')
                writer.append(encoded).append('\n')
                writer.flush()
                runCatching { socket.shutdownOutput() }

                val output = readBounded(socket)
                val parsed = parseAnswer(output)
                if (parsed == null && logFailure) {
                    AppLog.warn(
                        AppLogTags.WIRELESS_ADB,
                        "CVeyra broker response on port $selectedPort had no valid exit marker",
                    )
                }
                parsed
            }
        } catch (error: Throwable) {
            if (logFailure) {
                AppLog.warn(
                    AppLogTags.WIRELESS_ADB,
                    "CVeyra broker command failed on port $selectedPort: " +
                        "${error.javaClass.simpleName}: ${error.message}",
                )
            }
            null
        }
    }

    /** Stops this app's detached broker without needing root. */
    fun stop(context: Context) {
        ensureLock.withLock {
            val dir = agentDir(context)
            val pid = pidPath(context)
            val command =
                "P=$(cat ${shellQuote(pid)} 2>/dev/null); " +
                    "[ -n \"${'$'}P\" ] && kill \"${'$'}P\" 2>/dev/null || true; " +
                    "rm -rf ${shellQuote(dir)}"
            shellInternal(context, command, timeoutMillis = 2_000, logFailure = false)
            CVeyraPreferences.setRuntimeState(
                context,
                running = false,
                rootBroker = false,
                backend = CVeyraBackend.None,
            )
        }
    }

    private fun parseAnswer(raw: String): ShizukuController.ShellResult? {
        val marker = raw.lastIndexOf(EXIT_MARKER)
        if (marker < 0) return null
        val code = raw.substring(marker + EXIT_MARKER.length)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.toIntOrNull()
            ?: return null
        val output = raw.substring(0, marker).trimEnd()
        return ShizukuController.ShellResult(code, output)
    }

    private fun readBounded(socket: Socket): String {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            check(output.size() + count <= MAX_OUTPUT_BYTES) {
                "CVeyra agent response exceeded $MAX_OUTPUT_BYTES bytes"
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun startCommand(context: Context, selectedPort: Int): String {
        val dir = agentDir(context)
        val handler = "$dir/handler.sh"
        val pid = "$dir/agent.pid"
        val handlerBytes = handlerScript(token(context)).toByteArray()
        val handlerBase64 = Base64.encodeToString(handlerBytes, Base64.NO_WRAP)
        return buildString {
            append("OLD=$(cat ").append(shellQuote(pid)).append(" 2>/dev/null); ")
            append("[ -n \"${'$'}OLD\" ] && kill \"${'$'}OLD\" 2>/dev/null || true; ")
            // Only touch this Veyra Root instance's broker directory. Another app may also
            // use the CVeyra name; a wildcard cleanup must never erase somebody else's state.
            append("rm -rf ").append(shellQuote(dir)).append("; ")
            append("mkdir -p ").append(shellQuote(dir)).append(" || exit 61; ")
            append("chmod 700 ").append(shellQuote(dir)).append(" || exit 62; ")
            append("printf %s ").append(shellQuote(handlerBase64))
                .append(" | /system/bin/toybox base64 -d > ").append(shellQuote(handler))
                .append(" || exit 63; ")
            append("chmod 700 ").append(shellQuote(handler)).append(" || exit 64; ")
            append("nohup /system/bin/toybox setsid -d /system/bin/toybox nc ")
                .append("-s 127.0.0.1 -p ").append(selectedPort)
                .append(" -L /system/bin/sh ").append(shellQuote(handler))
                .append(" >/dev/null 2>&1 </dev/null & ")
            append("echo $! > ").append(shellQuote(pid)).append("; ")
            append("chmod 600 ").append(shellQuote(pid)).append("; ")
            append("sleep 1; ")
            append("P=$(cat ").append(shellQuote(pid)).append(" 2>/dev/null); ")
            append("[ -n \"${'$'}P\" ] && kill -0 \"${'$'}P\" 2>/dev/null || exit 65; ")
            append("echo CVEYRA_AGENT_STARTED")
        }
    }

    private fun handlerScript(auth: String): String = """
#!/system/bin/sh
TOKEN=$auth
IFS= read -r AUTH || exit 90
if [ "${'$'}AUTH" != "${'$'}TOKEN" ]; then
  printf '${EXIT_MARKER}91\n'
  exit 0
fi
IFS= read -r PAYLOAD || {
  printf '${EXIT_MARKER}92\n'
  exit 0
}
CMD=$(/system/bin/printf '%s' "${'$'}PAYLOAD" | /system/bin/toybox base64 -d 2>/dev/null) || {
  printf '${EXIT_MARKER}93\n'
  exit 0
}
/system/bin/sh -c "${'$'}CMD" 2>&1
RC=${'$'}?
printf '\n${EXIT_MARKER}%s\n' "${'$'}RC"
""".trimIndent() + "\n"
}
