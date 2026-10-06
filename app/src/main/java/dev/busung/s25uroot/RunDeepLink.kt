package ctrl.mietze.veyraroot

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Where a tap on a notification about a run should land.
 *
 * A run has two lives and they are not in the same place. The live one is the run screen, whose state is in
 * the memory of the process running it - so it is the best screen when the tap arrives while that process
 * still has the run: the log panel, the Stop button, the step marks, the progress bar. It is no screen at
 * all once that process is gone, and it was never the boot gate's run, because the gate installs from
 * `:autoroot_gate` and the screens are in the UI process. Opening the run screen for a run it does not have
 * is worse than opening nothing: a fresh [InstallActivity] knows about no run, so it offers to start one -
 * a second install, offered by a notification that was describing the first.
 *
 * The other life is the run's own record: the history entry, written to disk as the run goes. It is live
 * while the run is running, complete once it has ended, and readable from any process, which is exactly the
 * set of things the run screen cannot promise.
 *
 * So a notification names the run it is about rather than the screen it wants, and the destination decides:
 * [liveRunIntent] is the run screen, which shows the named run when it is the one this process has and hands
 * off to the record when it is not, and [runRecordIntent] is the record itself, for a tap that arrives where
 * nothing can show the run - the boot gate's notification, whose run lives in another process entirely.
 */
internal const val EXTRA_RUN_ID = "open_run_id"

/** The run screen, told which run the notification was about. */
internal fun liveRunIntent(context: Context, runId: String?): Intent =
    Intent(context, InstallActivity::class.java).apply {
        // SINGLE_TOP so a run in this process is the instance that is already on the stack: reusing it is
        // what keeps its view model - and therefore the live run - rather than building a second screen
        // about a run it cannot see.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (runId != null) putExtra(EXTRA_RUN_ID, runId)
    }

/** The run's own record, for the tap nothing here can answer: a run another process is holding. */
internal fun runRecordIntent(context: Context, runId: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runId != null) putExtra(EXTRA_RUN_ID, runId)
    }

/**
 * The run screen, as a notification's content intent.
 *
 * [requestCode] is 0 for the content intent, and the actions below it are their own: one pending intent per
 * thing a tap can do, which is also why every one of them is `UPDATE_CURRENT` - the extras are the point of
 * it, and a reused intent with stale ones would open the wrong run.
 */
internal fun liveRunPendingIntent(context: Context, runId: String?): PendingIntent {
    val intent = liveRunIntent(context, runId)
    return PendingIntent.getActivity(context, intent.requestCode(), intent, pendingIntentFlags())
}

/** The run's record, as a notification's content intent. */
internal fun runRecordPendingIntent(context: Context, runId: String?): PendingIntent {
    val intent = runRecordIntent(context, runId)
    return PendingIntent.getActivity(context, intent.requestCode(), intent, pendingIntentFlags())
}

private fun pendingIntentFlags(): Int =
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

/**
 * The request code an intent is filed under, from what it carries.
 *
 * Two pending intents are only distinct when their request codes differ *and* [Intent.filterEquals] says
 * their contents do, and extras are explicitly not part of that comparison. So the run id has to be in the
 * code: with one shared code, the notification for a run would reuse the pending intent filed for the run
 * before it, and `UPDATE_CURRENT` would leave the extras of the first in place - a tap that opens the wrong
 * run is exactly the bug this file exists to fix.
 */
private fun Intent.requestCode(): Int = getStringExtra(EXTRA_RUN_ID)?.hashCode() ?: 0
