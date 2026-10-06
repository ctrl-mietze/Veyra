package ctrl.mietze.veyraroot

/**
 * A retry in the boot that already ran one, after a wait.
 *
 * A failed attempt leaves state behind: the payload's own oracle and slab state, and a pipe page
 * budget the shell user has already spent. A second attempt made straight away usually fails on that
 * state rather than on the exploit, which is why "try again" is the least reliable of the three
 * answers a failed run has.
 *
 * The wait is a measurement rather than a guess. Two independent projects that drive this payload hit
 * the same thing and wrote the same answer into their code: the state clears itself in about a
 * minute. One chains whole payload relaunches with a 60-second gap between them and records the
 * observation that produced it; the other wraps that in a further loop with the same wait, after
 * refusing to keep hammering a boot whose attempt count has run out.
 *
 * So this sits between the other two: restarting is still the best odds, retrying at once is still the
 * fastest, and this is the one that pays a minute for a cleaner boot without giving up the session.
 */
object InBootRetry {
    /** How long to leave the device alone before the payload runs again in this boot. */
    const val WAIT_SECONDS = 60

    /**
     * Seconds still to wait, from the milliseconds already spent waiting.
     *
     * Rounded up, so a partly spent second still reads as a whole one: truncating told the user 59 s
     * with 999 ms gone, which is less time than the run actually had left. Never negative, because the
     * caller starts the run at zero and a late tick must not read as a fresh wait.
     */
    fun remainingSeconds(elapsedMillis: Long): Int {
        val left = (WAIT_SECONDS * 1000L - elapsedMillis).coerceAtLeast(0L)
        return ((left + 999L) / 1000L).toInt()
    }
}
