package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipeBudgetTest {

    @Test
    fun `a refusal of the sizing call is recognised`() {
        val log = """
            [*] p0 pipe oracle prepared
            F_SETPIPE_SZ: Operation not permitted (pipe pages)
            [!] pipe setup failed
        """.trimIndent()
        assertEquals(
            "F_SETPIPE_SZ: Operation not permitted (pipe pages)",
            PipeBudget.evidenceIn(log),
        )
    }

    @Test
    fun `the limits being printed as information is not a diagnosis`() {
        // The reference implementation pre-checks these and prints the values on a healthy device.
        // A message that fired on those lines would send someone to restart a phone that is fine.
        val log = """
            pipe-max-size=1048576
            pipe-user-pages-soft=16384
            pipe-user-pages-hard=0
            [*] pipe budget read
        """.trimIndent()
        assertNull(PipeBudget.evidenceIn(log))
    }

    @Test
    fun `a refusal about something else is not this diagnosis`() {
        // The exploit fails for many reasons, and the pipe word has to be in the same line as the
        // refusal for the two to be about each other.
        val log = """
            [-] failed to open /dev/uinput: Permission denied
            [-] root umh missing CVE43499_ROOT_HELPER
        """.trimIndent()
        assertNull(PipeBudget.evidenceIn(log))
    }

    @Test
    fun `a limit named in one line and a refusal in another is not a pair`() {
        val log = """
            pipe-user-pages-soft=16384
            [*] p0 attempt 3 failed
        """.trimIndent()
        assertNull(PipeBudget.evidenceIn(log))
    }

    @Test
    fun `the soft limit named with a refusal is recognised`() {
        val log = "[!] pipe-user-pages-soft exceeded, fcntl denied"
        assertEquals(log, PipeBudget.evidenceIn(log))
    }

    @Test
    fun `an empty log says nothing`() {
        assertNull(PipeBudget.evidenceIn(""))
    }

    @Test
    fun `a recorded budget belongs to the boot that recorded it`() {
        // The budget is the boot's, and the boot token is the only thing that changes when a restart
        // refills it - so a record that is not this boot's is not this boot's spent budget.
        assertTrue(PipeBudget.isSpentFor("boot-a", "boot-a"))
        assertFalse(PipeBudget.isSpentFor("boot-a", "boot-b"))
    }

    @Test
    fun `nothing recorded, or nothing to compare with, is not a spent budget`() {
        // Failing closed here would refuse every run on a device whose boot id could not be read,
        // which is the opposite of what a diagnosis with no run behind it should do.
        assertFalse(PipeBudget.isSpentFor(null, "boot-a"))
        assertFalse(PipeBudget.isSpentFor("boot-a", null))
        assertFalse(PipeBudget.isSpentFor(null, null))
    }

    @Test
    fun `the evidence is the line as it stands, not a paraphrase of it`() {
        // The card shows what the payload said, including whatever prefix the run puts on its log, so
        // that the two accounts of the failure can be compared line for line.
        val line = ">> [!] F_SETPIPE_SZ failed, errno 1"
        assertEquals(line, PipeBudget.evidenceIn(line))
    }
}
