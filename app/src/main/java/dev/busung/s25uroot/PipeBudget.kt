package ctrl.mietze.veyraroot

import android.content.Context

/**
 * The kernel's pipe page budget, and what a payload that runs into it looks like.
 *
 * A pipe in this exploit is not a small one: the race is built on a pipe buffer whose pages are
 * charged to the user's budget (`fs.pipe-user-pages-soft` and `-hard`), and the budget is per boot
 * rather than per process. An attempt that spends it leaves the next attempt unable to allocate at
 * all, which the payload reports as a kernel refusal on the sizing call and which the app, until now,
 * reported as a generic exploit failure. Every later attempt in that boot fails the same way, so the
 * symptom is a phone that "stopped working" after the first failure rather than a run that failed
 * once - and the answer is a restart, not another attempt.
 *
 * The check is a pair read from the payload's own output, not a guess from a single word. A payload
 * prints its pipe limits as ordinary information (that is what the pre-check in the reference
 * implementation is for), and those lines are not a diagnosis; what is a diagnosis is one of those
 * limits appearing in the same line as a refusal.
 */
internal object PipeBudget {

    /**
     * What a payload calls the pipe page pool when it names it.
     *
     * `F_SETPIPE_SZ` is the sizing call itself, and the rest are the sysctls that bound how many pages
     * of pipe a user may hold at once.
     */
    private val LIMITS = listOf(
        "F_SETPIPE_SZ",
        "pipe-max-size",
        "pipe-user-pages-soft",
        "pipe-user-pages-hard",
    )

    /** How a kernel refuses an allocation, as payloads word it. */
    private val REFUSALS = listOf(
        "EPERM",
        "Operation not permitted",
        "denied",
        "failed",
    )

    /**
     * The payload's own line that shows it was refused for the budget, or null.
     *
     * Null is the answer for the ordinary case where the payload prints the limits it is working
     * under, and for a refusal about something else entirely: a diagnosis that fires on those would
     * send someone to restart a phone that has nothing wrong with it.
     */
    fun evidenceIn(log: String): String? = log.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .firstOrNull { line ->
            val namesALimit = LIMITS.any { limit -> line.contains(limit, ignoreCase = limit != "F_SETPIPE_SZ") }
            val refuses = REFUSALS.any { refusal -> line.contains(refusal, ignoreCase = true) }
            namesALimit && refuses
        }

    /**
     * Whether the boot on record is the boot being asked about.
     *
     * Pure, so the rule can be checked without a device: the budget is refilled by a restart, and the
     * only thing that says a restart happened is the boot token changing. A recorded token that is
     * missing, or that belongs to the boot that has ended, is not this boot's spent budget.
     */
    fun isSpentFor(recordedBootToken: String?, bootToken: String?): Boolean =
        recordedBootToken != null && bootToken != null && recordedBootToken == bootToken

    /**
     * Whether a payload has already told this app that the boot's budget is gone.
     *
     * Recorded per boot rather than per run because that is what the budget is, and because it is what
     * lets the next run refuse before it stages anything: an attempt in a boot whose budget is spent
     * cannot succeed, so it should not be started. Keyed to the boot token rather than to a flag that
     * is cleared somewhere, so a restart ends it without anything having to remember to clear it.
     */
    fun spentInBoot(context: Context, bootToken: String?): Boolean =
        isSpentFor(
            prefs(context).getString(SPENT_BOOT, null),
            bootToken,
        )

    /** Records that this boot's budget is spent, as the payload's own output said it was. */
    fun rememberSpent(context: Context, bootToken: String) {
        prefs(context).edit().putString(SPENT_BOOT, bootToken).commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "pipe_budget"
    private const val SPENT_BOOT = "spent_boot"
}
