package ctrl.mietze.veyraroot

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one thing that happens on `BOOT_COMPLETED`: decide whether this boot gets an automatic
 * install, and start the gate that performs it.
 *
 * The receiver is kept deliberately small - no payload hashing, no file walking, no network - because
 * it runs inside the framework's boot broadcast with a time budget it does not control. The gate does
 * the real work in its own process, where being slow costs nothing but its own start.
 *
 * The Shizuku start is collected before the install checks return early for the same reason it always
 * has been: a boot that already has root is exactly the boot where Shizuku can be brought back
 * without a cable, so it must not be skipped along with the install.
 */
class AutoRootBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // The quick reading only: this runs inside the framework's boot broadcast, and the fallback
        // starts a process and waits on it. A wrong no here costs a wake-up, not an install - the
        // gate asks again, authoritatively, before it spends this boot's attempt.
        val rootActive = RootStatusProbe.isActiveQuick()

        // Collected before the root checks rather than inside them, because Shizuku no longer needs
        // root to be started: the app's own adb identity and a stored start token are each routes of
        // their own, and a boot with one of those and no root is exactly the boot where starting
        // Shizuku matters most. A boot with none of them is left alone instead of being told once per
        // reboot that nothing can be done.
        if (AppPreferences.shizukuBootMode(context)) {
            val paired = AdbCredentialStore.hasStoredKey(context) && AppPreferences.adbPaired(context)
            val token = AppPreferences.shizukuAutomationToken(context).isNotBlank()
            if (shizukuBootStartWorthAttempting(
                    rootAlreadyActive = rootActive,
                    localAdbPaired = paired,
                    tokenConfigured = token,
                )
            ) {
                AppLog.info(AppLogTags.BOOT, "Starting Shizuku at boot (root=$rootActive, paired=$paired)")
                ShizukuBootService.start(context)
            } else {
                // Said rather than skipped in silence: on a device with none of the three routes this
                // is the answer to "why is Shizuku never up after a reboot".
                AppLog.info(
                    AppLogTags.BOOT,
                    "Shizuku not started at boot: no root, no saved pairing and no start token",
                )
            }
        }

        if (rootActive) {
            AppLog.info(AppLogTags.BOOT, "Root is already active, so no install is started")
            return
        }
        // A one-shot retry armed from the run screen counts here too: those two are the only ways this
        // boot can have been asked for an install, and the gate is where either one is carried out.
        val bootRootMode = AppPreferences.bootRootMode(context)
        val retryArmed = AppPreferences.retryArmed(context)
        if (!bootRootMode && !retryArmed) {
            AppLog.debug(
                AppLogTags.BOOT,
                "No install this boot: root on boot is off and no retry is armed",
            )
            return
        }
        AppLog.info(
            AppLogTags.BOOT,
            "A boot install was asked for (root on boot=$bootRootMode, retry armed=$retryArmed)",
        )

        // The boot id is the only thing that tells a real reboot from a userspace restart that
        // re-emits BOOT_COMPLETED, and claiming it has to happen before anything is started.
        val bootToken = AutoRootSupport.currentBootToken()
        if (bootToken == null) {
            AppLog.warn(
                AppLogTags.BOOT,
                "A boot install was asked for but no boot id could be read; standing down",
            )
            return
        }
        if (!AutoRootSupport.claimBootCompletedForKernel(context, bootToken)) {
            AppLog.info(
                AppLogTags.BOOT,
                "Ignoring a repeated BOOT_COMPLETED within one kernel boot",
            )
            AutoRootService.stop(context)
            return
        }
        if (AutoRootSupport.hasAttemptedBoot(context, bootToken)) {
            AppLog.warn(AppLogTags.BOOT, "This boot has already had an attempt; standing down")
            AutoRootService.stop(context)
            return
        }
        if (!AutoRootSupport.shouldRunForBoot(context, bootToken)) {
            AppLog.warn(
                AppLogTags.BOOT,
                "The boot gate refused the install before the service was started",
            )
            AutoRootService.stop(context)
            return
        }
        AutoRootService.start(context)
    }

    private companion object {
        const val TAG = "RootMyGalaxyBoot"
    }
}

/** The actions a boot-install notification offers. */
class AutoRootActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SKIP_INSTALL -> {
                // One action for both kinds of boot install, because it means one thing: not this time.
                // It used to turn off root on boot - a setting, not the run the notification was about -
                // so skipping a run nobody asked to stop permanently also changed every boot after it.
                //
                // What it does take back is the request this boot is honouring, when there is one: the
                // notification is up before the gate has claimed the attempt, so a retry that survived a
                // "skip" came back at the next restart. That is [skipTakesBackRetry]'s question, and the
                // answer is no for a retry armed while this boot was already running - that one is for
                // the boot after this one.
                val bootToken = AutoRootSupport.currentBootToken()
                if (skipTakesBackRetry(AppPreferences.retryArmedInBoot(context), bootToken)) {
                    AppPreferences.setRetryAfterReboot(context, null)
                    AppLog.info(AppLogTags.BOOT, "The scheduled retry was taken back from the notification")
                }
                AppLog.info(AppLogTags.BOOT, "This boot's install was skipped from the notification")
                AutoRootService.stop(context)
            }

            ACTION_APPLY_MODULES -> applyModules(context)
        }
    }

    /**
     * Runs KernelSU's soft reboot, the userspace restart that walks the module lifecycle in its order.
     *
     * goAsync, because this is a broadcast: the action takes as long as a shell takes, and returning
     * from onReceive first would let the process be killed in the middle of the one action the user
     * just asked for. The notification is left standing when it is refused, so the offer is still there
     * to retry - and taken away when the restart has been accepted, because the userspace is going.
     */
    private fun applyModules(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = runRecoveryAction(context.applicationContext, RecoveryTool.SoftReboot)
                if (outcome.accepted) {
                    context.getSystemService(NotificationManager::class.java)
                        .cancel(AutoRootService.NOTIFICATION_ID)
                    AppLog.info(
                        AppLogTags.KERNEL_SU,
                        "KernelSU soft reboot accepted from the root on boot notification",
                    )
                } else {
                    AppLog.warn(
                        AppLogTags.KERNEL_SU,
                        "Applying modules from the notification was refused: ${outcome.detail}",
                    )
                }
            } catch (error: Throwable) {
                AppLog.error(
                    AppLogTags.KERNEL_SU,
                    "Applying modules from the notification failed",
                    error,
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /**
         * Take back the install this boot is running, and nothing else.
         *
         * One action for a root-on-boot install and for a retry, since the two differ only in what asked
         * for the boot's attempt - which the handler reads from the device rather than from which button
         * was pressed.
         */
        const val ACTION_SKIP_INSTALL =
            "ctrl.mietze.veyraroot.action.SKIP_INSTALL"
        const val ACTION_APPLY_MODULES =
            "ctrl.mietze.veyraroot.action.APPLY_MODULES"
        private const val TAG = "RootMyGalaxyBootAction"
    }
}
