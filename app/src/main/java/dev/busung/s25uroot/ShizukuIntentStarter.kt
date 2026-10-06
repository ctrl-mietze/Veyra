package ctrl.mietze.veyraroot

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Which way a start attempt should be made, from the things that decide it. */
internal enum class ShizukuStartRoute {
    /** Root exists, so Shizuku's own starter can be run where it is verifiable. */
    NativeStarter,

    /**
     * No root, but this app is paired with the device's own adbd, so the same starter can be run in the
     * shell adbd hands out.
     *
     * It is the same process Shizuku's own starter would be, started by this app, so the result is
     * verifiable in the same way root's is - and it needs no computer and no network, which the route
     * below cannot say for itself.
     */
    LocalAdb,

    /** No root and no adb identity: the token broadcast is the only way left. */
    AuthenticatedIntent,

    /** Nothing the app can start Shizuku with. */
    Unavailable,
}

/**
 * The route a start takes, as a function of the facts that decide it.
 *
 * Pure, so the choice can be tested without a device, and worth testing because these routes exist
 * precisely in the states that are hardest to reproduce: a device with no root, or a device with no
 * network.
 *
 * The order is by how much the app can see for itself. **Root** starts Shizuku's own starter, so the
 * app watches the process it started. **Local adb** does the same thing in the shell the device's own
 * adbd hands out, reached over loopback, which needs no root. **The token** is a request to another app
 * that can only be answered by waiting for a binder.
 *
 * Both of the no-root routes need one thing that root does not, and it is easy to miss because it is not
 * part of either route's own logic: they go through wireless debugging, and wireless debugging cannot be
 * on without a connected Wi-Fi network ([NetworkReach]). Reaching adbd over loopback is what that route
 * needs instead of a *computer*; it does not remove the network requirement, because the port it dials is
 * published by adbd's TLS listener, which the framework only starts for wireless debugging that it has
 * been able to turn on.
 */
internal fun shizukuStartRoute(
    rootShellAvailable: Boolean,
    localAdbPaired: Boolean,
    tokenConfigured: Boolean,
): ShizukuStartRoute = when {
    rootShellAvailable -> ShizukuStartRoute.NativeStarter
    localAdbPaired -> ShizukuStartRoute.LocalAdb
    tokenConfigured -> ShizukuStartRoute.AuthenticatedIntent
    else -> ShizukuStartRoute.Unavailable
}

/**
 * Whether a start attempt made right now would be one thrown away waiting for a network that is not
 * there.
 *
 * Root is the only route that can work with no network: Shizuku's own starter runs in KernelSU's shell
 * and is a local process from end to end. Everything else - this app's adb identity, Shizuku's own start
 * request, and Shizuku starting itself at boot - goes through wireless debugging, and the framework keeps
 * wireless debugging off while no Wi-Fi network is connected ([NetworkReach]). So an attempt made in
 * that state is one of a bounded few spent on a question the device cannot answer.
 *
 * Note what this means for a device with no route at all: it is still a device that *could* be brought
 * up by nothing on this side - Shizuku's own boot receiver, which needs wireless debugging, and therefore
 * a network too. Which is why this asks about the network rather than about whether an attempt would be
 * made.
 *
 * Pure, so the matrix can be checked without a device: it is the difference between waiting and a
 * refusal that names the wrong reason, and neither of those states can be produced by hand.
 */
internal fun startNeedsNetworkFirst(
    route: ShizukuStartRoute,
    networkConnected: Boolean,
): Boolean = !networkConnected && route != ShizukuStartRoute.NativeStarter

/**
 * How long a boot holds for a network once nothing can be tried without one.
 *
 * Measured separately from the window Shizuku itself is given ([AutoRootService]'s, and the boot
 * service's own attempt count), because the two are not the same question: one is how long Shizuku has
 * had a chance, the other is how long the device has been given to provide one. Only the first is spent
 * while there is no chance, so a boot with Wi-Fi off does not burn its Shizuku window on attempts the
 * framework cannot answer.
 *
 * Five minutes, and bounded, because the network this waits for is usually the user's own: a phone that
 * boots with Wi-Fi off is a phone whose owner will turn it on, and the notification says what is being
 * waited for the whole time. Waiting forever would hold a foreground service and a wake lock for as long
 * as the phone was away from a network, which is not a boot install's business.
 */
internal const val NETWORK_WAIT_MILLIS = 5 * 60 * 1_000L

/**
 * Whether a boot is worth starting Shizuku on at all.
 *
 * The start used to be triggered from inside the boot's root check, which made it root-only in
 * practice: a device with a stored token and no root never asked Shizuku to start, even though the
 * token is exactly the route that does not need root. The same facts as the route decide it, and a
 * boot with none of them is left alone rather than told, once per reboot, that nothing can be done.
 */
internal fun shizukuBootStartWorthAttempting(
    rootAlreadyActive: Boolean,
    localAdbPaired: Boolean,
    tokenConfigured: Boolean,
): Boolean = shizukuStartRoute(rootAlreadyActive, localAdbPaired, tokenConfigured) !=
    ShizukuStartRoute.Unavailable

/**
 * Starts Shizuku by asking the Shizuku app itself, for devices where the app has no root to work with.
 *
 * Shizuku cannot normally be started by another app - that is the whole point of its security model -
 * so this is deliberately narrow. It exists for builds whose manager exposes an authenticated start
 * broadcast, and it does nothing at all unless the user has stored the matching token in settings.
 * Without a token the app would be sending a broadcast it cannot authenticate, which is a request to
 * start a privileged process that nobody should honour.
 *
 * The broadcast is package-scoped, so it can only be delivered to the Shizuku package, and the token
 * is never logged - not on success, and not in the failure detail.
 */
internal object ShizukuIntentStarter {

    internal data class Outcome(
        val started: Boolean,
        val attempted: Boolean,
        val detail: String = "",
    )

    suspend fun start(
        context: Context,
        binderTimeoutMillis: Long,
        onLog: (String) -> Unit = {},
    ): Outcome {
        val token = AppPreferences.shizukuAutomationToken(context).trim()
        if (token.isBlank()) {
            onLog("[*] No Shizuku start token is configured, so the app cannot ask Shizuku to start")
            return Outcome(
                started = false,
                attempted = false,
                detail = "Shizuku start token is not configured",
            )
        }

        // Deliberately no component pre-query: a package-scoped broadcast is harmless when nothing
        // listens, while querying can report absence for a receiver that exists but is disabled.
        val intent = Intent(START_ACTION)
            .setPackage(SHIZUKU_PACKAGE)
            .putExtra(AUTH_EXTRA, token)

        return try {
            context.sendBroadcast(intent)
            onLog("[*] Asked Shizuku to start itself with the configured token")
            if (ShizukuController.pingUntilRunning(binderTimeoutMillis)) {
                onLog("[+] Shizuku started itself and its binder answered")
                Outcome(started = true, attempted = true)
            } else {
                val detail =
                    "the Shizuku start request was sent but no binder followed; check the token and " +
                        "that this Shizuku build accepts start requests"
                onLog("[!] $detail")
                Outcome(started = false, attempted = true, detail = detail)
            }
        } catch (error: Throwable) {
            val detail = error.message ?: error.javaClass.simpleName
            onLog("[!] Asking Shizuku to start itself failed: $detail")
            Outcome(started = false, attempted = true, detail = detail)
        }
    }

    /**
     * Whether Shizuku's own start-on-boot receiver is enabled.
     *
     * Reported rather than acted on: if Shizuku already starts itself at boot, this app starting it
     * too is redundant, and knowing that is the difference between "my boot start is broken" and "it
     * was never needed".
     */
    fun ownBootReceiverEnabled(context: Context): Boolean {
        val component = ComponentName(SHIZUKU_PACKAGE, BOOT_RECEIVER_CLASS)
        val manager = context.packageManager
        return runCatching {
            when (manager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                -> false
                else -> {
                    @Suppress("DEPRECATION")
                    manager.getReceiverInfo(component, 0).enabled
                }
            }
        }.getOrDefault(false)
    }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val START_ACTION = "$SHIZUKU_PACKAGE.START"
    private const val AUTH_EXTRA = "auth"
    private const val BOOT_RECEIVER_CLASS = "moe.shizuku.manager.receiver.BootCompleteReceiver"
}
