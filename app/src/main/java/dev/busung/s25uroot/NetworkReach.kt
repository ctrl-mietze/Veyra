package ctrl.mietze.veyraroot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay

/**
 * Whether the device is on Wi-Fi, which is the one thing wireless debugging waits for.
 *
 * Both of the routes that start Shizuku without root go through wireless debugging: this app's own adb
 * identity turns it on to be handed a shell to run Shizuku's starter in, and Shizuku's own start request
 * is answered by a build whose start method is wireless debugging. And wireless debugging cannot be up
 * without a connected Wi-Fi network - not by this app's rule, but by the framework's:
 * `AdbDebuggingManager` answers the enable message by writing `adb_wifi_enabled` back to 0 when there is
 * no current Wi-Fi access point, turns it off again on a Wi-Fi disconnect or a network change, and only
 * otherwise starts adbd's TLS thread. The port therefore cannot appear, and no amount of waiting
 * produces one.
 *
 * That is what turns "Shizuku has not arrived" into either "it has not had a chance yet" or a refusal
 * that names the reason. It is read and never changed: turning the radio on is not something a
 * third-party app can do - `setWifiEnabled` returns false for any caller that is not the system or a
 * privileged one - and the one command that can (`svc wifi enable`) needs the shell uid this app is
 * trying to obtain in the first place.
 */
/** How a wait for a network ended. */
internal enum class NetworkWait {
    /** There is a Wi-Fi network, so the routes that need one can be tried. */
    Connected,

    /** The window ran out with none. */
    TimedOut,

    /** The caller decided it no longer wanted to wait, so nothing should be held for this. */
    Abandoned,
}

internal object NetworkReach {

    /**
     * Whether a Wi-Fi network is connected, or null when the device would not say.
     *
     * Wi-Fi specifically, because that is what the framework's own gate checks: a device on mobile data
     * with Wi-Fi off has no current Wi-Fi access point, and `adb_wifi_enabled` is written back to 0 all
     * the same. Internet is not required and is deliberately not asked about - a phone's own hotspot,
     * with nowhere to go, satisfies the check, and that is the arrangement a phone with no Wi-Fi in reach
     * can actually make.
     */
    fun readConnected(context: Context): Boolean? = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching null
        val network = manager.activeNetwork ?: return@runCatching false
        manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            ?: false
    }.getOrNull()

    /**
     * The same reading, with an unreadable one taken as connected.
     *
     * For the callers that would hold something back on the strength of it, and only those: a device
     * this app cannot ask must keep the behaviour it had before this reading existed, and that behaviour
     * was to spend the wait. Failing closed here would refuse a boot on the strength of a reading that
     * failed, which is the mistake the mount probe and the process-map check both make a point of not
     * making.
     */
    fun connected(context: Context): Boolean = readConnected(context) != false

    /**
     * Waits for a Wi-Fi network to be connected, reporting as it goes.
     *
     * [stillWanted] is asked on every pass, and it is not decoration: this is the longest wait in the
     * app - five minutes - and it exists to make one caller's setting work, so a setting that is turned
     * off in the middle of it has to end it. The caller cannot do that from outside, because the wait
     * does not return until it is over.
     *
     * Polled rather than observed through a `NetworkCallback`, because both callers are already loops
     * that re-read the device once a second and a callback would be a second, differently-timed path
     * into the same decision. The interval is the tick both callers already use.
     */
    suspend fun awaitConnected(
        context: Context,
        timeoutMillis: Long,
        stillWanted: () -> Boolean = { true },
        onWaiting: (remainingMillis: Long) -> Unit = {},
    ): NetworkWait = awaitConnected(
        timeoutMillis = timeoutMillis,
        stillWanted = stillWanted,
        isConnected = { connected(context) },
        onWaiting = onWaiting,
    )

    /**
     * The wait itself, over a reading the caller supplies.
     *
     * Split out for the reason the rest of this file is pure: the loop's decisions - a network that is
     * there, a window that ran out, a caller that changed its mind - are worth pinning down, and the
     * `Context` overload above cannot be driven from a local JVM test. [tickMillis] is the seam that
     * does the same for the clock: it is the real second in production and a millisecond in a test,
     * where the passages of the loop are what is being pinned rather than the time between them.
     *
     * The two endings are checked in a fixed order, and the order is the answer: a network that is
     * already connected is reported as one even if the caller has since decided it no longer wants to
     * wait for it, because the network is a fact about the device while the wait is a decision about
     * this caller - and there is nothing left for the decision to do.
     */
    internal suspend fun awaitConnected(
        timeoutMillis: Long,
        isConnected: () -> Boolean,
        stillWanted: () -> Boolean = { true },
        tickMillis: Long = POLL_INTERVAL_MILLIS,
        onWaiting: (remainingMillis: Long) -> Unit = {},
    ): NetworkWait {
        var waited = 0L
        while (true) {
            if (isConnected()) return NetworkWait.Connected
            if (!stillWanted()) return NetworkWait.Abandoned
            val remaining = timeoutMillis - waited
            if (remaining <= 0L) return NetworkWait.TimedOut
            onWaiting(remaining)
            delay(tickMillis)
            waited += tickMillis
        }
    }

    private const val POLL_INTERVAL_MILLIS = 1_000L
}
