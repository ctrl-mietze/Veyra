package ctrl.mietze.veyraroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * The two things the run's notification can do.
 *
 * Both are broadcasts rather than service calls, and both are deliberately tiny: this runs in the app's own
 * process with a time budget the framework decides, so nothing here hashes, walks a directory or downloads.
 * The stop is a record the run reads at its next tick; the copy is a file read of a log the run is already
 * writing.
 */
class RunActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                // Written before anything is said, so the run stops even if the line below cannot be
                // delivered - and named for the process the run is in, which is *not* this one whenever
                // the boot gate posted the notification: a broadcast lands in the app's own process while
                // that run is in `:autoroot_gate`, so naming this pid would leave the request waiting on a
                // run that is somewhere else, and the Stop button would do nothing at all.
                val holder = RunInFlight.holder(context)
                RunStopSignal.request(
                    context = context,
                    bootToken = holder?.bootToken,
                    pid = stopTarget(holder, Process.myPid()),
                )
                AppLog.warn(AppLogTags.RUN, "Stop requested from the run notification")
                RunNotification.note(
                    context = context,
                    message = context.getString(R.string.run_notification_stopping),
                    runId = activeRunId(context),
                )
            }
            ACTION_COPY_LOG -> {
                val log = activeRunLog(context)
                copyLogToClipboard(context, log)
                AppLog.info(
                    AppLogTags.RUN,
                    "Run log copied from the notification (${log.length} characters)",
                )
                RunNotification.note(
                    context = context,
                    message = context.getString(R.string.run_notification_log_copied),
                    runId = activeRunId(context),
                )
            }
        }
    }

    companion object {
        const val ACTION_STOP = "ctrl.mietze.veyraroot.action.STOP_RUN"
        const val ACTION_COPY_LOG = "ctrl.mietze.veyraroot.action.COPY_RUN_LOG"

        /**
         * What "the log" means here: the run in flight, read from the history entry it is writing.
         *
         * The history store is the only copy of a run's output that another part of the app can reach - the
         * view model that holds it in memory is not something a receiver can be handed - and it is written as
         * the run goes, so it is the current log rather than the last one. The app log is the fallback for a
         * notification that outlived its run's history entry, which is the case where the newest entry is
         * still the most useful thing there is.
         */
        /**
         * The run the notification this receiver acted on was about, from the record both processes share.
         *
         * The receiver is not in the run's process - a broadcast lands in the app's own process, and the
         * boot gate's run is in another one - so the record is the only thing here that can say which run
         * the message belongs to. Null when the record names none, and the notification then keeps the
         * destination it already had.
         */
        internal fun activeRunId(context: Context): String? = RunInFlight.holder(context)?.entryId

        /**
         * The process a stop should be written for, from the record that says where the run is.
         *
         * Pure, because the mistake it prevents is invisible: a stop naming the wrong process is written,
         * read by nobody, and looks exactly like a Stop button that was never pressed.
         */
        internal fun stopTarget(holder: RunHolder?, ownPid: Int): Int = holder?.pid ?: ownPid

        internal fun activeRunLog(context: Context): String {
            val entries = runCatching { InstallHistoryStore(context).load() }.getOrDefault(emptyList())
            val running = entries.firstOrNull { it.result == InstallRunResult.Running }
            val newest = running ?: entries.firstOrNull()
            val log = newest?.log.orEmpty()
            if (log.isNotBlank()) return log
            return AppLog.asText(AppLog.log.value).ifBlank { context.getString(R.string.logs_empty_title) }
        }
    }
}
