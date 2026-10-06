package ctrl.mietze.veyraroot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wireless debugging as a window, never as device state.
 *
 * The app turns it on to get a shell and turns it off again, because leaving it on leaves a shell
 * port open that the user did not ask for. Two things make that reliable rather than hopeful:
 *
 * - A short grace period between consecutive users. The transport is handed over - the exploit's
 *   session ends and the KernelSU handoff starts within a second or two of it - and tearing adbd down
 *   in that gap would break the handoff that follows.
 * - An alarm armed *before* the setting is turned on, so a process killed during the window still has
 *   something that turns it back off. The alarm is set well past the longest run this app starts, so
 *   the failsafe can never fire while a legitimate run is still using the transport.
 *
 * In-process users are serialized, because two of them can be alive at once (a boot-time Shizuku start
 * and an automatic install): without the lock, whoever finished first would turn wireless debugging off
 * while the other still held an authenticated session.
 */
internal object TemporaryWirelessAdb {

    private val sessionMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cleanupLock = Any()
    private var pendingGraceDisable: Runnable? = null

    /**
     * Turns wireless debugging on if it is off. Returns false when this device would not let it be.
     *
     * The outcome is read back from the setting rather than taken from the write, because a write that
     * is ignored is indistinguishable from one that worked until something reads it - and what follows
     * this call is a search for a port that only exists if the switch really moved. Naming the reason
     * here is the difference between "the app could not turn it on, do it in Developer options" and a
     * silent wait for a listener nobody started.
     */
    fun begin(context: Context, onLog: (String) -> Unit = {}): Boolean =
        when (AdbPairing.tryEnableWirelessAdb(context)) {
            WirelessAdbEnableResult.AlreadyOn -> {
                cancelGraceDisable()
                armCleanup(context)
                // Do not erase an ownership bit from the previous Veyra transport window. During
                // the handoff grace period adbd is intentionally still on; a second user arriving
                // there sees AlreadyOn even though Veyra is still the component that enabled it.
                // Clearing the bit here made the second user "adopt" the setting permanently.
                val stillOwnedByVeyra = AppPreferences.wirelessAdbOwnedByApp(context)
                onLog(
                    if (stillOwnedByVeyra) {
                        "[*] Wireless debugging is still on from Veyra's previous transport window; ownership retained"
                    } else {
                        "[*] Wireless debugging was already on before Veyra; it will be left on"
                    },
                )
                true
            }

            WirelessAdbEnableResult.Enabled -> {
                cancelGraceDisable()
                armCleanup(context)
                AppPreferences.setWirelessAdbOwnedByApp(context, true)
                onLog("[*] Wireless debugging enabled for this run only")
                true
            }

            // Armed and marked, because the write may have taken even though the read did not: the port
            // is the authority on an unreadable setting, and restoring something this app may have
            // changed is the safe side of that doubt.
            WirelessAdbEnableResult.Unknown -> {
                cancelGraceDisable()
                armCleanup(context)
                AppPreferences.setWirelessAdbOwnedByApp(context, true)
                onLog(
                    "[!] Wireless debugging's setting could not be read; trying the connection " +
                        "anyway, because the app may not be allowed to look rather than have it off",
                )
                true
            }

            WirelessAdbEnableResult.Refused -> {
                cancelCleanup(context)
                onLog(
                    "[!] Wireless debugging is off and turning it on did not take effect: switch it on " +
                        "in Developer options → Wireless debugging, then try again",
                )
                false
            }

            WirelessAdbEnableResult.Unavailable -> {
                cancelCleanup(context)
                onLog(
                    "[!] Wireless debugging is off and this device gives the app no way to turn it on: " +
                        "grant it with `${AdbPairing.GRANT_COMMAND}`, or switch it on in Developer options",
                )
                false
            }
        }

    /**
     * Blocking bridge for legacy/non-coroutine callers.
     *
     * Crucially this uses the same [sessionMutex] as coroutine users, so CVeyra, pairing, diagnostics
     * and root staging can no longer independently toggle adbd underneath one another.
     */
    fun <T> useBlocking(
        context: Context,
        settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        onLog: (String) -> Unit = {},
        block: () -> T,
    ): T = runBlocking {
        use(context, settleMillis, onLog) { block() }
    }

    /** Runs [block] with wireless debugging on, and schedules it off again afterwards. */
    suspend fun <T> use(
        context: Context,
        settleMillis: Long = DEFAULT_SETTLE_MILLIS,
        onLog: (String) -> Unit = {},
        block: suspend () -> T,
    ): T = sessionMutex.withLock {
        check(begin(context, onLog)) {
            "Wireless debugging could not be enabled: WRITE_SECURE_SETTINGS is required"
        }
        try {
            // adbd takes a moment to publish its port and start listening, and a lookup that raced it
            // would report a device with no transport.
            if (settleMillis > 0) delay(settleMillis)
            block()
        } finally {
            scheduleGraceDisable(context, onLog)
        }
    }

    /**
     * Turns wireless debugging off again now, whatever the grace period was doing.
     *
     * Only if this app turned it on. A device already running wireless debugging for the user's own
     * adb session has nothing to restore, and switching it off there would end a session the app was
     * never asked to touch. The answer is persisted rather than held in memory because the process that
     * enabled it may be gone by the time this runs: the failsafe alarm fires in a new process, and a
     * flag that died with the old one would leave the switch on with nobody left to turn it off.
     */
    fun forceDisable(context: Context, onLog: (String) -> Unit = {}) {
        // Pairing/service cleanup can arrive while another Veyra component owns a serialized ADB
        // window. Never tear adbd down underneath that live user; retry after the normal handoff grace.
        if (sessionMutex.isLocked) {
            onLog("[*] Wireless debugging is still in use by Veyra; cleanup deferred")
            scheduleGraceDisable(context, onLog)
            return
        }

        cancelGraceDisable()
        cancelCleanup(context)
        if (!AppPreferences.wirelessAdbOwnedByApp(context)) {
            onLog("[*] Wireless debugging was already on; leaving it as it was")
            return
        }
        val disabled = runCatching { AdbPairing.disableWirelessAdb(context) }.getOrDefault(false)
        if (disabled) {
            // Cleared only once the setting reads back as off: if it did not, this app still owns the
            // change and the next cleanup - or the failsafe alarm - has to try again.
            AppPreferences.setWirelessAdbOwnedByApp(context, false)
            onLog("[+] Wireless debugging disabled")
        } else {
            // A warning and not an error: the window this opened failed to close, which is worth
            // knowing on a device left with a shell port open.
            AppLog.warn(AppLogTags.WIRELESS_ADB, "Wireless debugging could not be turned back off")
            onLog("[!] Wireless debugging could not be turned off")
        }
    }

    private fun scheduleGraceDisable(context: Context, onLog: (String) -> Unit) {
        val appContext = context.applicationContext
        val task = Runnable {
            synchronized(cleanupLock) { pendingGraceDisable = null }
            forceDisable(appContext)
        }
        synchronized(cleanupLock) {
            pendingGraceDisable?.let(mainHandler::removeCallbacks)
            pendingGraceDisable = task
            mainHandler.postDelayed(task, HANDOFF_GRACE_MILLIS)
        }
        onLog("[*] Wireless debugging will be turned off once this transport is no longer in use")
    }

    private fun cancelGraceDisable() {
        synchronized(cleanupLock) {
            pendingGraceDisable?.let(mainHandler::removeCallbacks)
            pendingGraceDisable = null
        }
    }

    private fun armCleanup(context: Context) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + FAILSAFE_DISABLE_DELAY_MILLIS,
            cleanupPendingIntent(context),
        )
    }

    private fun cancelCleanup(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(cleanupPendingIntent(context))
    }

    private fun cleanupPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        CLEANUP_REQUEST_CODE,
        Intent(context, WirelessAdbCleanupReceiver::class.java).setAction(ACTION_FORCE_DISABLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_FORCE_DISABLE = "ctrl.mietze.veyraroot.action.FORCE_DISABLE_WIRELESS_ADB"

    private const val CLEANUP_REQUEST_CODE = 0x57414442
    private const val HANDOFF_GRACE_MILLIS = 5_000L

    /** Past the longest run this app starts, so the failsafe cannot fire under a live run. */
    private const val FAILSAFE_DISABLE_DELAY_MILLIS = 20 * 60 * 1_000L
    private const val DEFAULT_SETTLE_MILLIS = 1_000L
}

/** What turns wireless debugging off when the process that enabled it is no longer there to. */
class WirelessAdbCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TemporaryWirelessAdb.ACTION_FORCE_DISABLE) return
        TemporaryWirelessAdb.forceDisable(context)
    }
}
