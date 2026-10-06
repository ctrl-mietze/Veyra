package ctrl.mietze.veyraroot

import android.content.Context

/**
 * Deleting what the app staged in `/data/local/tmp`.
 *
 * The app leaves files there because it has to - a payload has to be executable by a shell, and that
 * directory is the only place outside the app's sandbox a shell can reach - but nothing requires them
 * to *stay*. A staged daemon is a five-megabyte copy of a file the load already installed, a staged
 * payload is a copy of one the app still holds, and the logs and marker files are read once and never
 * again. What they are worth after the run is one thing only: evidence, to anything that goes looking.
 * This is the code that takes it away.
 *
 * ## Why a shell is needed, and why that decides when this runs
 *
 * The app cannot delete these itself. `/data/local/tmp` is mode `0771` owned by `shell`, so the app's
 * uid may traverse it and may not write in it - which is the same permission that makes the check in
 * [StagedResidue] a name-by-name stat. Deletion therefore goes through a shell this app already has:
 * KernelSU's `su`, or the `shell`-uid server Shizuku provides, whose uid *owns* the directory.
 *
 * That is also the reason a sweep is never something the app waits for. A device with no shell has no
 * way to clean up, and asking for one - starting Shizuku, turning on wireless debugging - to delete a
 * few files would cost far more than the files are worth. So [sweepWhenQuiet] uses a shell that is
 * already answering and reports [SweepOutcome.NoShell] otherwise, and the files simply stay until
 * there is one.
 *
 * ## What it keeps: nothing, and that was checked rather than assumed
 *
 * The first version of this sweep spared the daemon and its stage file. The reason to take them too is
 * not that nothing wants the daemon - the payload's root helper does: it bind-mounts
 * `/data/local/tmp/ksud-s25u-kdp` over `logcat` and runs the late-load out of it. The reason is *when*
 * it wants it. That request is made by this app, as a step of the run, and the helper is otherwise a
 * plain socket server with no retry of its own - so by the time a run is over, the late-load has
 * happened and the file has no reader left.
 *
 * Nor does anything later in the boot need a copy: the module reload - the one repair action that
 * stages a daemon - copies the *installed* one out of `/data/adb/ksud` into
 * `/data/local/tmp/.ksud-stage` itself and refuses unless the two hash the same, and both restarts and
 * the soft reboot reach the daemon at that installed path. The transport all four run through is
 * KernelSU's own `su`, never a staged binary.
 *
 * Keeping the daemon would also have been the exemption that matters least where it counts: on a real
 * device `/data/local/tmp/ksud-s25u-kdp` is the largest thing this app leaves behind, and the first
 * name a detector prints.
 *
 * ## What it can and cannot report
 *
 * The sweep is one shell command that says what is there, deletes it, and says what is there
 * afterwards. What it cannot see it cannot report: the temp-su socket is denied to the `shell` domain
 * altogether, so `rm` fails on it and that failure is the only evidence there is. Both are carried, and
 * the app's own [StagedResidue] reading is left to say what is actually there now.
 */
internal sealed interface SweepOutcome {

    /**
     * Nothing was deleted: there is no shell on this device.
     *
     * Not a failure, and deliberately not reported as one. It is the state of every device before the
     * first run, and the correct answer is to keep the files rather than to start asking for a shell.
     */
    data object NoShell : SweepOutcome

    /**
     * Nothing was deleted because another process is on a run.
     *
     * Its own outcome rather than [NoShell]: the two read the same in a log and mean opposite things,
     * and a sweep that stood down has to be tellable from one that found no way to start.
     */
    data object SkippedRun : SweepOutcome

    /**
     * The sweep ran.
     *
     * [found] is what was there before it deleted anything, which is the only way to tell a cleanup
     * from a no-op: `rm -f` is silent about a file that was already gone.
     */
    data class Done(
        val found: List<String>,
        val left: List<String>,
        /** What `rm` said about the paths it would not remove, empty when it said nothing. */
        val complaint: String,
        /**
         * Names this sweep deliberately never asked about, because the other install writes them too.
         *
         * Empty whenever that install is not present, which is the usual case: the sweep then asks
         * about every catalogued path, this list included.
         */
        val leftToTheOtherInstall: List<String> = emptyList(),
    ) : SweepOutcome {

        /**
         * Which of the sweep's four answers this is.
         *
         * Decided here rather than in the line below, so the choice can be tested without a device: the
         * order these are checked in is the part that matters, and it is the part a `Context` would
         * otherwise hide.
         */
        val verdict: SweepVerdict
            get() = when {
                // A refusal is reported before a leftover, and it is the stronger of the two: `rm`
                // saying it was not allowed to remove something is a fact about the device, where a name
                // missing from its own check is only what the shell could see.
                complaint.isNotEmpty() -> SweepVerdict.Refused
                left.isNotEmpty() -> SweepVerdict.LeftBehind
                found.isEmpty() -> SweepVerdict.NothingToDo
                else -> SweepVerdict.Removed
            }

        /** How many of what was there are gone, which is the difference and not what was attempted. */
        val removed: Int get() = found.size - left.size
    }

    /**
     * The line for the app log, or null when the sweep has nothing to report.
     *
     * Null rather than a sentence about nothing happening, because a line on every launch saying the
     * directory was already empty is exactly the noise that makes the interesting lines hard to find -
     * and because "nothing was staged" is the state the sweep exists to produce, not news.
     */
    fun logLine(context: Context): String? = when (this) {
        NoShell -> context.getString(R.string.staging_sweep_no_shell)
        SkippedRun -> context.getString(R.string.staging_sweep_skipped)
        is Done -> {
            val swept = when (verdict) {
                SweepVerdict.Refused -> context.getString(
                    R.string.staging_sweep_refused,
                    found.size,
                    complaint,
                )
                SweepVerdict.LeftBehind -> context.getString(
                    R.string.staging_sweep_left,
                    removed,
                    left.joinToString(", "),
                )
                SweepVerdict.Removed -> context.getString(R.string.staging_sweep_clean, removed)
                SweepVerdict.NothingToDo -> null
            }
            // Appended rather than given a line of its own: it is a qualifier on what the sweep did,
            // and a second line would read as a second event. It is also the only sentence here that
            // explains a name a detector can still find in the directory.
            val otherInstall = otherInstallNote(context, leftToTheOtherInstall)
            when {
                otherInstall.isEmpty() -> swept
                swept == null -> otherInstall
                else -> "$swept $otherInstall"
            }
        }
    }

    /**
     * What this sweep left for the other install, or empty when there is no other install.
     *
     * Only the names, not what is on the device: the sweep never asked about them, so anything more
     * would be a claim it did not make.
     */
    private fun otherInstallNote(context: Context, names: List<String>): String =
        if (names.isEmpty()) {
            ""
        } else {
            context.getString(R.string.staging_sweep_other_install, names.joinToString(", "))
        }

    /**
     * The line for the app log when the directory was emptied on purpose, which is not a sweep.
     *
     * Its own sentence because the two are different claims about the same directory: a sweep says what
     * this app left behind, a clear says everything that was in there is gone - the stronger of the
     * two, and the one worth being able to find in the log afterwards.
     */
    fun clearLogLine(context: Context): String = when (this) {
        NoShell -> context.getString(R.string.residue_log_clear_none)
        SkippedRun -> context.getString(R.string.residue_log_clear_skipped)
        is Done -> context.getString(R.string.residue_log_clear, removed, left.size)
    }
}

/** What a sweep came to, in the four ways it can: the sentence for each is string assembly. */
internal enum class SweepVerdict { NothingToDo, Removed, LeftBehind, Refused }

internal object StagingSweep {

    /**
     * What a sweep removes: everything the app stages, with one exemption and one condition.
     *
     * Not a second list. A sweep that named its own paths would be a sweep that can quietly stop
     * covering one, and the catalogue is already the list of what this app writes there - so the set is
     * the catalogue itself, and a path added to one is swept by the other the moment it is catalogued.
     *
     * The exemption is [StagedResidue.sharedWithTheOtherInstall], and it is asked for only while the
     * other install is actually present. Those six names are the payload's rather than either app's, so
     * both write them, and a sweep cannot tell whose it is looking at: deleting one while the other app
     * is mid-run is deleting the payload that run is about to load. On a device without that install -
     * which is every device this fork is expected to be on - the question does not arise and the
     * exemption costs nothing, so the daemon copy is still cleaned up after every run.
     */
    fun removable(otherInstallPresent: Boolean): List<StagedPath> =
        if (!otherInstallPresent) StagedResidue.catalog
        else StagedResidue.catalog.filterNot { it.name in StagedResidue.sharedWithTheOtherInstall }

    /**
     * The command: say what is there, delete, say what `rm` said about it, say what is still there.
     *
     * One round trip rather than one per file, and self-checking rather than trusting `rm`'s exit
     * status - which is useless here rather than merely unreliable. The status of a script is the
     * status of its last command, and that is a `[ -e ]` test for a path this sweep has just deleted,
     * so a clean sweep would report failure every time. What `rm` has to say is therefore carried in
     * the output, under its own prefix, and the shell is told to end clean.
     *
     * `2>&1` on the delete is what puts a refusal where it can be read at all: a denied unlink writes
     * to stderr, which this transport does not carry.
     */
    internal fun command(paths: List<String>): String {
        val quoted = paths.map { shellQuote(it) }
        // The same question asked twice, under two different words, which is what makes one output
        // readable as both "what was there" and "what is there now".
        fun askedUnder(prefix: String) = quoted.joinToString("\n") { path ->
            "[ -e $path ] && printf '$prefix%s\\n' $path"
        }
        return askedUnder(FOUND_PREFIX) +
            "\nrm_out=\$(rm -f -- ${quoted.joinToString(" ")} 2>&1)\n" +
            "[ -n \"\$rm_out\" ] && printf '$SAID_PREFIX%s\\n' \"\$rm_out\"\n" +
            askedUnder(LEFT_PREFIX) +
            "\nexit 0"
    }

    /**
     * The paths a sweep's output names under [prefix].
     *
     * Everything else in the output is passed over, which is what lets `rm`'s own errors travel in the
     * same stream without being mistaken for paths. A path cannot mimic either prefix: these names are
     * absolute, so a line that starts with a prefix and then a path is one of these loops talking.
     */
    internal fun pathsIn(output: String, prefix: String): List<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith(prefix) }
        .map { it.removePrefix(prefix).trim() }
        .filter { it.isNotEmpty() }
        .toList()

    /**
     * Sweeps, unless another process is on a run.
     *
     * The guard is the point of the whole call: a boot install runs in the gate's own process, so a
     * sweep started by the app being opened could otherwise delete the payload another process is
     * about to execute. [RunInFlight] is what both processes can see, and a run in *this* process is the
     * caller's to have ended already - the sweep that follows a run is called after it.
     */
    fun sweepWhenQuiet(context: Context): SweepOutcome {
        if (RunInFlight.holder(context) != null) return SweepOutcome.SkippedRun
        return sweep(context)
    }

    /** The sweep itself, through the first shell that answers. */
    fun sweep(context: Context): SweepOutcome {
        val otherInstall = SiblingInstall.isPresent(context)
        return when (val outcome = remove(removable(otherInstall).map { it.path })) {
            // Carried on the outcome rather than logged here: this object returns lines for its callers
            // to log, and a note about what was left alone belongs on the same line as what was removed.
            is SweepOutcome.Done -> outcome.copy(
                leftToTheOtherInstall = if (otherInstall) {
                    StagedResidue.sharedWithTheOtherInstall.sorted()
                } else {
                    emptyList()
                },
            )
            else -> outcome
        }
    }

    /**
     * Removes these paths, one at a time from the caller's point of view and in one round trip here.
     *
     * The same command as a sweep, with the list the caller named instead of the catalogue: a row's own
     * delete button wants exactly this, and a delete of one file is not a different kind of operation
     * from a delete of twenty - it is the same `rm -f`, measured against the same "what was there" lines.
     *
     * `-f` and not `-r`: every name here is a path this app writes, and a name in the catalogue is a
     * file. A leftover that is a directory is another app's or an old build's, which the screen offers
     * through the clear rather than through a row.
     */
    fun remove(paths: List<String>): SweepOutcome {
        if (paths.isEmpty()) return SweepOutcome.Done(emptyList(), emptyList(), "")
        val command = command(paths)
        // Root first, then Shizuku's own shell: whichever one put the files there can take them away,
        // and the order keeps the quiet route - a Shizuku server that already answers as root - first.
        val result = KernelSuRuntime.rootShell(command)
            ?: KernelSuRuntime.unprivilegedShell(command)
            ?: return SweepOutcome.NoShell
        return SweepOutcome.Done(
            found = pathsIn(result.output, FOUND_PREFIX),
            left = pathsIn(result.output, LEFT_PREFIX),
            // `rm`'s own words, and only when it had any: its silence is the clean case, which is why
            // the exit status is not consulted - see [command].
            complaint = pathsIn(result.output, SAID_PREFIX)
                .joinToString(", ")
                .take(COMPLAINT_LIMIT),
        )
    }

    /**
     * Removes these paths unless a run is in flight.
     *
     * The same guard the sweep takes, for the same reason and with more force here: a row's delete can
     * name the payload itself, and the payload is executed out of this directory by the process that is
     * running it.
     */
    fun removeWhenQuiet(context: Context, paths: List<String>): SweepOutcome {
        if (RunInFlight.holder(context) != null) return SweepOutcome.SkippedRun
        return remove(paths)
    }

    /**
     * Empties the directory, which is more than a sweep does.
     *
     * A sweep is this app clearing up after itself: it names its own paths and leaves every other name
     * alone, because a name it does not write is not its to delete. This is the user asking for the
     * directory to be empty - the button somebody presses after a detector has told them what is in
     * there - so it removes what the listing found, the entries this app cannot account for included.
     *
     * That is a wider claim than `rm` of known names, so the screen that offers it names what is about
     * to go, and this refuses while a run is in flight: the payload is executed out of this directory,
     * which is the one thing here another process may be mid-way through using.
     */
    fun clearWhenQuiet(context: Context): SweepOutcome {
        if (RunInFlight.holder(context) != null) return SweepOutcome.SkippedRun
        return clear()
    }

    /** The clear itself, through the first shell that answers. Root first, since it can remove more. */
    fun clear(): SweepOutcome {
        val command = clearCommand()
        val result = KernelSuRuntime.rootShell(command)
            ?: KernelSuRuntime.unprivilegedShell(command)
            ?: return SweepOutcome.NoShell
        return SweepOutcome.Done(
            found = pathsIn(result.output, FOUND_PREFIX),
            left = pathsIn(result.output, LEFT_PREFIX),
            complaint = pathsIn(result.output, SAID_PREFIX)
                .joinToString(", ")
                .take(COMPLAINT_LIMIT),
        )
    }

    /**
     * The clear, as one command: say what is there, delete all of it with `rm -rf`, say what `rm` said,
     * say what is still there.
     *
     * The globs are the same two the listing uses, and for the same reason: a dot-name is exactly the
     * shape a staging marker takes. `-r` because a leftover can be a directory - `dalvik-cache` is one
     * on a real device - and `-f` so that an unmatched glob is silence rather than an error. The
     * directory itself is never removed, only its contents: it is a system directory with a mode this
     * app has no business rewriting.
     */
    internal fun clearCommand(directory: String = StagedResidue.DIRECTORY): String {
        val globs = "$directory/* $directory/.[!.]*"
        fun askedUnder(prefix: String) = "for e in $globs; do [ -e \"\$e\" ] || continue; " +
            "printf '$prefix%s\\n' \"\$e\"; done"
        return askedUnder(FOUND_PREFIX) +
            "\nrm_out=\$(rm -rf -- $globs 2>&1)\n" +
            "[ -n \"\$rm_out\" ] && printf '$SAID_PREFIX%s\\n' \"\$rm_out\"\n" +
            askedUnder(LEFT_PREFIX) +
            "\nexit 0"
    }

    /** What a path still there is reported under. Both are prefixes of a whole line, not of a name. */
    internal const val FOUND_PREFIX = "had "

    /** What a path that survived the delete is reported under. */
    internal const val LEFT_PREFIX = "left "

    /** What the delete's own output is reported under, which is the refusal if there was one. */
    internal const val SAID_PREFIX = "said "

    /** How much of a complaint is worth carrying; a refusal is a phrase, not a transcript. */
    private const val COMPLAINT_LIMIT = 200
}
