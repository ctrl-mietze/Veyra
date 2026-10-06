package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claim that decides which writer still owns the install screen.
 *
 * The case it exists for is not hypothetical: a lookup that starts when the screen opens fetches the
 * support catalog without suspending, so cancelling it does nothing, and its write lands after the run
 * it was racing has already reported the exploit. These tests pin the rule that makes that write a
 * no-op rather than a screen that was live and wrong at once.
 */
class PublishClaimTest {

    @Test
    fun `a later claim takes the screen from an earlier one`() {
        val claim = PublishClaim()
        val lookup = claim.claim()
        val run = claim.claim()

        assertFalse("the lookup must not publish over the run", claim.holds(lookup))
        assertTrue("the run owns the screen", claim.holds(run))
    }

    @Test
    fun `the holder of the newest claim keeps it until something newer claims`() {
        val claim = PublishClaim()
        val run = claim.claim()

        assertTrue(claim.holds(run))
        assertTrue(claim.holds(run))
    }

    /** Two claims never share a token, so a stale one cannot pass by looking current again. */
    @Test
    fun `every claim is a token of its own`() {
        val claim = PublishClaim()
        val tokens = (1..5).map { claim.claim() }

        assertEquals(5, tokens.toSet().size)
    }

    /** A fresh claim holds, which is what lets the very first lookup publish at all. */
    @Test
    fun `the first claim holds until a run starts`() {
        val claim = PublishClaim()
        val first = claim.claim()

        assertTrue(claim.holds(first))
    }

    /**
     * A run that stops to ask about Shizuku takes the screen before it writes the question.
     *
     * This is the same race as the run above, in its newest place. The question is a state of the
     * screen like any other, and the lookup that the screen starts when it opens publishes a state
     * built with no prompt in it - so a lookup whose fetch returned 50 ms after the question was set
     * replaced the question with "ready to install". The run had not started and the dialog was gone,
     * which is indistinguishable from the app hanging.
     *
     * The sequence is the one that happened: the screen opens and the lookup claims, the run is asked
     * for and claims (and holds), and the lookup's fetch then returns and tries to publish.
     */
    @Test
    fun `a lookup cannot publish over a run that stopped to ask`() {
        val claim = PublishClaim()
        val lookup = claim.claim()
        val heldRun = claim.claim()

        assertFalse("the lookup must not publish over the question", claim.holds(lookup))
        assertTrue("the held run owns the screen", claim.holds(heldRun))
    }
}
