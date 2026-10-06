package ctrl.mietze.veyraroot

import android.content.Context
import android.os.Process

/**
 * A stop asked for from outside the run screen - today, from the run's own notification.
 *
 * A record rather than a call, for the same reason [RunInFlight] is one: the thing that asks is a broadcast
 * receiver and the thing that must stop is a coroutine somewhere in this app, and neither holds a reference
 * to the other. The run polls this while it works, so a stop arrives at the next tick rather than at the next
 * screen - which is the whole point of offering it from the notification, where nobody is watching.
 *
 * Keyed by the boot token and the pid, and cleared when a run begins. A stop that outlived its run would be a
 * trap: the next run in the same process would find it waiting and stop the moment it started, which reads as
 * an app that refuses to install.
 */
internal data class RunStopRequest(val bootToken: String, val pid: Int) {

    /**
     * Whether this request was meant for the run asking.
     *
     * Both halves matter. The pid alone is only unique within one boot, and the boot token alone would match a
     * run in another process - the boot gate installs from its own.
     */
    fun isFor(bootToken: String?, pid: Int): Boolean = this.bootToken == bootToken && this.pid == pid

    companion object {
        fun of(bootToken: String?, pid: String?): RunStopRequest? {
            val token = bootToken?.trim()?.takeIf(String::isNotBlank) ?: return null
            val parsed = pid?.trim()?.toIntOrNull() ?: return null
            if (parsed <= 0) return null
            return RunStopRequest(token, parsed)
        }
    }
}

internal object RunStopSignal {

    private const val STATE = "run_stop_signal"
    private const val TOKEN = "boot_token"
    private const val PID = "pid"

    /**
     * Writes the request, naming the process that should act on it.
     *
     * Read from the caller's own process on purpose: the receiver runs in the process whose run posted the
     * notification, which is also the process that will poll for this. A boot token that cannot be read writes
     * nothing, because a request nobody could ever match would only sit there.
     */
    fun request(
        context: Context,
        bootToken: String? = kernelBootToken(),
        pid: Int = Process.myPid(),
    ) {
        val token = bootToken?.trim()?.takeIf(String::isNotBlank) ?: return
        prefs(context).edit()
            .putString(TOKEN, token)
            .putString(PID, pid.toString())
            .commit()
    }

    /** The request, whoever it was written for, or null when there is none. */
    fun pending(context: Context): RunStopRequest? = RunStopRequest.of(
        bootToken = prefs(context).getString(TOKEN, null),
        pid = prefs(context).getString(PID, null),
    )

    /**
     * Whether this run was asked to stop, consuming the request.
     *
     * Consumed by the run it names and only by that one. A request naming another process is left where it is -
     * that process has not read it yet - while one naming another boot is dropped, since nothing can match a
     * boot that has ended and leaving it in place would make the next run in this process stop on its first
     * tick.
     *
     * A boot token that could not be read falls back to the pid alone. That is the narrow case where the run
     * cannot prove which boot it is in - the same file the writer read a moment earlier - and a stop that was
     * asked for is worth more than the guarantee it breaks.
     */
    fun consumeFor(context: Context, bootToken: String?, pid: Int = Process.myPid()): Boolean {
        val request = pending(context) ?: return false
        val mine = if (bootToken == null) request.pid == pid else request.isFor(bootToken, pid)
        if (mine) {
            clear(context)
            return true
        }
        if (bootToken != null && request.bootToken != bootToken) clear(context)
        return false
    }

    /** Drops any request, which is what a run does before it starts. */
    fun clear(context: Context) {
        prefs(context).edit().remove(TOKEN).remove(PID).commit()
    }

    /**
     * The app's own preferences file, opened per call: the writer here is a broadcast receiver and the reader
     * is a run, and a cached instance in either would answer with the value it read at its own start.
     */
    private fun prefs(context: Context) =
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
}
