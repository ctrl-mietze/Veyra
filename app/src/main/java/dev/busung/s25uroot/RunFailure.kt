package ctrl.mietze.veyraroot

import androidx.annotation.StringRes

/**
 * Where a run was when it stopped.
 *
 * A message on its own rarely answers "what now?": the same wording can come out of a download,
 * the exploit, or the KernelSU load, and the stage is what tells them apart. It is recorded with
 * every failure, so a run that ended an hour ago still says where it ended.
 */
enum class RunStage(@StringRes val label: Int) {
    Transport(R.string.stage_transport),
    Target(R.string.stage_target),
    Download(R.string.stage_download),
    Exploit(R.string.stage_exploit),
    KernelSu(R.string.stage_kernel_su),
    Verify(R.string.stage_verify),
}

/**
 * A run that stopped: the stage it stopped in, the reason the app reports, and the tail of what the
 * payload or helper last said. For an exploit failure the last part is usually the only evidence
 * that says *which* part of the payload gave up, since the payload's own lines are the only account
 * of the kernel race.
 */
data class RunFailure(
    val stage: RunStage,
    val reason: String,
    val evidence: List<String> = emptyList(),
    /**
     * True when the protection this run set up is what refused the write that ended it.
     *
     * Carried on the failure rather than worked out where it is shown, because the evidence on the
     * card is only the last few lines and the rule needs the whole log. The screen also has to be able
     * to offer the fix, and this is the one failure whose fix is a switch in this app.
     */
    val readOnlyWall: Boolean = false,
    /**
     * Why a retry in this boot cannot be offered, when it cannot.
     *
     * Carried on the failure because it changes what can be offered next, and it is a fact about the
     * run rather than about its message: an attempt in this boot either would put a second payload on
     * top of one that may still be running, or would be refused the pipe pages the exploit needs. The
     * two have the same consequence - the only answer that clears the boot is a restart - and naming
     * the cause is what lets the screen say which one it is instead of refusing for no stated reason.
     */
    val inBootRetryBlocked: InBootRetryBlock? = null,
) {
    companion object {
        /**
         * A failure whose reason is reduced to one short line by [failureSummary].
         *
         * Going through here rather than through the constructor is what keeps the card readable:
         * a message that arrives carrying a log is turned into a cause, not rendered as one.
         */
        fun of(
            stage: RunStage,
            reason: String,
            evidence: List<String> = emptyList(),
            readOnlyWall: Boolean = false,
            inBootRetryBlocked: InBootRetryBlock? = null,
        ): RunFailure =
            RunFailure(stage, failureSummary(reason), evidence, readOnlyWall, inBootRetryBlocked)
    }
}

/**
 * Why a failed run cannot offer a retry in the boot it failed in.
 *
 * Each carries the notice the screen shows, because the screen's question is "why is there no retry
 * here?" and the answer differs: one is a payload that may still be writing to the kernel, the other
 * is a budget the kernel has already given away until the next boot.
 */
enum class InBootRetryBlock(@StringRes val notice: Int) {
    /** The payload could not be confirmed stopped, so a retry would be a second payload. */
    PayloadMayStillRun(R.string.install_payload_may_still_run),

    /** The boot's pipe page budget is spent, and a restart is what refills it. */
    PipeBudgetSpent(R.string.install_pipe_budget_spent),
}

/**
 * The meaningful tail of a run log. Blank lines are dropped and long lines are clipped, because
 * payload output is printed to a narrow monospace view and an unclipped line there pushes the rest
 * out of sight.
 */
internal fun failureEvidence(log: String, maxLines: Int = 4, maxLength: Int = 160): List<String> =
    log.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
        .takeLast(maxLines)
        .map { line -> if (line.length <= maxLength) line else line.take(maxLength - 1) + "\u2026" }

/**
 * A reason as one short line.
 *
 * The reason is rendered as the cause under the failed stage, in a card, so anything long enough to
 * be a log belongs in the log instead: a message that carried a payload's whole output turned the
 * failure card into a page of text with the stage nowhere in sight. Anything after the first line is
 * dropped here rather than there, so a message can never do that again, and the payload's own lines
 * still reach the card as [failureEvidence] and the log.
 */
internal fun failureSummary(reason: String, maxLength: Int = 240): String {
    val line = reason.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
    return if (line.length <= maxLength) line else line.take(maxLength - 1) + "\u2026"
}

/** Signal names for the codes a process killed by a signal reports through its exit status. */
private val SIGNAL_NAMES = mapOf(
    4 to "SIGILL",
    6 to "SIGABRT",
    7 to "SIGBUS",
    8 to "SIGFPE",
    9 to "SIGKILL",
    11 to "SIGSEGV",
    13 to "SIGPIPE",
    15 to "SIGTERM",
)

/**
 * What a shell reports as a signal rather than an exit code, read back.
 *
 * `128 + n` is how a killed process is reported by a shell, which the app then sees as an exit code.
 * Saying `137` tells nobody anything; saying that the payload was killed by signal 9 is the single
 * most useful thing a run can report about a payload that died without choosing to.
 */
internal fun exitCodeSummary(exitCode: Int): String? {
    val signal = exitCode - 128
    if (exitCode !in 129..192 || signal <= 0) return null
    val name = SIGNAL_NAMES[signal]
    return if (name != null) "signal $signal ($name)" else "signal $signal"
}

/**
 * The short account of a payload that stopped, for the exit message.
 *
 * The payload's whole output used to be inlined here, which is what made a failed run unreadable.
 * Only the reading of the status survives, because the payload's lines are in the log and, clipped,
 * on the failure card. Kept in the shape the message already expects, so every translation stays
 * valid.
 */
internal fun payloadExitDetail(exitCode: Int): String =
    exitCodeSummary(exitCode)?.let { " \u2014 $it" }.orEmpty()
