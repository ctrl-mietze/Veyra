package ctrl.mietze.veyraroot

/**
 * A run happening in another process, drawn on the run screen.
 *
 * The run screen's state is the memory of the process running the run, which is why a boot install has never
 * been showable on it: the gate installs from `:autoroot_gate` and the screens are in the UI process. What the
 * gate *does* publish is its history entry, written as the run goes - and since a live run now writes the phase
 * it has reached beside the log, that entry is enough to draw the same screen: the same bar, the same steps,
 * the same log arriving a line at a time.
 *
 * So a tap on a run notification can open the run screen for a boot install, and not a log: the difference is
 * this type, and nothing about it is a guess. [followedRun] refuses unless the shared record says this entry is
 * the one being written right now, which is the same fact the history screen follows and the same one that
 * spares the entry from being closed as interrupted.
 *
 * What it cannot do is what needs the run's own process: skipping the boot settle, and answering a failure.
 * Those stay where the run is - a screen here offering them would be offering to reach into another process.
 */
internal data class FollowedRun(val entry: InstallHistoryEntry, val holder: RunHolder) {

    /**
     * The state the run screen draws.
     *
     * [InstallUiState.busy] is derived from the phase, which is why the phase matters twice: it sets the bar
     * and the step marks, and it is what offers the Stop. A record written before this build carries no phase,
     * and that falls back to the first step of a run rather than to nothing - the run is in flight (the record
     * says so), so "a run is under way" is the honest reading, and a screen with no Stop for a run in flight
     * would be the wrong one in the only way that matters.
     *
     * The status line is the run's own last line, because the message the run posts is not in the entry: the
     * log is written per line and the message is the thing being written most often.
     */
    fun state(): InstallUiState = InstallUiState(
        phase = entry.phase ?: InstallPhase.Checking,
        message = entry.log.lastLogLine(),
        log = entry.log,
    )
}

/**
 * The run this entry is, if something is still writing it, or null.
 *
 * Both halves are needed and neither is optional. The record names the entry its owner is writing, so an entry
 * it does not name is either finished or another run's; and a run that has ended is a record to read, not a
 * screen to watch - the verdict, the stage and the log are on the record, and this screen has none of them.
 */
internal fun followedRun(entry: InstallHistoryEntry?, holder: RunHolder?): FollowedRun? {
    if (entry == null || holder == null) return null
    if (holder.entryId != entry.id) return null
    if (entry.result != InstallRunResult.Running) return null
    return FollowedRun(entry, holder)
}

/**
 * How often the entry is re-read while this screen follows a run.
 *
 * The same second the history screen follows one at, and for the same reason: the entry is saved once per log
 * line, so a longer tick reads as a stalled run and a shorter one is a file read per frame for nothing.
 */
internal const val FOLLOW_TICK_MILLIS = 1_000L

/**
 * The last line the run wrote, or empty.
 *
 * The whole line, markers included: it is the payload's own output as often as this app's, and stripping the
 * leading `[*]` would edit the one thing on the screen that is quoted rather than written.
 */
private fun String.lastLogLine(): String =
    lineSequence().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
