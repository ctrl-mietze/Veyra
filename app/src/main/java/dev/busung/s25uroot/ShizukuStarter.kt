package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File

/** How a start attempt ended, and by which route. */
internal data class ShizukuStartOutcome(
    val started: Boolean,
    val method: String? = null,
    val detail: String = "",
)

/**
 * The root shell [ShizukuStarter] wants, from KernelSU, or the refusal it reads as "no root here".
 *
 * A missing root shell is reported as a failed command rather than thrown, so the starter's own routing
 * stays in charge of what the user is told: it is the difference between "no root, so take the pairing
 * route" and "the start attempt broke". Shared by the two unattended callers - the boot service and the
 * install gate - so the route a boot takes cannot depend on which of them asked.
 */
internal fun kernelSuRootShell(context: Context): (String) -> ShizukuController.ShellResult =
    { command ->
        KernelSuRuntime.rootShell(command) ?: ShizukuController.ShellResult(
            NO_ROOT_SHELL_EXIT,
            context.getString(R.string.error_no_root_shell),
        )
    }

/**
 * The command that starts a modern Shizuku build's server for this app.
 *
 * Shizuku ships its starter as a native library inside its own APK, and it needs the path of the
 * APK it belongs to, which is what the `--apk=` argument is for. Kept as a function of its inputs so
 * the quoting and the argument shape can be tested without a device.
 */
internal fun shizukuStarterCommand(starterPath: String, apkPath: String): String =
    "${shellQuote(starterPath)} --apk=${shellQuote(apkPath)}"

/**
 * Starts Shizuku's server without a computer, using the root this app already put on the device.
 *
 * Shizuku is what this app uses to run the payload as shell, and it is normally started by the user
 * over adb - so after a reboot the transport is simply gone until someone finds a cable. Once
 * KernelSU is loaded that is unnecessary: KernelSU's own root shell can run Shizuku's starter, which
 * is what this does. It is deliberately not a general "start Shizuku" button for a device with no
 * root: without root or adb the app has no way to start a privileged process, and pretending
 * otherwise would only produce a button that does nothing.
 *
 * Current Shizuku builds expose the native starter; older or manually installed builds may have
 * dropped a `start.sh` on shared storage. The native route is always preferred and a missing legacy
 * script is not an error, so a device that has neither is reported as such once rather than as two
 * failures.
 */
internal object ShizukuStarter {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val BINDER_RACE_PROBE_MILLIS = 500L

    /** A binder that appears just after a probe means another starter won the race, not a failure. */
    private val LEGACY_START_PATHS = listOf(
        "/storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
        "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
    )

    /**
     * [shell] runs a command as root and is expected to be KernelSU's shell. The binder is re-probed
     * immediately before every launch because a start from this app racing another starter (the
     * Shizuku app itself, or a previous boot's attempt) is otherwise indistinguishable from one that
     * never took effect.
     *
     * The whole attempt runs under [ShizukuStartCoordinator] rather than a process-local lock, because
     * the callers are in different processes: the boot service in its own, the settings screen in the
     * app's, and the automatic install in the gate's.
     */
    suspend fun start(
        context: Context,
        shell: (String) -> ShizukuController.ShellResult,
        binderTimeoutMillis: Long = DEFAULT_BINDER_TIMEOUT_MILLIS,
        onLog: (String) -> Unit = {},
    ): ShizukuStartOutcome = ShizukuStartCoordinator.withStartLock(context) {
        // Every route reports through this one callback, so this is the one place that also feeds the
        // app log: a start is a sequence of decisions rather than a single result, and the tab is where
        // that sequence is read afterwards.
        val log = appLogged(onLog)
        if (ShizukuController.pingUntilRunning(BINDER_RACE_PROBE_MILLIS)) {
            log("[+] Shizuku is already running; no starter needed")
            return@withStartLock ShizukuStartOutcome(started = true, method = METHOD_ALREADY_RUNNING)
        }

        val tokenConfigured = AppPreferences.shizukuAutomationToken(context).isNotBlank()
        when (
            shizukuStartRoute(
                rootShellAvailable = hasRoot(shell),
                localAdbPaired = hasLocalAdbCredential(context),
                tokenConfigured = tokenConfigured,
            )
        ) {
            // Shizuku's own starter, in the shell this app was handed - KernelSU's - which is the route
            // whose result the app can watch for itself.
            ShizukuStartRoute.NativeStarter ->
                startWithStarter(context, shell, binderTimeoutMillis, log)

            // No root, but this app has an adb identity of its own on the device: the same starter in
            // the shell adbd hands out, over loopback, with no network in the way.
            ShizukuStartRoute.LocalAdb ->
                startThroughDeviceAdb(context, tokenConfigured, binderTimeoutMillis, log)

            ShizukuStartRoute.AuthenticatedIntent,
            ShizukuStartRoute.Unavailable,
            -> startWithoutRoot(context, tokenConfigured, binderTimeoutMillis, log)
        }
    }

    /**
     * Files a start attempt's own commentary in the app log, and passes it on.
     *
     * `[!]` is a warning rather than news: those are the lines that say an attempt did not work, and on
     * a device with no ADB they are the only account of why.
     */
    private fun appLogged(onLog: (String) -> Unit): (String) -> Unit = { line ->
        AppLog.record(
            level = if (line.startsWith("[!]")) AppLogLevel.Warn else AppLogLevel.Info,
            tag = AppLogTags.SHIZUKU,
            message = line,
        )
        onLog(line)
    }

    /**
     * Runs Shizuku's starter in [shell], native first and the legacy script second.
     *
     * Both are Shizuku's own routes: `libshizuku.so --apk=<apk>` for current builds, and the `start.sh`
     * an older or manually installed build may have left on shared storage. Neither cares which shell it
     * is running in, which is the whole point - the same sequence serves KernelSU's root shell and the
     * shell the device's own adbd hands out, so the two routes cannot drift apart.
     */
    private suspend fun startWithStarter(
        context: Context,
        shell: (String) -> ShizukuController.ShellResult,
        binderTimeoutMillis: Long,
        onLog: (String) -> Unit,
    ): ShizukuStartOutcome {
        val native = nativeStarter(context)
        if (native == null) {
            onLog("[*] The Shizuku app is not installed; only a legacy starter could be used")
        } else if (shell("test -f ${shellQuote(native.starterPath)}").exitCode != 0) {
            onLog("[*] This Shizuku build has no native starter; checking the legacy script")
        } else {
            if (ShizukuController.pingUntilRunning(BINDER_RACE_PROBE_MILLIS)) {
                return ShizukuStartOutcome(started = true, method = METHOD_ALREADY_RUNNING)
            }
            onLog("[*] Starting Shizuku with its own native starter")
            val result = shell(shizukuStarterCommand(native.starterPath, native.apkPath))
            if (result.exitCode == 0 && ShizukuController.pingUntilRunning(binderTimeoutMillis)) {
                onLog("[+] Shizuku started and its binder answered")
                return ShizukuStartOutcome(started = true, method = METHOD_NATIVE)
            }
            onLog(
                "[!] The native starter " +
                    if (result.exitCode != 0) {
                        "exited ${result.exitCode}${result.output.trim().takeLast(240).let {
                            if (it.isBlank()) "" else ": $it"
                        }}; checking the legacy script"
                    } else {
                        "finished but no binder followed; checking the legacy script"
                    },
            )
        }

        if (ShizukuController.pingUntilRunning(BINDER_RACE_PROBE_MILLIS)) {
            return ShizukuStartOutcome(started = true, method = METHOD_ALREADY_RUNNING)
        }

        val legacy = legacyStartScript(shell)
        if (legacy != null) {
            onLog("[*] Starting Shizuku with the legacy start.sh")
            val result = shell("sh ${shellQuote(legacy)} 2>&1")
            if (result.exitCode == 0 && ShizukuController.pingUntilRunning(binderTimeoutMillis)) {
                onLog("[+] Shizuku started and its binder answered")
                return ShizukuStartOutcome(started = true, method = METHOD_LEGACY)
            }
            val detail = result.output.trim().takeLast(240).ifBlank { "exit ${result.exitCode}" }
            val reason = "the legacy start.sh did not produce a binder: $detail"
            onLog("[!] $reason")
            return ShizukuStartOutcome(started = false, method = METHOD_LEGACY, detail = reason)
        }

        val detail = "no Shizuku starter on this device produced a binder"
        onLog("[!] $detail")
        return ShizukuStartOutcome(started = false, detail = detail)
    }

    /**
     * The no-root route: this app's own adb identity, used as the shell.
     *
     * A paired device has an authenticated adb connection to its *own* adbd, which is a shell-uid
     * context - exactly what Shizuku's starter needs - and the connection is to `127.0.0.1`, so no
     * computer is involved. What it does not remove is the network: the port it dials belongs to wireless
     * debugging, which the framework turns off while no Wi-Fi network is connected, so this route is as
     * dependent on Wi-Fi as the request route - see [NetworkReach] and [startNeedsNetworkFirst].
     *
     * Wireless debugging is turned on for the attempt and off again afterwards, the same window the run
     * transport uses, so the device is not left with a shell port open for the sake of one command.
     */
    private suspend fun startThroughDeviceAdb(
        context: Context,
        tokenConfigured: Boolean,
        binderTimeoutMillis: Long,
        onLog: (String) -> Unit,
    ): ShizukuStartOutcome {
        onLog("[*] No root: running Shizuku's starter in the device's own adb shell")
        // Cancellable rather than plain: everything below this line is work, and a cancelled start that
        // read its own cancellation as "that shell did not work" would go on to make the request route
        // too, from inside a coroutine nobody is waiting for any more.
        val outcome = runCatchingCancellable {
            TemporaryWirelessAdb.use(context, onLog = onLog) {
                WirelessAdbSession.open(context, portDiscoveryTimeoutMs = LOCAL_ADB_PORT_TIMEOUT_MILLIS)
                    .use { session ->
                        // Shizuku's starter needs the same domain a payload does, and a shell that is not
                        // in it answers every command with a refusal that reads like Shizuku refusing.
                        localAdbShellIdentityFailure(session.shell("id"))?.let { reason ->
                            error(context.getString(R.string.error_local_adb_shell, reason))
                        }
                        onLog(context.getString(R.string.log_local_adb_shell_ready))
                        startWithStarter(
                            context = context,
                            shell = { command ->
                                val result = session.shell(command)
                                ShizukuController.ShellResult(result.exitCode, result.output)
                            },
                            binderTimeoutMillis = binderTimeoutMillis,
                            onLog = onLog,
                        )
                    }
            }
        }.getOrElse { error ->
            val detail = error.message ?: error.javaClass.simpleName
            onLog("[!] The device's own adb shell is not usable: $detail")
            null
        }
        if (outcome?.started == true) return outcome

        // No starter worked in that shell, but the request route has different requirements, so it is
        // still worth making - and with no token it is also the call that says in words what this
        // device has to work with.
        val requested = startWithoutRoot(context, tokenConfigured, binderTimeoutMillis, onLog)
        return when {
            requested.started -> requested
            // With no token there was nothing to request, so the failure worth reporting is the one
            // from the attempt that was actually made.
            !tokenConfigured && outcome != null -> outcome
            else -> requested
        }
    }

    /**
     * Whether this app has an adb identity of its own on the device.
     *
     * Asked as two facts, the way the run transport asks it: a completed pairing leaves a key and a
     * flag, and the flag on its own is a stale note if the key has gone.
     */
    private fun hasLocalAdbCredential(context: Context): Boolean =
        AdbCredentialStore.hasStoredKey(context) && AppPreferences.adbPaired(context)

    /**
     * The two routes that need no root: asking Shizuku to start itself when a token is stored, and
     * saying plainly that nothing can be done when it is not.
     *
     * The distinction matters to the person reading it. "No starter produced a binder" after a failed
     * token describes a wrong token; the same words on a device with no root and no token describe a
     * device where this was never possible, and only one of those is worth retrying.
     */
    private suspend fun startWithoutRoot(
        context: Context,
        tokenConfigured: Boolean,
        binderTimeoutMillis: Long,
        onLog: (String) -> Unit,
    ): ShizukuStartOutcome {
        if (!tokenConfigured) {
            val detail =
                "this device has no root, no paired wireless debugging identity, and no Shizuku start " +
                    "token, so Shizuku cannot be started from here"
            onLog("[!] $detail")
            return ShizukuStartOutcome(started = false, detail = detail)
        }
        val outcome = ShizukuIntentStarter.start(context, binderTimeoutMillis, onLog)
        return ShizukuStartOutcome(
            started = outcome.started,
            method = if (outcome.attempted) METHOD_AUTHENTICATED_INTENT else null,
            detail = outcome.detail,
        )
    }

    /**
     * Whether the transport in [shell] is running commands as root.
     *
     * Asked of the device rather than assumed from the caller: a caller that falls back to a
     * non-root transport hands in a shell that answers every command with a refusal, and a start
     * attempt routed to Shizuku's native starter through it would fail for a reason that looks like
     * Shizuku's fault.
     */
    private fun hasRoot(shell: (String) -> ShizukuController.ShellResult): Boolean {
        val result = runCatching { shell("id") }.getOrNull() ?: return false
        return result.exitCode != NO_ROOT_SHELL_EXIT && result.output.contains("uid=0")
    }

    /** Where Shizuku's own starter and its APK live, when Shizuku is installed. */
    private fun nativeStarter(context: Context): NativeStarter? {
        val info = runCatching {
            context.packageManager.getApplicationInfo(SHIZUKU_PACKAGE, 0)
        }.getOrNull() ?: return null
        val libraryDir = info.nativeLibraryDir?.takeIf(String::isNotBlank) ?: return null
        val apkPath = info.sourceDir?.takeIf(String::isNotBlank) ?: return null
        return NativeStarter(
            starterPath = File(libraryDir, "libshizuku.so").absolutePath,
            apkPath = apkPath,
        )
    }

    /**
     * The first legacy start script that exists, asked of the device rather than of the app, because
     * shared storage is a different filesystem from the one the app sees.
     */
    private fun legacyStartScript(
        shell: (String) -> ShizukuController.ShellResult,
    ): String? = firstPresent(LEGACY_START_PATHS) { path ->
        shell("test -f ${shellQuote(path)}").exitCode == 0
    }

    private data class NativeStarter(val starterPath: String, val apkPath: String)

    private const val METHOD_ALREADY_RUNNING = "already running"
    internal const val METHOD_NATIVE = "native starter"
    internal const val METHOD_LEGACY = "legacy start.sh"
    internal const val METHOD_AUTHENTICATED_INTENT = "Shizuku start request"
    internal const val DEFAULT_BINDER_TIMEOUT_MILLIS = 20_000L

    /**
     * How long the adb route waits for adbd to publish the port it is listening on.
     *
     * Shorter than the run transport's, because this is a start attempt rather than a run: the port is
     * read from the system property adbd sets when wireless debugging comes up, which is there as soon
     * as the listener is, and a start that has waited this long is not going to be helped by waiting
     * more.
     */
    private const val LOCAL_ADB_PORT_TIMEOUT_MILLIS = 20_000L
}

/**
 * The exit code a command reports when there was no root shell to run it in.
 *
 * It is deliberately outside the range a real command uses, so "never ran" cannot be read as
 * "ran and failed with something specific".
 */
internal const val NO_ROOT_SHELL_EXIT = 127

/** The first candidate that a probe accepts, in order; null when none do. */
internal fun firstPresent(candidates: List<String>, probe: (String) -> Boolean): String? =
    candidates.firstOrNull(probe)
