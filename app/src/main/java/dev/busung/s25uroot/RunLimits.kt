package ctrl.mietze.veyraroot

import kotlin.math.abs

/**
 * The three ceilings a run is given, resolved once when it starts.
 *
 * A value type rather than three lookups, so the run and the run-plan screen read the same numbers: a
 * plan that described a different ceiling from the one the run enforced would be worse than no plan.
 */
internal data class RunCeilings(
    /** Silence before the payload is treated as stalled. */
    val stallMillis: Long,
    /** How long one whole run may take. */
    val totalMillis: Long,
    /** How long one helper command may take. */
    val helperMillis: Long,
)

/** Which of the three ceilings a change is for. */
internal enum class RunLimit { Total, Stall, Helper }

/**
 * The three ceilings as they are stored, which is what the settings and the run plan read together.
 *
 * One value rather than three lookups, so a screen cannot show a mix of a value that was just saved and
 * one that was not.
 */
internal data class RunLimitsSettings(
    val totalSeconds: Int,
    val stallSeconds: Int,
    val helperSeconds: Int,
)

/**
 * The ceilings this app puts on a run, and which of them a person may change.
 *
 * A run has two owners, and only one of them is this app. The **payload profile** in the feed decides
 * *how the exploit is attempted* — how many tries, how long each one gets, which way it looks for the
 * slide — and the app hands those to the payload as environment variables. They are the payload's own
 * account of itself, and a setting that overrode them would be this app claiming to know better than
 * the thing doing the work, so there isn't one.
 *
 * What this app owns is the *stopping*: how long a whole run may take, how much silence means a stalled
 * payload, and how long one helper command may sit there. Those are the numbers a device and a boot
 * change — a cold device settles late, an overloaded one prints nothing for a while, a slow phone takes
 * longer over each step — so those are the ones worth a setting. They are also the ones whose failure is
 * *this app's* decision rather than the payload's, which is what makes them safe to move: a run that
 * reaches one is reported as a ceiling the user set, not as a payload that gave up.
 */
internal object RunLimits {

    /** What a run gets unless it is told otherwise. */
    const val DEFAULT_TOTAL_SECONDS = 900
    const val DEFAULT_HELPER_SECONDS = 120

    /**
     * The shipped silence limit, at the whole-run ceiling rather than below it.
     *
     * It used to be ninety seconds, and that was wrong: the root helper is quiet *by design* while a
     * scheduler-sensitive payload is alive - it prints nothing on the transport until the payload
     * exits - so a silence-based watchdog shorter than the run itself ends healthy runs. The validated
     * baseline this app's runtime is measured against found the same thing the expensive way and settled
     * on fifteen minutes, which is exactly [DEFAULT_TOTAL_SECONDS]: at this value the watchdog cannot
     * fire before the whole-run deadline that is already there, so the app stops guessing about silence
     * and lets the payload finish.
     *
     * It stays a setting, because a device where a payload really has hung is a device whose owner may
     * want a shorter leash - but the *default* is pinned by a test so it cannot quietly drift back down
     * to a value that kills working runs.
     */
    const val DEFAULT_STALL_SECONDS = 900

    /**
     * The floor under a fresh-session profile's whole-run ceiling.
     *
     * A fresh P0 session hands its pacing to the payload: one payload-native attempt, no attempt budget,
     * and no stall watchdog — which is exactly the slow case. A ceiling below this would cut a
     * payload-native attempt off *between* its own decisions rather than let it finish, so the setting
     * can raise this ceiling but never lower a fresh session below the app's own hour.
     */
    const val FRESH_SESSION_FLOOR_SECONDS = 3600

    /** What the settings offer. Rounded to these rather than free-form, as [BootSettle] does. */
    // The offers start above the value that was found to kill healthy runs, so the menu cannot be read
    // as an invitation to go there.
    val allowedStallSeconds = listOf(120, 180, 300, 600, 900)
    val allowedTotalSeconds = listOf(300, 600, 900, 1200, 1800, 3600, 5400, 7200)
    val allowedHelperSeconds = listOf(30, 60, 120, 180, 300, 600)

    fun normalizeStallSeconds(seconds: Int): Int =
        nearest(allowedStallSeconds, seconds, DEFAULT_STALL_SECONDS)

    fun normalizeTotalSeconds(seconds: Int): Int =
        nearest(allowedTotalSeconds, seconds, DEFAULT_TOTAL_SECONDS)

    fun normalizeHelperSeconds(seconds: Int): Int =
        nearest(allowedHelperSeconds, seconds, DEFAULT_HELPER_SECONDS)

    /**
     * The ceilings one run is given, given what is stored and which kind of session it is.
     *
     * Pure, so the fresh-session rule [FRESH_SESSION_FLOOR_SECONDS] can be checked without a device —
     * and so that a stored value that is not one of the offered ones still resolves to one that is,
     * rather than reaching the run as a number nobody chose.
     */
    fun resolve(
        stallSeconds: Int,
        totalSeconds: Int,
        helperSeconds: Int,
        freshSession: Boolean,
    ): RunCeilings = RunCeilings(
        stallMillis = normalizeStallSeconds(stallSeconds) * 1_000L,
        totalMillis = effectiveSeconds(RunLimit.Total, totalSeconds, freshSession) * 1_000L,
        helperMillis = normalizeHelperSeconds(helperSeconds) * 1_000L,
    )

    /** The same resolution for the three values as one object. */
    fun resolve(settings: RunLimitsSettings, freshSession: Boolean): RunCeilings = resolve(
        stallSeconds = settings.stallSeconds,
        totalSeconds = settings.totalSeconds,
        helperSeconds = settings.helperSeconds,
        freshSession = freshSession,
    )

    /** What a run gets when nothing has been chosen: the app's own values. */
    fun defaultCeilings(freshSession: Boolean): RunCeilings =
        resolve(DEFAULT_STALL_SECONDS, DEFAULT_TOTAL_SECONDS, DEFAULT_HELPER_SECONDS, freshSession)

    /** The shipped value for one of the three, which is what a reset puts back. */
    fun defaultSeconds(limit: RunLimit): Int = when (limit) {
        RunLimit.Total -> DEFAULT_TOTAL_SECONDS
        RunLimit.Stall -> DEFAULT_STALL_SECONDS
        RunLimit.Helper -> DEFAULT_HELPER_SECONDS
    }

    /** The shipped values as one object, for a reset and for the app's own starting state. */
    fun defaults(): RunLimitsSettings = RunLimitsSettings(
        totalSeconds = DEFAULT_TOTAL_SECONDS,
        stallSeconds = DEFAULT_STALL_SECONDS,
        helperSeconds = DEFAULT_HELPER_SECONDS,
    )

    /** What the settings offer for one of the three. */
    fun options(limit: RunLimit): List<Int> = when (limit) {
        RunLimit.Total -> allowedTotalSeconds
        RunLimit.Stall -> allowedStallSeconds
        RunLimit.Helper -> allowedHelperSeconds
    }

    /** Normalizes a value for one of the three, so a caller does not have to know which is which. */
    fun normalize(limit: RunLimit, seconds: Int): Int = when (limit) {
        RunLimit.Total -> normalizeTotalSeconds(seconds)
        RunLimit.Stall -> normalizeStallSeconds(seconds)
        RunLimit.Helper -> normalizeHelperSeconds(seconds)
    }

    /**
     * The offering a value is judged against, and why it may not be what was chosen.
     *
     * Only one of the three has a rule beyond its own menu, and hiding it in the resolution would leave
     * a user who picked five minutes for a fresh session wondering why the plan said an hour.
     */
    fun effectiveSeconds(limit: RunLimit, seconds: Int, freshSession: Boolean): Int {
        val chosen = normalize(limit, seconds)
        return if (limit == RunLimit.Total && freshSession) {
            maxOf(chosen, FRESH_SESSION_FLOOR_SECONDS)
        } else {
            chosen
        }
    }

    /**
     * The setting's own label for a value, in the shape [BootSettle.label] uses.
     *
     * Whole hours read as hours, the way the run plan already writes them: the same ceiling printed two
     * ways in two places is how a user comes to doubt one of them.
     */
    fun label(seconds: Int): String {
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            seconds >= 3_600 && rest == 0 && minutes % 60 == 0 -> "${minutes / 60} h"
            seconds < 60 -> "$rest s"
            minutes == 0 -> "$rest s"
            rest == 0 -> "$minutes min"
            else -> "$minutes min $rest s"
        }
    }

    /** A duration in a plan row, which is where the mid-size values are not all whole minutes. */
    fun durationLabel(millis: Long): String = label((millis / 1_000L).toInt())

    private fun nearest(allowed: List<Int>, seconds: Int, fallback: Int): Int =
        allowed.minByOrNull { abs(it - seconds) } ?: fallback
}
