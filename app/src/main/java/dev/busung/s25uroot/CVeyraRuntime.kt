package ctrl.mietze.veyraroot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

internal enum class CVeyraBackend(val storedValue: String) {
    None("none"),
    WirelessAdb("wireless_adb"),
    Root("root");

    companion object {
        fun fromStoredValue(value: String?): CVeyraBackend =
            entries.firstOrNull { it.storedValue == value } ?: None
    }
}

internal object CVeyraApi {
    const val ACTION_START_REQUEST = "ctrl.mietze.veyraroot.action.CVEYRA_START"
    const val EXTRA_START_TOKEN = "cveyra_start_token"
    const val ACTION_START = "ctrl.mietze.veyraroot.action.CVEYRA_SERVICE_START"
    const val ACTION_BOOT = "ctrl.mietze.veyraroot.action.CVEYRA_SERVICE_BOOT"
    const val ACTION_STOP = "ctrl.mietze.veyraroot.action.CVEYRA_SERVICE_STOP"
}

internal object CVeyraPreferences {
    private const val PREFS = "cveyra"
    private const val ENABLED = "enabled"
    private const val START_AS_ROOT = "start_as_root"
    private const val PROMOTE_AFTER_BOOT = "promote_after_boot"
    private const val LEGACY_PROMOTE_AFTER_ROOT = "promote_after_root"
    private const val START_ON_BOOT = "start_on_boot"
    private const val TOKEN_ENABLED = "token_enabled"
    private const val TOKEN = "start_token"
    private const val RUNNING = "running"
    private const val ROOT_BROKER = "root_broker"
    private const val BACKEND = "backend"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(ENABLED, value).apply()

    fun startAsRoot(context: Context) = prefs(context).getBoolean(START_AS_ROOT, false)
    fun setStartAsRoot(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(START_AS_ROOT, value).apply()

    fun promoteAfterBoot(context: Context): Boolean {
        val preferences = prefs(context)
        return if (preferences.contains(PROMOTE_AFTER_BOOT)) {
            preferences.getBoolean(PROMOTE_AFTER_BOOT, true)
        } else {
            preferences.getBoolean(LEGACY_PROMOTE_AFTER_ROOT, true)
        }
    }

    fun setPromoteAfterBoot(context: Context, value: Boolean) {
        val editor = prefs(context).edit()
            .putBoolean(PROMOTE_AFTER_BOOT, value)
            .remove(LEGACY_PROMOTE_AFTER_ROOT)
        // A promotion request without the CVeyra client enabled can never run. Treat the
        // stronger option as also opting into the base client.
        if (value) editor.putBoolean(ENABLED, true)
        editor.apply()
    }

    fun startOnBoot(context: Context) = prefs(context).getBoolean(START_ON_BOOT, false)
    fun setStartOnBoot(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(START_ON_BOOT, value).apply()

    fun tokenEnabled(context: Context) = prefs(context).getBoolean(TOKEN_ENABLED, false)
    fun setTokenEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(TOKEN_ENABLED, value).apply()

    fun startToken(context: Context): String {
        val existing = prefs(context).getString(TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        return rotateStartToken(context)
    }

    fun rotateStartToken(context: Context): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString(TOKEN, token).apply()
        return token
    }

    fun running(context: Context) = prefs(context).getBoolean(RUNNING, false)
    fun rootBroker(context: Context) = prefs(context).getBoolean(ROOT_BROKER, false)
    fun backend(context: Context) =
        CVeyraBackend.fromStoredValue(prefs(context).getString(BACKEND, null))

    internal fun setRuntimeState(
        context: Context,
        running: Boolean,
        rootBroker: Boolean,
        backend: CVeyraBackend = if (rootBroker) CVeyraBackend.Root else CVeyraBackend.None,
    ) {
        prefs(context).edit()
            .putBoolean(RUNNING, running)
            .putBoolean(ROOT_BROKER, rootBroker)
            .putString(BACKEND, backend.storedValue)
            .apply()
    }
}

internal object CVeyraController {
    fun setEnabled(context: Context, enabled: Boolean) {
        if (CVeyraAccessStore.isActive(context)) {
            CVeyraAccessStore.enforce(context)
            start(context)
            return
        }
        CVeyraPreferences.setEnabled(context, enabled)
        if (enabled) start(context) else stop(context)
    }

    fun start(context: Context) {
        runCatching {
            context.startForegroundService(
                Intent(context, CVeyraService::class.java).setAction(CVeyraApi.ACTION_START),
            )
        }.onFailure { error ->
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "Foreground CVeyra start was refused; falling back to JobScheduler: " +
                    "${error.javaClass.simpleName}: ${error.message}",
            )
            CVeyraBootJobService.schedule(context, promoteRoot = false)
        }
    }

    fun startAfterBoot(context: Context) {
        CVeyraAccessStore.enforce(context)
        CVeyraBootJobService.schedule(
            context,
            promoteRoot = CVeyraAccessStore.isActive(context) ||
                CVeyraPreferences.promoteAfterBoot(context),
        )
    }

    fun stop(context: Context) {
        if (CVeyraAccessStore.isActive(context)) {
            CVeyraAccessStore.enforce(context)
            start(context)
            return
        }
        val appContext = context.applicationContext
        Thread({
            runCatching { CVeyraAgent.stop(appContext) }
        }, "CVeyraStop").start()
        context.stopService(Intent(context, CVeyraService::class.java))
        CVeyraPreferences.setRuntimeState(
            context,
            running = false,
            rootBroker = false,
            backend = CVeyraBackend.None,
        )
    }

    /**
     * Ask KernelSU directly. This deliberately bypasses any compatibility transport so the
     * "CVeyra as Root" switch really means a KernelSU/Magisk-style su grant for Veyra itself.
     */
    fun requestRootAccess(context: Context): Boolean {
        CVeyraPreferences.setEnabled(context, true)
        // Direct app su first; then the detached uid=2000 CVeyra agent. Some temporary KernelSU
        // sessions authorize shell before the manager has a profile for Veyra's Android UID.
        val root = isRootAnswer(SuShell.run("id", timeoutSeconds = 30)) ||
            agentRootShell(context, "id")?.let(::isRootAnswer) == true
        if (root) {
            CVeyraPreferences.setStartAsRoot(context, true)
            CVeyraPreferences.setRuntimeState(
                context,
                running = true,
                rootBroker = true,
                backend = CVeyraBackend.Root,
            )
        }
        return root
    }

    /** Root command through either Veyra's own su profile or CVeyra's detached shell agent. */
    fun rootShell(context: Context, command: String): ShizukuController.ShellResult? {
        SuShell.run(command)?.let { direct ->
            if (direct.exitCode == 0 || SuShell.isRoot()) return direct
        }
        return agentRootShell(context, command)
    }

    private fun agentRootShell(
        context: Context,
        command: String,
    ): ShizukuController.ShellResult? {
        if (!CVeyraAgent.isRunning(context) && !CVeyraAgent.ensure(context)) return null
        val probe = CVeyraAgent.shell(context, "su -c id") ?: return null
        if (!isRootAnswer(probe)) return null
        return CVeyraAgent.shell(context, "su -c ${shellQuote(command)}")
    }

    fun refreshRootBackend(context: Context): Boolean {
        val available = CVeyraPreferences.startAsRoot(context) &&
            rootShell(context, "id")?.let(::isRootAnswer) == true
        val previous = CVeyraPreferences.backend(context)
        CVeyraPreferences.setRuntimeState(
            context,
            running = CVeyraPreferences.running(context),
            rootBroker = available,
            backend = if (available) CVeyraBackend.Root
                else if (previous == CVeyraBackend.WirelessAdb) previous
                else CVeyraBackend.None,
        )
        return available
    }

    /**
     * Probe the exact backend CVeyra would use right now.
     *
     * Root is opt-in through "CVeyra as Root". Otherwise CVeyra behaves like the normal
     * wireless-debugging client: it uses its own ADB identity and connects to this phone's adbd.
     */
    fun probeBackend(context: Context): CVeyraBackend {
        if (CVeyraAccessStore.isActive(context)) {
            CVeyraAccessStore.enforce(context)
            val root = rootShell(context, "id")?.let(::isRootAnswer) == true
            CVeyraPreferences.setRuntimeState(
                context,
                running = root,
                rootBroker = root,
                backend = if (root) CVeyraBackend.Root else CVeyraBackend.None,
            )
            return if (root) CVeyraBackend.Root else CVeyraBackend.None
        }

        if (!CVeyraPreferences.enabled(context)) {
            CVeyraPreferences.setRuntimeState(
                context,
                running = false,
                rootBroker = false,
                backend = CVeyraBackend.None,
            )
            return CVeyraBackend.None
        }

        if (CVeyraPreferences.startAsRoot(context) &&
            rootShell(context, "id")?.let(::isRootAnswer) == true
        ) {
            CVeyraPreferences.setRuntimeState(
                context,
                running = true,
                rootBroker = true,
                backend = CVeyraBackend.Root,
            )
            return CVeyraBackend.Root
        }

        // Prefer CVeyra's detached shell broker. It is created through authenticated local ADB
        // once and then lives as uid=2000 outside the app process, so Android killing/recreating Veyra
        // does not take the shell broker down with it and wireless debugging does not have to stay on.
        if (CVeyraAgent.isRunning(context) || CVeyraAgent.ensure(context)) {
            CVeyraPreferences.setRuntimeState(
                context,
                running = true,
                rootBroker = false,
                backend = CVeyraBackend.WirelessAdb,
            )
            return CVeyraBackend.WirelessAdb
        }

        CVeyraPreferences.setRuntimeState(
            context,
            running = false,
            rootBroker = false,
            backend = CVeyraBackend.None,
        )
        return CVeyraBackend.None
    }

    /**
     * CVeyra's shell client. No Permission-Manager policy is involved here; V-SPR remains a
     * separate preview. This is only the base broker used by Veyra's own features.
     */
    fun shell(context: Context, command: String): ShizukuController.ShellResult? {
        if (CVeyraAccessStore.isActive(context)) {
            CVeyraAccessStore.enforce(context)
            return rootShell(context, command)?.also { result ->
                val root = isRootAnswer(
                    if (command == "id") result
                    else rootShell(context, "id") ?: result,
                )
                CVeyraPreferences.setRuntimeState(
                    context,
                    running = root,
                    rootBroker = root,
                    backend = if (root) CVeyraBackend.Root else CVeyraBackend.None,
                )
            }
        }

        if (!CVeyraPreferences.enabled(context)) return null

        if (!CVeyraPreferences.running(context)) {
            runCatching { start(context) }
        }

        if (CVeyraPreferences.startAsRoot(context)) {
            rootShell(context, command)?.let { result ->
                CVeyraPreferences.setRuntimeState(
                    context,
                    running = true,
                    rootBroker = true,
                    backend = CVeyraBackend.Root,
                )
                return result
            }
        }

        CVeyraAgent.shell(context, command)?.let { result ->
            CVeyraPreferences.setRuntimeState(
                context,
                running = true,
                rootBroker = false,
                backend = CVeyraBackend.WirelessAdb,
            )
            return result
        }

        if (CVeyraAgent.ensure(context)) {
            CVeyraAgent.shell(context, command)?.let { result ->
                CVeyraPreferences.setRuntimeState(
                    context,
                    running = true,
                    rootBroker = false,
                    backend = CVeyraBackend.WirelessAdb,
                )
                return result
            }
        }

        // Last-resort one-shot local ADB. It uses the same serialized wireless-ADB window as every
        // other Veyra component, so this fallback cannot tear adbd down underneath a concurrent run.
        if (!AdbCredentialStore.hasStoredKey(context)) return null
        return runCatching {
            TemporaryWirelessAdb.useBlocking(
                context = context,
                settleMillis = 500,
                onLog = { line -> AppLog.debug(AppLogTags.WIRELESS_ADB, line) },
            ) {
                WirelessAdbSession.open(context, portDiscoveryTimeoutMs = 10_000).use { session ->
                    val result = session.shell(command)
                    CVeyraPreferences.setRuntimeState(
                        context,
                        running = true,
                        rootBroker = false,
                        backend = CVeyraBackend.WirelessAdb,
                    )
                    ShizukuController.ShellResult(result.exitCode, result.output)
                }
            }
        }.onFailure { error ->
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "CVeyra one-shot local ADB failed: ${error.javaClass.simpleName}: ${error.message}",
            )
        }.getOrNull()
    }
}

class CVeyraService : Service() {
    private val bootstrapRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.readiness_cveyra),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CVeyraApi.ACTION_STOP) {
            val appContext = applicationContext
            Thread({ runCatching { CVeyraAgent.stop(appContext) } }, "CVeyraStop").start()
            CVeyraPreferences.setRuntimeState(
                this,
                running = false,
                rootBroker = false,
                backend = CVeyraBackend.None,
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!CVeyraPreferences.enabled(this)) {
            val appContext = applicationContext
            Thread({ runCatching { CVeyraAgent.stop(appContext) } }, "CVeyraStop").start()
            CVeyraPreferences.setRuntimeState(
                this,
                running = false,
                rootBroker = false,
                backend = CVeyraBackend.None,
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Starting from BOOT_COMPLETED is still a background start on modern Android. Become a short
        // foreground bootstrap immediately, then disappear once the detached shell-owned agent lives.
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.readiness_cveyra))
                .setContentText(getString(R.string.cveyra_runtime_notification))
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build(),
        )

        if (!bootstrapRunning.compareAndSet(false, true)) {
            AppLog.debug(AppLogTags.WIRELESS_ADB, "CVeyra bootstrap already in progress; coalescing start")
            return START_NOT_STICKY
        }

        // Manual/user-driven service starts are coalesced into one bootstrap. Boot starts use the
        // JobScheduler service below and therefore do not depend on a boot-time foreground-service
        // exemption on Android 15/16.
        Thread({
            try {
                val backend = CVeyraController.probeBackend(this)
                if (backend == CVeyraBackend.None) {
                    CVeyraPreferences.setRuntimeState(
                        this,
                        running = false,
                        rootBroker = false,
                        backend = CVeyraBackend.None,
                    )
                }
            } catch (error: Throwable) {
                AppLog.error(AppLogTags.WIRELESS_ADB, "CVeyra bootstrap failed", error)
                CVeyraPreferences.setRuntimeState(
                    this,
                    running = false,
                    rootBroker = false,
                    backend = CVeyraBackend.None,
                )
            } finally {
                bootstrapRunning.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "CVeyraBootstrap").start()

        return START_NOT_STICKY
    }

    // Do not clear runtime state here. A normal service stop is expected after bootstrapping the
    // detached uid=2000 agent; clearing it would make Home say CVeyra stopped while the broker is live.
    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "cveyra_runtime"
        private const val NOTIFICATION_ID = 0x43564559
    }
}

class CVeyraBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        CVeyraAccessStore.enforce(context)
        if (!CVeyraPreferences.enabled(context) || !CVeyraPreferences.startOnBoot(context)) return
        CVeyraController.startAfterBoot(context)
    }
}

class CVeyraStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CVeyraApi.ACTION_START_REQUEST) return
        if (!CVeyraPreferences.tokenEnabled(context)) return
        val supplied = intent.getStringExtra(CVeyraApi.EXTRA_START_TOKEN) ?: return
        if (supplied != CVeyraPreferences.startToken(context)) return
        CVeyraPreferences.setEnabled(context, true)
        // This receiver may run while the app is backgrounded, where direct FGS starts are not
        // generally allowed. Use the same durable bootstrap job as boot startup.
        CVeyraBootJobService.schedule(context, promoteRoot = false)
    }
}
