package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InBootRetryTest {

    @Test
    fun `the wait is long enough to be the measurement, not a pause`() {
        // The number is a measured clearing time from two independent projects, so it is a minute
        // rather than a round small interval: a wait nobody measured would be worse than no wait,
        // because it would spend the user's time and still retry into the same state.
        assertTrue(InBootRetry.WAIT_SECONDS >= 45)
        assertTrue(InBootRetry.WAIT_SECONDS <= 120)
    }

    @Test
    fun `the countdown starts at the whole wait`() {
        assertEquals(InBootRetry.WAIT_SECONDS, InBootRetry.remainingSeconds(0L))
    }

    @Test
    fun `each second spent reads as one second less`() {
        assertEquals(InBootRetry.WAIT_SECONDS - 1, InBootRetry.remainingSeconds(1_000L))
        assertEquals(InBootRetry.WAIT_SECONDS - 10, InBootRetry.remainingSeconds(10_000L))
    }

    @Test
    fun `a partly spent second does not shorten the countdown`() {
        // The label is a whole number of seconds, so a tick that lands mid-second must not take one
        // off early: a countdown that skips is a countdown nobody trusts.
        assertEquals(InBootRetry.WAIT_SECONDS, InBootRetry.remainingSeconds(999L))
    }

    @Test
    fun `a finished wait stays finished`() {
        // The caller starts the run at zero, and a late tick must not restart the wait.
        assertEquals(0, InBootRetry.remainingSeconds(InBootRetry.WAIT_SECONDS * 1000L))
        assertEquals(0, InBootRetry.remainingSeconds(InBootRetry.WAIT_SECONDS * 1000L + 30_000L))
    }
}
