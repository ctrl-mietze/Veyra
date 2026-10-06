package ctrl.mietze.veyraroot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The network wait's three endings, and the order it decides them in.
 *
 * The wait is the longest one in the app - five minutes - and it holds a foreground service and a wake
 * lock while it runs, so every way it can end is worth pinning rather than only the happy one. The
 * `Context` overload cannot be driven from here at all, which is why the loop takes its own reading and
 * is tested through that.
 *
 * The tick is a millisecond here rather than a second: what these assert is how many times the loop
 * asked and in what order, not how long it took.
 */
class NetworkWaitTest {

    private val tick = 1L

    @Test
    fun `a device already on Wi-Fi does not wait at all`() = runBlocking {
        var asked = 0
        val end = NetworkReach.awaitConnected(
            timeoutMillis = 10_000L,
            isConnected = { asked++; true },
            tickMillis = tick,
        )
        assertEquals(NetworkWait.Connected, end)
        assertEquals(1, asked)
    }

    @Test
    fun `a window with no network in it runs out, and is not a refusal to wait`() = runBlocking {
        val end = NetworkReach.awaitConnected(
            timeoutMillis = 5L,
            isConnected = { false },
            tickMillis = tick,
        )
        assertEquals(NetworkWait.TimedOut, end)
    }

    @Test
    fun `a network that arrives inside the window ends the wait as connected`() = runBlocking {
        var passes = 0
        val end = NetworkReach.awaitConnected(
            timeoutMillis = 50L,
            isConnected = { passes++ >= 2 },
            tickMillis = tick,
        )
        assertEquals(NetworkWait.Connected, end)
    }

    @Test
    fun `a caller that changes its mind ends the wait, and early`() = runBlocking {
        // This is the setting that can be turned off while the gate waits: the wait returns from its
        // own loop rather than after the window, which is the whole reason it asks.
        var passes = 0
        var remainingWhenItStopped = -1L
        val end = NetworkReach.awaitConnected(
            timeoutMillis = 300_000L,
            isConnected = { false },
            stillWanted = { passes++ < 3 },
            tickMillis = tick,
            onWaiting = { remainingWhenItStopped = it },
        )
        assertEquals(NetworkWait.Abandoned, end)
        // Three passes to say yes, the fourth to say no - and nowhere near the five minute window.
        assertTrue("the wait reported $remainingWhenItStopped ms left", remainingWhenItStopped > 0)
    }

    @Test
    fun `a network that is already there is reported even by a caller that gave up`() = runBlocking {
        // The order the two endings are decided in, asserted rather than assumed: the network is a fact
        // about the device and the wait is a decision about this caller, so a fact already true is not
        // turned into an abandonment.
        val end = NetworkReach.awaitConnected(
            timeoutMillis = 10_000L,
            isConnected = { true },
            stillWanted = { false },
            tickMillis = tick,
        )
        assertEquals(NetworkWait.Connected, end)
    }

    @Test
    fun `an abandoned wait is never a timeout`() = runBlocking {
        // The two call for different things afterwards: one is a device with no Wi-Fi, the other is a
        // setting that no longer wants any of this, and the gate answers them differently.
        val end = NetworkReach.awaitConnected(
            timeoutMillis = 0L,
            isConnected = { false },
            stillWanted = { false },
            tickMillis = tick,
        )
        assertEquals(NetworkWait.Abandoned, end)
    }
}
