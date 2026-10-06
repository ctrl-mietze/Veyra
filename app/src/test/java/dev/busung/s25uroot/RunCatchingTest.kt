package ctrl.mietze.veyraroot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one difference between this and `runCatching`, and why it matters enough to have its own helper.
 *
 * The premise is asserted rather than described: the first test shows the plain `runCatching` doing the
 * wrong thing, so the tests below it are known to be testing a real difference and not a tidier spelling
 * of the same behaviour.
 */
class RunCatchingTest {

    @Test
    fun `the plain one files a cancelled call as a failure`() = runBlocking {
        // The bug this helper exists for, in three lines: a cancelled call is reported to the branch that
        // handles failures, which is a branch that cannot know the work was abandoned.
        var reported = 0
        runCatching {
            withTimeout(1L) { delay(1_000L) }
        }.onFailure { reported++ }
        assertEquals(1, reported)
    }

    @Test
    fun `a caller's own timeout escapes instead of arriving as a failure`() = runBlocking {
        var reported = 0
        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                runCatchingCancellable {
                    withTimeout(1L) { delay(1_000L) }
                }.onFailure { reported++ }
            }
        }
        assertFalse("the failure branch ran for a timeout", reported > 0)
    }

    @Test
    fun `an ordinary cancellation escapes too`() {
        var reported = 0
        assertThrows(CancellationException::class.java) {
            runCatchingCancellable<Unit> { throw CancellationException("stopped") }
                .onFailure { reported++ }
        }
        assertEquals(0, reported)
    }

    @Test
    fun `everything else is still a failure, with the exception it was`() {
        val failure = IllegalStateException("the port was closed")
        val outcome = runCatchingCancellable { throw failure }
        assertTrue(outcome.isFailure)
        assertEquals(failure, outcome.exceptionOrNull())
    }

    @Test
    fun `a value that came back is a success`() {
        assertEquals("wrote", runCatchingCancellable { "wrote" }.getOrNull())
    }
}
