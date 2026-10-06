package ctrl.mietze.veyraroot

/**
 * The two ways a run that wanted Shizuku can go ahead anyway.
 *
 * One rule, written down once, because it is answered in two places that must not drift: the question
 * the run screen asks when Shizuku is not running, and the actions the boot notification carries when
 * its own wait for Shizuku ran out. A boot has nobody to ask, so it says what it could not do and hands
 * the same two answers to the notification - and what each answer *means* is decided here rather than at
 * either caller.
 *
 * [extra] is the value that travels in the intent, so a notification action and the screen that reads it
 * name the same thing rather than two strings that look alike.
 */
internal enum class RunAnswer(val extra: String) {
    /**
     * Try to start Shizuku here, and run through it if it comes up.
     *
     * The attempt is the ordinary one, with every route it has: this device's root, a saved wireless
     * pairing, or a configured start token. A start that does not produce a binder leaves the question
     * standing rather than claiming to have answered it.
     */
    RetryShizuku("retry_shizuku"),

    /**
     * Run this attempt the way it would run with Use Shizuku off.
     *
     * About this attempt only: the setting is left alone, because turning it off would be a second
     * decision the person did not make. A target that needs a shell without a pairing to carry it is
     * refused by the run itself, in its own words.
     */
    StandardMethod("standard_method");

    /** Whether the run this answer asks for skips Shizuku. */
    val withoutShizuku: Boolean
        get() = this == StandardMethod

    /** Whether the screen should try to start Shizuku as soon as it is up, before running. */
    val startsShizukuFirst: Boolean
        get() = this == RetryShizuku

    companion object {
        /** The answer an intent's extra names, or null when it names none this build knows. */
        fun fromExtra(value: String?): RunAnswer? =
            value?.takeIf(String::isNotBlank)?.let { named -> entries.firstOrNull { it.extra == named } }
    }
}

/**
 * Which of [RunAnswer]'s answers a refusal is worth offering.
 *
 * The difference is whether a retry has anything to retry with, and saying so is the point: offering
 * "start Shizuku" on a device with no root, no pairing and no token is a button that cannot work, which
 * is the same silence as offering nothing at all. Decided by the same reading of the device the wait
 * itself used - [shizukuWait] - so the actions cannot promise a route the wait already knew was missing.
 */
internal enum class ShizukuRefusalActions {
    /** Something here could start it and did not, so both answers are offered. */
    RetryOrStandard,

    /** Nothing here can start it, so only the way that does not need it is worth offering. */
    StandardOnly;

    /** Whether starting Shizuku is worth offering as an answer to this refusal. */
    val offersRetry: Boolean
        get() = this == RetryOrStandard
}
