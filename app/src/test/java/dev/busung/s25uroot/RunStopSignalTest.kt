package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which run a request written by a notification's Stop belongs to. */
class RunStopSignalTest {

    @Test
    fun `a stop is written for the process the run is in, not for the one that pressed it`() {
        // The boot gate's run is in `:autoroot_gate` while the broadcast lands in the app's own process, so
        // naming this process would leave the request waiting on a run that is somewhere else - a Stop button
        // that appears to work and stops nothing.
        assertEquals(777, RunActionReceiver.stopTarget(RunHolder("boot-a", 777, "run-1"), ownPid = 111))
        assertEquals(
            "with no run on the device there is nothing to name but this process",
            111,
            RunActionReceiver.stopTarget(null, ownPid = 111),
        )
    }

    @Test
    fun `a request names one run, in one boot`() {
        val request = RunStopRequest("boot-a", 4242)

        assertTrue(request.isFor("boot-a", 4242))
        // The pid alone is only unique within a boot: the same number after a restart is a stranger.
        assertFalse(request.isFor("boot-b", 4242))
        // And the boot alone would name every process this app runs, including the boot gate's.
        assertFalse(request.isFor("boot-a", 1111))
    }

    @Test
    fun `a record that is not a request is not read as one`() {
        assertNull(RunStopRequest.of(null, "4242"))
        assertNull(RunStopRequest.of("   ", "4242"))
        assertNull(RunStopRequest.of("boot-a", null))
        assertNull(RunStopRequest.of("boot-a", "not a pid"))
        assertNull(RunStopRequest.of("boot-a", "0"))
        assertNull(RunStopRequest.of("boot-a", "-3"))
    }

    @Test
    fun `a request survives being written down and read back`() {
        val written = RunStopRequest("8f2c-1", 4242)
        val read = RunStopRequest.of(written.bootToken, written.pid.toString())

        assertEquals(written, read)
    }
}
