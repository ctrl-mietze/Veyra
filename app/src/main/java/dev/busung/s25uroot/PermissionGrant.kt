package ctrl.mietze.veyraroot

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Giving this app the one permission it cannot take for itself, over whatever shell the device offers.
 *
 * `WRITE_SECURE_SETTINGS` is development-flagged: the documented way to grant it is a cable and
 * `pm grant`, or `adb install -g`. That is not the only way in, and this object exists for the ones
 * that need no computer. The `shell` user is allowed to run `pm grant` on any package - it is the
 * mechanism `adb shell pm grant` itself uses - so a running Shizuku server is enough, and so is a
 * pairing with the device's own wireless debugging.
 *
 * The transports are tried cheapest first, and the order is the whole design:
 *
 * 1. **A root shell**, which covers both KernelSU's `su` and the bootstrap helper's daemon, so it works
 *    in the moment after an exploit and on a device where nothing else is running.
 * 2. **Shizuku's own shell.** No root needed, no cable needed, and it is the transport a device with
 *    neither root nor a pairing can still have.
 * 3. **The device's own adbd**, over a stored pairing. Last, and partly because of its own cost, but
 *    mostly because the permission is what lets this app turn wireless debugging on by itself - so this
 *    route only exists on a device where it is already on.
 *
 * Every attempt is verified by reading the permission back rather than by its exit code. `pm grant` can
 * report success while granting nothing, and the permission is the only thing that decides whether the
 * next screen works.
 */
internal object PermissionGrant {

    /**
     * The exact command, with the package quoted.
     *
     * Pure, so the quoting can be tested: the package name is the app's own, but a command assembled by
     * string concatenation is precisely where a name carrying a quote turns into a second command.
     */
    internal fun grantCommand(packageName: String): String =
        "pm grant ${shellQuote(packageName)} android.permission.WRITE_SECURE_SETTINGS"

    /** Whether this app already holds the permission. Read from the platform, not from a record. */
    internal fun hasPermission(context: Context): Boolean =
        AdbPairing.hasWriteSecureSettings(context)

    /**
     * Asks the device to grant the permission, through the first transport that answers.
     *
     * Never throws: a transport that is missing, or that dies mid-command, is the next one's turn.
     */
    suspend fun writeSecureSettings(context: Context): GrantOutcome = withContext(Dispatchers.IO) {
        if (hasPermission(context)) {
            return@withContext recorded(context, GrantOutcome.AlreadyGranted)
        }

        val command = grantCommand(context.packageName)

        KernelSuRuntime.rootShell(command)?.let { result ->
            return@withContext recorded(
                context,
                settle(context, GrantTransport.RootShell, result.exitCode, result.output),
            )
        }

        CVeyraController.shell(context, command)?.let { result ->
            return@withContext recorded(
                context,
                settle(
                    context,
                    GrantTransport.CVeyraShell,
                    result.exitCode,
                    result.output,
                ),
            )
        }

        // Cancellable rather than plain: the branch below writes to the shared app log, and a grant
        // request that was cancelled is not a transport that failed to answer.
        runCatchingCancellable {
            TemporaryWirelessAdb.use(context) {
                WirelessAdbSession.open(context).use { session ->
                    val result = session.shell(command)
                    settle(context, GrantTransport.LocalAdb, result.exitCode, result.output)
                }
            }
        }.fold(
            onSuccess = { outcome -> recorded(context, outcome) },
            onFailure = { error ->
                AppLog.warn(
                    AppLogTags.PERMISSIONS,
                    "WRITE_SECURE_SETTINGS: no transport answered " +
                        "(${error.javaClass.simpleName}: ${error.message})",
                )
                GrantOutcome.NoTransport
            },
        )
    }

    /**
     * Files what a grant attempt came to, and passes the outcome on.
     *
     * The outcome is the interesting part in the tab because of where it comes from: it is read back
     * from the platform rather than taken from the command's exit code, so a refusal here is a refusal
     * by the device and not by the shell that asked.
     */
    private fun recorded(context: Context, outcome: GrantOutcome): GrantOutcome {
        AppLog.record(
            level = if (outcome.granted) AppLogLevel.Info else AppLogLevel.Warn,
            tag = AppLogTags.PERMISSIONS,
            message = outcome.logLine(context),
        )
        return outcome
    }

    /**
     * What an attempt came to, decided by the permission rather than by what the command said.
     *
     * A transport that exits zero without the permission appearing has still failed, and saying so is
     * the difference between "the app cannot turn wireless debugging on" and a screen that quietly
     * carries on assuming it can.
     */
    private fun settle(
        context: Context,
        transport: GrantTransport,
        exitCode: Int,
        output: String,
    ): GrantOutcome = if (hasPermission(context)) {
        GrantOutcome.Granted(transport)
    } else {
        GrantOutcome.Refused(
            transport = transport,
            detail = output.trim().takeLast(180).ifBlank {
                context.getString(R.string.grant_silent_refusal, exitCode)
            },
        )
    }
}

/** Which shell a grant was attempted through. */
internal enum class GrantTransport(@StringRes val labelRes: Int) {
    RootShell(R.string.grant_transport_root),
    CVeyraShell(R.string.grant_transport_cveyra),
    LocalAdb(R.string.grant_transport_local_adb),
}

/** What came of asking for the permission. */
internal sealed interface GrantOutcome {

    /** It was already held, so nothing was attempted. */
    data object AlreadyGranted : GrantOutcome

    /** A transport granted it and the permission now reads as held. */
    data class Granted(val transport: GrantTransport) : GrantOutcome

    /**
     * A transport ran the command and the permission did not appear.
     *
     * [detail] is the transport's own last words, which is the only part that can say *why* - a
     * read-only system, a package name the manager does not know, a permission denied by policy.
     */
    data class Refused(val transport: GrantTransport, val detail: String) : GrantOutcome

    /** There was no root, no Shizuku and no pairing: nothing here could have asked. */
    data object NoTransport : GrantOutcome

    /** Whether the permission is held now, which is the only question callers act on. */
    val granted: Boolean get() = this is Granted || this is AlreadyGranted

    /** The run-log line for this outcome, which is where a failed grant has to be visible. */
    fun logLine(context: Context): String = when (this) {
        AlreadyGranted -> context.getString(R.string.log_grant_already)
        is Granted -> context.getString(
            R.string.log_grant_granted,
            context.getString(transport.labelRes),
        )
        is Refused -> context.getString(
            R.string.log_grant_failed,
            context.getString(transport.labelRes),
            detail,
        )
        NoTransport -> context.getString(R.string.log_grant_no_transport)
    }

    /** What to tell the user, for the screens that offer the grant by hand. */
    fun message(context: Context): String = when (this) {
        AlreadyGranted -> context.getString(R.string.grant_already_message)
        is Granted -> context.getString(
            R.string.grant_done_message,
            context.getString(transport.labelRes),
        )
        is Refused -> context.getString(
            R.string.grant_refused_message,
            context.getString(transport.labelRes),
            detail,
        )
        NoTransport -> context.getString(R.string.grant_no_transport_message)
    }
}
