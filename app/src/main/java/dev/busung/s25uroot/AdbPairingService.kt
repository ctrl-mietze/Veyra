package ctrl.mietze.veyraroot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import java.net.ConnectException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pairs with the device's own wireless debugging, from a notification.
 *
 * The code has to be entered from the *other* side of the pairing: the user opens **Pair device with
 * pairing code** in Developer options, and the number Android shows there is what this app needs. So
 * the code field belongs where the user already is - the shade - rather than in the app, which would
 * mean switching to Developer options, memorising six digits and coming back. A notification with a
 * reply field is the smallest interface that fits.
 *
 * Wireless debugging is turned off again the moment the pairing transaction ends, whether it worked
 * or not: the app needed it for those seconds and has no further use for it, and leaving it on would
 * leave a shell port open that nobody asked for.
 */
class AdbPairingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var discovery: AdbMdns? = null
    private var searching = false
    private var lastPromptPort = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat(searchingNotification())
                startSearch()
            }
            ACTION_REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(REMOTE_INPUT_KEY)
                    ?.toString()
                    .orEmpty()
                val port = intent.getIntExtra(EXTRA_PORT, -1)
                if (port != -1 && code.isNotBlank()) {
                    startForegroundCompat(workingNotification())
                    pair(code, port)
                } else {
                    startSearch()
                }
            }
            ACTION_STOP -> {
                stopSearch()
                getSystemService(NotificationManager::class.java).cancel(PROMPT_NOTIFICATION_ID)
                TemporaryWirelessAdb.forceDisable(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (searching) return
        searching = true
        // The pairing service only exists while Android's own pairing dialog is open, so the port is
        // discovered rather than remembered.
        discovery = AdbMdns(this, AdbMdns.TLS_PAIRING) { port ->
            if (port <= 0) return@AdbMdns
            val notification = codeEntryNotification(port)
            // Update the foreground notification and also post a fresh prompt notification. Android's
            // pairing service appears only while the six-digit-code dialog is open, so this is exactly
            // the moment the code-entry notification should surface again.
            startForegroundCompat(notification)
            if (lastPromptPort != port) {
                lastPromptPort = port
                val manager = getSystemService(NotificationManager::class.java)
                manager.cancel(PROMPT_NOTIFICATION_ID)
                manager.notify(PROMPT_NOTIFICATION_ID, notification)
            }
        }.apply { start() }
    }

    private fun stopSearch() {
        if (!searching) return
        searching = false
        discovery?.stop()
    }

    private fun pair(code: String, port: Int) {
        scope.launch {
            val outcome = runCatching {
                val keyManager = AdbKeyManager(this@AdbPairingService)
                AdbPairingClient("127.0.0.1", port, code, keyManager).use { it.start() }
            }
            finishPairing(outcome.getOrDefault(false), outcome.exceptionOrNull())
        }
    }

    private suspend fun finishPairing(success: Boolean, error: Throwable?) {
        stopSearch()
        getSystemService(NotificationManager::class.java).cancel(PROMPT_NOTIFICATION_ID)
        // Turned off before anything else is reported: whether the pairing worked or not, the window
        // it needed is over.
        TemporaryWirelessAdb.forceDisable(this)

        val title: String
        var text: String
        if (success) {
            AppPreferences.setAdbPaired(this, true)
            if (CVeyraPreferences.enabled(this)) {
                CVeyraController.start(this)
            }
            title = getString(R.string.adb_pair_success_title)
            text = getString(R.string.adb_pair_success_text)
        } else {
            title = getString(R.string.adb_pair_failed_title)
            text = when (error) {
                is ConnectException -> getString(R.string.adb_pair_cannot_connect)
                is AdbInvalidPairingCodeException -> getString(R.string.adb_pair_wrong_code)
                else -> error?.message ?: getString(R.string.adb_pair_unknown_error)
            }
            AppLog.warn(AppLogTags.WIRELESS_ADB, "Pairing failed: ${error?.message}")
        }

        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                // Both outcomes need the same door: a failure is usually "the dialog was not open", and
                // the fix for that is the screen it is in.
                .setContentIntent(developerOptionsIntent())
                .addAction(
                    0,
                    getString(R.string.adb_pair_open_developer_options),
                    developerOptionsIntent(),
                )
                .setOngoing(false)
                .setAutoCancel(true)
                .build(),
        )
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun searchingNotification(): Notification = builder()
        .setContentTitle(getString(R.string.adb_pair_searching))
        .setOngoing(true)
        .setContentIntent(developerOptionsIntent())
        .addAction(
            0,
            getString(R.string.adb_pair_open_developer_options),
            developerOptionsIntent(),
        )
        .addAction(
            0,
            getString(R.string.action_cancel),
            PendingIntent.getForegroundService(
                this,
                2,
                stopIntent(this),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    /**
     * Developer options, from a notification.
     *
     * A broadcast rather than an activity pending intent, so the tap gets [DeveloperOptions]' whole
     * fallback chain: a pending intent is built around one immutable Intent, and the entry it would
     * carry is exactly the action a build without that screen refuses.
     */
    private fun developerOptionsIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        3,
        Intent(this, OpenDeveloperOptionsReceiver::class.java)
            .setAction(OpenDeveloperOptionsReceiver.ACTION_OPEN_DEVELOPER_OPTIONS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun codeEntryNotification(port: Int): Notification {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel(getString(R.string.adb_pair_code_hint))
            .build()
        val reply = PendingIntent.getForegroundService(
            this,
            1,
            replyIntent(this, port),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return builder()
            .setContentTitle(getString(R.string.adb_pair_service_found))
            .setContentText(getString(R.string.adb_pair_enter_code))
            .setOngoing(true)
            // Where the code comes from, one tap away: the notification is read either side of the
            // dialog that generates it.
            .setContentIntent(developerOptionsIntent())
            .addAction(
                0,
                getString(R.string.adb_pair_open_developer_options),
                developerOptionsIntent(),
            )
            .addAction(
                NotificationCompat.Action.Builder(0, getString(R.string.adb_pair_button), reply)
                    .addRemoteInput(remoteInput)
                    .build(),
            )
            .build()
    }

    private fun workingNotification(): Notification = builder()
        .setContentTitle(getString(R.string.adb_pair_working))
        .setOngoing(true)
        .build()

    private fun builder() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.adb_pair_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // The code field is the point, so the notification must be visible without sound: the
                // user is looking at Developer options, not at a tone.
                setSound(null, null)
                setShowBadge(false)
            },
        )
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            // A pairing that cannot show its notification can still be posted as an ordinary one;
            // failing here would mean the code field never appears at all.
            AppLog.warn(
                AppLogTags.WIRELESS_ADB,
                "Could not start in the foreground: ${error.message}",
            )
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        stopSearch()
        getSystemService(NotificationManager::class.java).cancel(PROMPT_NOTIFICATION_ID)
        TemporaryWirelessAdb.forceDisable(this)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 0x504149
        private const val PROMPT_NOTIFICATION_ID = 0x50414A
        private const val REMOTE_INPUT_KEY = "pairing_code"
        private const val EXTRA_PORT = "pairing_port"
        private const val ACTION_START = "start"
        private const val ACTION_REPLY = "reply"
        private const val ACTION_STOP = "stop"

        fun startIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(ACTION_START)

        private fun stopIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(ACTION_STOP)

        private fun replyIntent(context: Context, port: Int): Intent =
            Intent(context, AdbPairingService::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_PORT, port)
    }
}
