package ctrl.mietze.veyraroot

/**
 * What the Logs tab is being asked to show, in the two questions it is asked in.
 *
 * One object rather than two pieces of screen state, because the two are not independent: a line is shown
 * when it clears the floor *and* matches the text, and held apart they would be applied in two places that
 * could disagree about the same line.
 *
 * Pure, so the answers worth checking - what the Errors floor lets through, that turning it off only lowers
 * the floor it raised - are checked without a device.
 *
 * There used to be a third question, a set of tags drawn as a row of chips with a count beside each one.
 * It is **gone**, and deliberately not replaced by a smaller version of itself: the row was the tallest
 * thing above the log, and for the question it actually answered - where is the noise - the text field is
 * one word away.
 */
internal data class LogFilter(
    val minLevel: AppLogLevel = AppLogLevel.Debug,
    val query: String = "",
) {
    /**
     * Whether the Errors chip is on: the floor is a warning, so everything that went wrong is on screen.
     *
     * Warnings are under it on purpose. A warning is what makes someone open this tab - a daemon that did
     * not answer, a grant that was refused - and an error is the same complaint at its loudest, so one chip
     * covering both is one idea rather than two. There used to be a second chip one level above this one,
     * named after the error level, and it showed a subset of what the first already showed.
     */
    val errorsOnly: Boolean get() = minLevel.ordinal >= FAILURES_FLOOR.ordinal

    /**
     * Turns the Errors chip on, or takes back down the floor it raised.
     *
     * Turning it off only lowers a floor of warnings - the level this chip put there. A higher floor is one
     * someone picked deliberately, and clearing it would be this chip answering a question it was not
     * asked.
     */
    fun withErrorsOnly(on: Boolean): LogFilter = if (on) {
        copy(minLevel = FAILURES_FLOOR)
    } else {
        copy(minLevel = if (minLevel == FAILURES_FLOOR) AppLogLevel.Debug else minLevel)
    }

    /** Whether a line belongs on screen. */
    fun matches(entry: AppLogEntry): Boolean = AppLogFormat.matches(entry, minLevel, query)

    companion object {
        /**
         * Where the Errors chip puts the floor: warnings and up.
         *
         * A value rather than `AppLogLevel.Error`, because the chip is about everything that went wrong
         * rather than about one level's name - and a floor written as `Error` here would silently hide
         * every warning the tab exists to show.
         */
        val FAILURES_FLOOR: AppLogLevel = AppLogLevel.Warn
    }
}
