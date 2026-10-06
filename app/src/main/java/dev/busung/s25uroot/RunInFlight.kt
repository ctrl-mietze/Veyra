package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File

/**
 * Which process is on a run right now, in a form this app's *other* processes can read.
 *
 * A run can be started from two places and they are not in the same process: the boot gate installs
 * from its own `:autoroot_gate` service, and the screens install from the UI process. Every guard the
 * app has against a second attempt - the screen's own `installJob`, the gate's one-attempt-per-boot
 * claim - is a guard about its own process, so neither of them can tell the other that it is busy. For
 * a run that means an overlapping attempt, which the app has always been able to have; for a cleanup
 * it means something worse, because a sweep that cannot see a run in flight deletes the payload that
 * run is about to execute.
 *
 * So this is a record rather than a lock: a run writes down which process is running it, and anything
 * that wants to know asks. Deliberately not a lock, because a process that dies mid-run would leave a
 * lock held and nothing would ever be swept again - the record carries the process that wrote it, and a
 * pid that no longer exists is a run that no longer exists.
 *
 * The boot token travels with the pid for the same reason it does everywhere else in this app: a pid is
 * only unique within one boot, and a record that outlived its boot would name a stranger.
 */
internal data class RunHolder(
    val bootToken: String,
    val pid: Int,
    /**
     * The history entry this run is writing, when it named one.
     *
     * It is what turns the record from "somebody is running" into "*this* run is running": the entry is
     * written to disk as the run goes, so a reader that knows the id can tell the live record from one
     * left behind by a run that was killed, and can follow that run without being in its process.
     */
    val entryId: String? = null,
    /**
     * When that process started, so the pid is not asked to name it on its own.
     *
     * A pid is handed out again once its process is gone, and this record outlives its process by design -
     * it is read by other processes, and a run killed with its process never clears it. "Pid 8123 is alive"
     * is therefore not "the run that wrote this is alive": on a long boot the number comes round again and
     * the record describes a stranger. The start time is what a pid cannot be.
     */
    val startTicks: String? = null,
) {

    /**
     * Whether this record still describes a run in flight.
     *
     * Every input is passed in rather than read here so the rule is a rule about facts and can be tested
     * without a device or a `/proc`.
     *
     * Only a *positive* mismatch refuses. A start time that could not be read on either side is one this
     * rule cannot answer for, and treating that as a mismatch would strand a device with a run on it -
     * which is the same reason the module check elsewhere in this app refuses only on a positive finding.
     */
    fun holds(bootToken: String?, alive: Boolean, startTicks: String? = null): Boolean =
        this.bootToken == bootToken &&
            alive &&
            (this.startTicks == null || startTicks == null || this.startTicks == startTicks)

    companion object {
        /** Parses a stored record, refusing anything that is not one. */
        fun of(
            bootToken: String?,
            pid: String?,
            entryId: String? = null,
            startTicks: String? = null,
        ): RunHolder? {
            val token = bootToken?.trim()?.takeIf(String::isNotBlank) ?: return null
            val parsed = pid?.trim()?.toIntOrNull() ?: return null
            if (parsed <= 0) return null
            return RunHolder(
                bootToken = token,
                pid = parsed,
                entryId = entryId?.trim()?.takeIf(String::isNotBlank),
                startTicks = startTicks?.trim()?.takeIf(String::isNotBlank),
            )
        }
    }
}

internal object RunInFlight {

    private const val STATE = "run_in_flight"
    private const val TOKEN = "boot_token"
    private const val PID = "pid"
    private const val ENTRY = "entry_id"
    private const val START_TICKS = "start_ticks"

    /**
     * Records that this process is about to run, and returns whether it recorded anything.
     *
     * A null [bootToken] means the boot could not be read, and nothing is recorded: a holder nobody
     * could ever match is a record that only serves to go stale. [entryId] is the run's own history
     * entry, named so that anything reading this record can tell which run it is about - and so that the
     * entry a run is writing is not closed as an interrupted one while it is still being written.
     */
    fun begin(context: Context, bootToken: String?, entryId: String? = null): Boolean {
        val token = bootToken?.trim()?.takeIf(String::isNotBlank) ?: return false
        return preferences(context).edit()
            .putString(TOKEN, token)
            .putString(PID, android.os.Process.myPid().toString())
            // Null removes the key, which is what a caller with no entry to name wants: a stale id left
            // from an earlier run would point a reader at a record this run is not writing.
            .putString(ENTRY, entryId?.trim()?.takeIf(String::isNotBlank))
            .putString(START_TICKS, startTicksOf(android.os.Process.myPid()))
            .commit()
    }

    /**
     * Clears the record, but only where it names this process.
     *
     * Compared before clearing because two processes can both be finishing: a blind clear would erase
     * the other one's record and leave its run invisible to the sweep that follows.
     */
    fun end(context: Context) {
        val preferences = preferences(context)
        val holder = holder(context, preferences) ?: return
        if (holder.pid != android.os.Process.myPid()) return
        preferences.edit().remove(TOKEN).remove(PID).remove(ENTRY).remove(START_TICKS).commit()
    }

    /** The run this device has in flight, or null when there is none. */
    fun holder(context: Context): RunHolder? = holder(context, preferences(context))

    private fun holder(context: Context, preferences: android.content.SharedPreferences): RunHolder? {
        val holder = RunHolder.of(
            bootToken = preferences.getString(TOKEN, null),
            pid = preferences.getString(PID, null),
            entryId = preferences.getString(ENTRY, null),
            startTicks = preferences.getString(START_TICKS, null),
        ) ?: return null
        return holder.takeIf {
            it.holds(
                bootToken = currentBootToken(),
                alive = processAlive(it.pid),
                startTicks = startTicksOf(it.pid),
            )
        }
    }

    /**
     * When [pid] started, from the kernel's own table.
     *
     * The 22nd field of `/proc/<pid>/stat`, counted after the process name - which is the second field,
     * wrapped in parentheses, and may itself contain anything at all, spaces included. Everything after
     * the last `)` is therefore the fields from the third on.
     *
     * Null when it cannot be read, which the rule above treats as "cannot tell" rather than as a mismatch.
     */
    private fun startTicksOf(pid: Int): String? = runCatching {
        java.io.File("/proc/$pid/stat").readText()
            .substringAfterLast(") ")
            .split(" ")
            .getOrNull(19)
    }.getOrNull()?.trim()?.takeIf(String::isNotBlank)

    /**
     * Whether a process is still there.
     *
     * `/proc/<pid>` rather than `kill(pid, 0)`: this asks only about a process, needs no signal to be
     * sent - even a null signal is a permission this app does not need to spend - and works for the
     * app's own processes, which is the whole of what it is for.
     */
    private fun processAlive(pid: Int): Boolean = File("/proc/$pid").exists()

    private fun currentBootToken(): String? = kernelBootToken()

    /**
     * The app's own preferences file, opened per call.
     *
     * Not cached: two processes writing this record must see each other's writes, and a cached
     * `SharedPreferences` in one process would answer with the value it read at its own start.
     */
    private fun preferences(context: Context) =
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
}
