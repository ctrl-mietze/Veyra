package ctrl.mietze.veyraroot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether the run's log is following its tail. */
class LogFollowTest {

    @Test
    fun `a fresh panel follows`() {
        assertTrue(LogFollow.Start.following)
        assertFalse(LogFollow.Start.jumpOffered)
    }

    @Test
    fun `scrolling up stops the follow and offers the way back once something arrives`() {
        val scrolledUp = LogFollow.Start.atEnd(atEnd = false)

        assertFalse(scrolledUp.following)
        // Nothing has been missed yet, so there is nothing to jump to and a chip would be a dead button.
        assertFalse(scrolledUp.jumpOffered)

        val missed = scrolledUp.onNewLines()
        assertTrue(missed.jumpOffered)
        assertFalse(missed.following)
    }

    @Test
    fun `coming back to the end resumes, whatever was missed`() {
        val resumed = LogFollow.Start.atEnd(false).onNewLines().onNewLines().atEnd(atEnd = true)

        assertTrue(resumed.following)
        assertFalse(resumed.jumpOffered)
    }

    @Test
    fun `once a person has scrolled up, the next line offers the way back`() {
        // The two events arrive from different places - a gesture and a payload's output - so this holds for
        // every state they can be in when it happens.
        listOf(
            LogFollow.Start,
            LogFollow.Start.onNewLines(),
            LogFollow.Start.atEnd(atEnd = false),
            LogFollow.Start.atEnd(atEnd = false).onNewLines(),
        ).forEach { before ->
            assertTrue("from $before", before.atEnd(atEnd = false).onNewLines().jumpOffered)
        }
    }

    @Test
    fun `a line that was on screen when the scrolling started is not a missed one`() {
        // The two orders are genuinely different, and this is the difference: a line that arrived while the
        // panel was following has been read, so scrolling up straight after it must not offer to take you
        // back to it.
        assertFalse(LogFollow.Start.onNewLines().atEnd(atEnd = false).jumpOffered)
    }

    @Test
    fun `a line that arrives while following is not a missed one`() {
        val following = LogFollow.Start.onNewLines()

        assertTrue(following.following)
        assertFalse(following.jumpOffered)
    }

    @Test
    fun `a log nobody has scrolled is never offered the chip`() {
        // A run's worth of lines: the panel that is watching is watching, and a chip over it would be a
        // button about nothing.
        var state = LogFollow.Start
        repeat(500) { state = state.onNewLines() }

        assertTrue(state.following)
        assertFalse(state.jumpOffered)
    }
}
