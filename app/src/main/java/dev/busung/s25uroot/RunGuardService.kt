package ctrl.mietze.veyraroot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Keeps a manual root run in Android's foreground-service class.
 *
 * The exploit still owns its native children and its own timeouts; this service changes none of that.
 * Its job is only process lifetime: leaving Veyra, locking the screen or memory pressure should not
 * demote the UI process to an ordinary cached app while it is supervising a kernel run.
 */
internal class RunGuardService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || RunInFlight.holder(this) == null) {
            stopGuard(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification())
        val manager = getSystemService(PowerManager::class.java)
        wakeLock = manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VeyraRoot:manualRun",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_GUARD_MILLIS)
        }
        return START_NOT_STICKY
    }

    private fun stopGuard(startId: Int) {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.run_guard_notification))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, InstallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.run_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.run_notification_channel_description)
            },
        )
    }

    companion object {
        private const val ACTION_STOP = "ctrl.mietze.veyraroot.action.RUN_GUARD_STOP"
        private const val CHANNEL_ID = "run_guard"
        private const val NOTIFICATION_ID = 0x56524744
        private const val MAX_GUARD_MILLIS = 20 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, RunGuardService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RunGuardService::class.java))
        }
    }
}
