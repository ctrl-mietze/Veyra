package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two answers a Shizuku refusal can be continued with.
 *
 * What is pinned here is the part that two callers have to agree on: the boot notification writes an
 * extra and the run screen reads it, and the screen decides what to do from [RunAnswer] alone. If those
 * two drifted, an action labelled "Run without Shizuku" could start a run that waits for Shizuku - a
 * button that does the opposite of what it says, which is worse than no button.
 */
class RunAnswerTest {

    @Test
    fun `every answer survives a round trip through the intent extra`() {
        RunAnswer.entries.forEach { answer ->
            assertEquals(answer, RunAnswer.fromExtra(answer.extra))
        }
    }

    /** Extras are values, not names: an unreadable one must not fall back to either answer. */
    @Test
    fun `an unknown or missing extra names no answer`() {
        assertNull(RunAnswer.fromExtra(null))
        assertNull(RunAnswer.fromExtra(""))
        assertNull(RunAnswer.fromExtra("start_shizuku"))
    }

    /** The one thing a standard-method run must do, and the one thing it must not. */
    @Test
    fun `the standard method skips Shizuku and does not start it`() {
        assertTrue(RunAnswer.StandardMethod.withoutShizuku)
        assertFalse(RunAnswer.StandardMethod.startsShizukuFirst)
    }

    /** Retrying means running *through* Shizuku, so it holds and it starts it rather than skipping. */
    @Test
    fun `retrying Shizuku does not skip it and starts it first`() {
        assertFalse(RunAnswer.RetryShizuku.withoutShizuku)
        assertTrue(RunAnswer.RetryShizuku.startsShizukuFirst)
    }

    /**
     * A retry is only worth offering when something on the device can start Shizuku.
     *
     * The refusal that says "nothing here can start it" is produced from the same reading of the device
     * as this, so a "Start Shizuku" action on it would be a button the app already knew could not work.
     */
    @Test
    fun `only a refusal with a route offers the retry`() {
        assertTrue(ShizukuRefusalActions.RetryOrStandard.offersRetry)
        assertFalse(ShizukuRefusalActions.StandardOnly.offersRetry)
    }
}
