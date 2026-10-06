package ctrl.mietze.veyraroot

/**
 * Whether the run's log is following its tail, and whether anything arrived while it was not.
 *
 * The panel used to scroll to the bottom on every line, which is right for watching a run and wrong for
 * reading one: an attempt to scroll back through a twelve-minute exploit was undone by the next line the
 * payload printed. So following is a state rather than a reflex - it stops the moment a person scrolls up,
 * starts again when they come back to the end, and while it is off the lines that arrive are counted as
 * missed so the panel can offer the way back down.
 *
 * Pure, and a value rather than three booleans, because the transitions are what matters: a scroll and a line
 * can arrive in either order, and the state after both has to be the same.
 */
internal data class LogFollow(
    /** Whether the newest line should stay on screen. */
    val following: Boolean = true,

    /** Whether lines have arrived since following stopped - the chip's reason to exist. */
    val missedLines: Boolean = false,
) {

    /** Output arrived. Following, it is already on screen; not following, it is one more line missed. */
    fun onNewLines(): LogFollow = if (following) this else copy(missedLines = true)

    /** Where the panel ended up, after a scroll by the person reading it. */
    fun atEnd(atEnd: Boolean): LogFollow =
        if (atEnd) LogFollow() else copy(following = false)

    /**
     * Whether to offer the way back to the newest line.
     *
     * Both halves again: a chip over a log that has not moved would be a button that does nothing, and a log
     * that has run on while nobody was looking is exactly the case it is for.
     */
    val jumpOffered: Boolean get() = !following && missedLines

    companion object {
        /** The state a panel starts in, and the one the chip puts it back into. */
        val Start = LogFollow()
    }
}
