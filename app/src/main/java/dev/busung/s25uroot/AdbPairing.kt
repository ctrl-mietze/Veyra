package ctrl.mietze.veyraroot

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The port the pairing service published, from the output of a property read.
 *
 * Takes the first non-empty line so a stray warning on the way in cannot be read as a port, and
 * refuses anything outside the port range rather than passing it to a socket call.
 */
internal fun parseAdbTlsPort(raw: String): Int? = raw
    .lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)
    ?.toIntOrNull()
    ?.takeIf { it in 1..65535 }

/**
 * Wireless debugging's state, and the loopback port adbd is listening on.
 *
 * Everything here is about the device's own settings rather than about this app: whether wireless
 * debugging is on, whether this app may turn it on, and which port it publishes. The pairing exchange
 * itself is [AdbPairingClient], and the UX around it is [AdbPairingService].
 */
/** Which route can change the wireless-debugging setting on this device. */
internal enum class WirelessAdbEnableRoute {
    /** The setting directly, which needs WRITE_SECURE_SETTINGS. */
    Setting,

    /** Through a root shell, for a device with root but not the permission. */
    Root,

    /** Neither: the setting can only be changed by hand in Developer options. */
    Unavailable,
}

/**
 * Whether changing the setting is possible, and by which route.
 *
 * Pure, so the two things that decide it can be checked without a device. The order matters: the
 * setting is preferred where the permission exists because it is the same mechanism the Developer
 * options screen uses, while a root shell is the fallback for the device this app is mostly used on.
 */
internal fun wirelessAdbEnableRoute(
    permissionGranted: Boolean,
    rootAvailable: Boolean,
): WirelessAdbEnableRoute = when {
    permissionGranted -> WirelessAdbEnableRoute.Setting
    rootAvailable -> WirelessAdbEnableRoute.Root
    else -> WirelessAdbEnableRoute.Unavailable
}

/** What an attempt to switch wireless debugging on actually did. */
internal enum class WirelessAdbEnableResult {
    /** It read as on before anything was written, so nothing was. */
    AlreadyOn,

    /** It was written and reads back as on. */
    Enabled,

    /** The write did not change it, or could not be made at all: this device refuses it here. */
    Refused,

    /** Nothing could be written because this device has neither the permission nor root. */
    Unavailable,

    /**
     * The setting could not be read, so whether it is on cannot be said.
     *
     * Its own answer rather than a "no": the reads are exactly the ones a device may deny this app,
     * and a caller that treats an unreadable setting as an off one gives up on a transport that may be
     * working. The port is the authority then - it either answers or it does not.
     */
    Unknown,
}

/**
 * What a write did, from the readings around it.
 *
 * Pure, so the case that produced this can be checked without a device: `Settings.Global.putInt`
 * succeeds whether or not the device honours it, so a write that returns without throwing proves
 * nothing - only a reading after it does. That is the difference between the app saying "wireless
 * debugging is on, so the port must be coming" and the truth, which was that the switch never moved
 * and no port was ever going to appear.
 */
internal fun wirelessAdbEnableResult(
    stateBefore: Boolean?,
    route: WirelessAdbEnableRoute,
    stateAfter: Boolean?,
): WirelessAdbEnableResult = when {
    stateBefore == true -> WirelessAdbEnableResult.AlreadyOn
    stateAfter == true -> WirelessAdbEnableResult.Enabled
    route == WirelessAdbEnableRoute.Unavailable -> WirelessAdbEnableResult.Unavailable
    stateAfter == null -> WirelessAdbEnableResult.Unknown
    else -> WirelessAdbEnableResult.Refused
}

/**
 * Whether a connection test can be attempted at all.
 *
 * The rule the app got wrong: wireless debugging being **on** is itself enough. Neither the permission
 * nor root is needed to *use* the transport, only to *change* the setting - and a user who is opening
 * the pairing dialog in Developer options has necessarily just turned it on. Gating the test on the
 * permission reported a working transport as unusable.
 */
internal fun wirelessAdbUsable(
    wirelessDebuggingEnabled: Boolean,
    permissionGranted: Boolean,
    rootAvailable: Boolean,
): Boolean = wirelessDebuggingEnabled ||
    wirelessAdbEnableRoute(permissionGranted, rootAvailable) != WirelessAdbEnableRoute.Unavailable

/**
 * Wireless debugging's state, and the loopback port adbd is listening on.
 *
 * Everything here is about the device's own settings rather than about this app: whether wireless
 * debugging is on, whether this app may turn it on, and which port it publishes. The pairing exchange
 * itself is [AdbPairingClient], and the UX around it is [AdbPairingService].
 */
object AdbPairing {

    private const val ADB_WIFI_ENABLED_SETTING = "adb_wifi_enabled"

    /**
     * The command that grants this app the setting, shown when nothing else can turn it on.
     *
     * Built from the build's own application id rather than typed out, because this string is read by
     * a person and then pasted into a cable session: a stale literal would grant the setting to a
     * package that is not this one, and the screen that printed it would be the last to know.
     */
    val GRANT_COMMAND: String =
        "pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"

    private const val ENABLE_SETTING_COMMAND = "settings put global $ADB_WIFI_ENABLED_SETTING 1"
    private const val DISABLE_SETTING_COMMAND = "settings put global $ADB_WIFI_ENABLED_SETTING 0"
    private const val READ_SETTING_COMMAND = "settings get global $ADB_WIFI_ENABLED_SETTING"
    private const val ADB_TLS_PORT_PROPERTY = "service.adb.tls.port"
    private const val PROPERTY_READ_TIMEOUT_MS = 750L
    private const val PROPERTY_POLL_INTERVAL_MS = 1_000L

    /**
     * Turns wireless debugging on, by whichever route this device has.
     *
     * Two routes, because the two devices this matters for need different ones. **The setting directly**
     * is the ordinary way and needs `WRITE_SECURE_SETTINGS`, which is a development-flagged permission:
     * a rooted device can grant it (`pm grant`), and `adb install -g` grants it at install time. **A root
     * shell running `settings put`** is the fallback for the device this app is mostly used on, where
     * the permission is absent but root is not - and refusing there would mean the app cannot do for
     * itself what the user could do by hand in Developer options.
     *
     * Neither route is tried speculatively: a device with no permission and no root is reported as
     * unable, because that is the truth about it.
     */
    fun enableWirelessAdb(context: Context): Boolean = when (tryEnableWirelessAdb(context)) {
        // An unreadable setting cannot be called a success, and it cannot be called a failure either:
        // the attempt is made, and whatever is listening on the port decides. Callers that need the
        // distinction ask [tryEnableWirelessAdb].
        WirelessAdbEnableResult.AlreadyOn,
        WirelessAdbEnableResult.Enabled,
        WirelessAdbEnableResult.Unknown,
        -> true

        WirelessAdbEnableResult.Refused,
        WirelessAdbEnableResult.Unavailable,
        -> false
    }

    /**
     * Turns wireless debugging off.
     *
     * Only ever called to restore a state this app changed: leaving it on after a temporary use would
     * be leaving a shell port open that the user did not ask for.
     */
    fun disableWirelessAdb(context: Context): Boolean {
        // Asked first, for the same reason the enable path asks: a write proves nothing, and this one
        // has to be able to say "it is off" rather than "a write was made".
        if (wirelessAdbEnabledState(context) == false) return true
        putWirelessAdbEnabled(context, enabled = false) { value ->
            Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED_SETTING, value)
        }
        return wirelessAdbEnabledState(context) != true
    }

    private inline fun putWirelessAdbEnabled(
        context: Context,
        enabled: Boolean,
        direct: (Int) -> Unit,
    ): Boolean = when (
        wirelessAdbEnableRoute(
            permissionGranted = hasWriteSecureSettings(context),
            rootAvailable = rootIsAvailable(),
        )
    ) {
        WirelessAdbEnableRoute.Setting ->
            // The root route stays as a fallback: a granted permission can still be refused at the
            // write itself on a device with a restriction this app cannot see.
            runCatching { direct(if (enabled) 1 else 0) }.isSuccess || writeWirelessAdbThroughRoot(enabled)
        WirelessAdbEnableRoute.Root -> writeWirelessAdbThroughRoot(enabled)
        WirelessAdbEnableRoute.Unavailable -> false
    }

    private fun rootIsAvailable(): Boolean =
        runCatching { KernelSuRuntime.rootShell("id") != null }.getOrDefault(false)

    /**
     * The same change through a root shell, which is what a rooted device without the permission has.
     *
     * The outcome is read back rather than taken from the exit code: `settings put` can exit zero on a
     * device where the write did not take effect, and reporting success for a setting that did not
     * change would leave the transport waiting for a port that will never exist.
     */
    private fun writeWirelessAdbThroughRoot(enabled: Boolean): Boolean {
        val command = if (enabled) ENABLE_SETTING_COMMAND else DISABLE_SETTING_COMMAND
        val result = runCatching { KernelSuRuntime.rootShell(command) }.getOrNull() ?: return false
        if (result.exitCode != 0) {
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "Wireless debugging could not be changed through root: ${result.output.take(120)}",
            )
            return false
        }
        return readWirelessAdbState() == enabled
    }

    /**
     * Whether wireless debugging is on, asked of the settings and then of a root shell.
     *
     * An unreadable setting is not the same as a disabled one, so a device this app cannot read is
     * read through root instead of being reported as off - and only a device that answers neither way
     * is treated as off, because the caller's next move (turn it on) is the right one then anyway.
     */
    fun isWirelessAdbEnabled(context: Context): Boolean = wirelessAdbEnabledState(context) ?: false

    /** Wireless debugging's setting as a reading that is allowed to say "could not be read". */
    fun wirelessAdbEnabledState(context: Context): Boolean? {
        val direct = runCatching {
            Settings.Global.getString(context.contentResolver, ADB_WIFI_ENABLED_SETTING)
        }.getOrNull()
        direct?.let { return it.trim() == "1" }
        return readWirelessAdbState()
    }

    /**
     * Turns wireless debugging on, and says what actually happened rather than that a write was made.
     *
     * The write is only half of it. A device that ignores the setting - or refuses it outright - leaves
     * this function returning success under the old shape, and the caller then waits out a time-limited
     * port search for a listener nobody turned on. Reading the setting back is what makes the difference
     * visible, and it is why this reports five outcomes where a boolean reported two.
     */
    internal fun tryEnableWirelessAdb(context: Context): WirelessAdbEnableResult {
        val before = wirelessAdbEnabledState(context)
        if (before == true) return WirelessAdbEnableResult.AlreadyOn
        // Asked in this order: a granted permission answers the question on its own, and the root probe
        // behind the other arm starts a shell that can sit on a grant prompt. A device with the
        // permission should not wait for that to learn it does not need it.
        val route = if (hasWriteSecureSettings(context)) {
            WirelessAdbEnableRoute.Setting
        } else {
            wirelessAdbEnableRoute(permissionGranted = false, rootAvailable = rootIsAvailable())
        }
        if (route != WirelessAdbEnableRoute.Unavailable) {
            when (route) {
                WirelessAdbEnableRoute.Setting ->
                    // The root route stays as a fallback: a granted permission can still be refused at
                    // the write itself on a device with a restriction this app cannot see.
                    runCatching {
                        Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED_SETTING, 1)
                    }.isSuccess || writeWirelessAdbThroughRoot(true)
                WirelessAdbEnableRoute.Root -> writeWirelessAdbThroughRoot(true)
                WirelessAdbEnableRoute.Unavailable -> Unit
            }
        }
        return wirelessAdbEnableResult(before, route, wirelessAdbEnabledState(context))
    }

    private fun readWirelessAdbState(): Boolean? {
        val result = runCatching { KernelSuRuntime.rootShell(READ_SETTING_COMMAND) }.getOrNull()
            ?: return null
        if (result.exitCode != 0) return null
        return when (result.output.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The local port adbd is listening on, without making Wi-Fi a prerequisite.
     *
     * The published property is asked first, because it works with no network at all and is
     * authoritative about this boot. mDNS is the fallback for a device that does not publish it, and
     * both are polled until the deadline because the port only exists while wireless debugging is up -
     * a lookup that raced the setting being turned on would otherwise report nothing and give up.
     *
     * The result is only ever connected to over loopback.
     */
    fun discoverConnectPort(context: Context, timeoutMs: Long = 15_000): Int {
        readTlsPortProperty()?.let { port ->
            AppLog.info(AppLogTags.WIRELESS_ADB, "Port from $ADB_TLS_PORT_PROPERTY: $port")
            return port
        }

        val found = CountDownLatch(1)
        val discoveredPort = AtomicInteger(-1)
        val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { discovered ->
            if (discovered > 0 && discoveredPort.compareAndSet(-1, discovered)) found.countDown()
        }
        mdns.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(0L))
            while (discoveredPort.get() <= 0) {
                readTlsPortProperty()?.let { port ->
                    discoveredPort.compareAndSet(-1, port)
                    AppLog.info(AppLogTags.WIRELESS_ADB, "Port found without mDNS: $port")
                    break
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val waitMillis = minOf(
                    PROPERTY_POLL_INTERVAL_MS,
                    TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1L),
                )
                if (found.await(waitMillis, TimeUnit.MILLISECONDS)) break
            }
        } finally {
            mdns.stop()
        }

        val port = discoveredPort.get().takeIf { it > 0 } ?: readTlsPortProperty() ?: -1
        if (port <= 0) {
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "No local ADB port was found, by property or by mDNS",
            )
        }
        return port
    }

    /**
     * Whether adbd currently accepts this app's key.
     *
     * Asked of the device by running `id` over the transport, because the stored "paired" flag only
     * records that a pairing once happened; the device can forget the key at any time.
     */
    fun testConnection(context: Context): Boolean {
        val port = discoverConnectPort(context)
        if (port <= 0) return false
        return runCatching {
            LocalAdbClient.shellOnce("127.0.0.1", port, AdbKeyManager(context), "id")
                .output
                .contains("uid=")
        }.getOrElse { error ->
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "Connection test failed: ${error.message}",
            )
            false
        }
    }

    private fun readTlsPortProperty(): Int? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", ADB_TLS_PORT_PROPERTY)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(PROPERTY_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            runCatching { process.waitFor(100, TimeUnit.MILLISECONDS) }
            if (process.isAlive) process.destroyForcibly()
            return@runCatching null
        }
        if (process.exitValue() != 0) return@runCatching null
        parseAdbTlsPort(process.inputStream.bufferedReader().use { it.readText() })
    }.getOrNull()
}
