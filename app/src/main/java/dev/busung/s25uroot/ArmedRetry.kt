package ctrl.mietze.veyraroot

/**
 * The one-shot retry a failed run can leave behind, and what it means right now.
 *
 * The retry is stored as the kernel boot it was armed in rather than as a flag, because the whole
 * promise is "after a reboot" - a run armed while the phone is up must not be started by the same boot
 * that armed it. Two things follow from that, and both are why this type exists:
 *
 * - Until the reboot happens, the retry is waiting on something the user controls, and the only thing
 *   worth offering them is the way to take it back.
 * - After the reboot happens, it is waiting on *this app*: the boot gate runs it by itself only where
 *   there is a cached payload to run from, since at boot there is no network to fetch one. On a device
 *   whose last run never succeeded there is no cache, which is exactly the device that was told
 *   "restart and retry". So for that one, a run is owed and only a tap can give it.
 *
 * It also overrides root on boot, deliberately: a retry is a request the user made by hand, and the
 * gate's rule is that either way of asking gets the boot's single attempt. That override is the reason
 * this is on screen at all - it used to be invisible, so a phone that rooted itself at boot looked like
 * a setting that had been ignored.
 */
internal data class ArmedRetry(
    /** The kernel boot the retry was armed in. */
    val armedForBoot: String,
    /** The kernel boot the phone is running now, or null when it could not be read. */
    val currentBoot: String?,
) {

    /**
     * Whether the reboot this was armed for has happened, so a run is waiting to be started.
     *
     * An unreadable boot id counts as "after the reboot": the boot gate cannot run either without one,
     * so nothing will start on its own, and the honest offer is to start it by hand rather than to
     * claim it is waiting on a restart that may already have happened.
     */
    val afterReboot: Boolean get() = currentBoot == null || currentBoot != armedForBoot

    companion object {

        /**
         * Reads the retry this device has armed, or null when there is none.
         *
         * [bootToken] is passed in rather than read here so this stays a pure decision about two ids,
         * which is the part worth testing.
         */
        fun of(armedForBoot: String?, bootToken: String?): ArmedRetry? =
            armedForBoot?.takeIf(String::isNotBlank)?.let { ArmedRetry(it, bootToken) }
    }
}
