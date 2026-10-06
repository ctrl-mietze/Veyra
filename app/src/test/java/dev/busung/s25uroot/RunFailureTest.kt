package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunFailureTest {

    @Test
    fun `a reason that carried a log is reduced to its first line`() {
        val reason = "Payload execution failed: 137 ([runner-live] following\n[app] root helper=...\nmore"

        assertEquals("Payload execution failed: 137 ([runner-live] following", failureSummary(reason))
    }

    @Test
    fun `a leading blank line is skipped rather than becoming the reason`() {
        assertEquals("The payload exited", failureSummary("\n\n  The payload exited  \nignored"))
    }

    @Test
    fun `a long single line is clipped`() {
        val summary = failureSummary("x".repeat(500))

        assertEquals(240, summary.length)
        assertTrue(summary.endsWith("\u2026"))
    }

    @Test
    fun `a short reason is left exactly as it was`() {
        assertEquals("The exploit log did not advance", failureSummary("The exploit log did not advance"))
    }

    @Test
    fun `a failure built through the factory never carries a log`() {
        val failure = RunFailure.of(
            stage = RunStage.Exploit,
            reason = "Payload execution failed: 137\n" + "payload line\n".repeat(200),
            evidence = listOf("last line"),
        )

        assertEquals("Payload execution failed: 137", failure.reason)
        assertEquals(listOf("last line"), failure.evidence)
    }

    @Test
    fun `a killed payload is reported as a signal, not as a number`() {
        // 137 is 128 + 9, which is how a SIGKILL reaches the app.
        assertEquals("signal 9 (SIGKILL)", exitCodeSummary(137))
        assertEquals("signal 15 (SIGTERM)", exitCodeSummary(143))
        assertEquals("signal 11 (SIGSEGV)", exitCodeSummary(139))
        assertEquals("signal 31", exitCodeSummary(159))
    }

    @Test
    fun `an ordinary exit code is not dressed up as a signal`() {
        assertNull(exitCodeSummary(1))
        assertNull(exitCodeSummary(0))
        assertNull(exitCodeSummary(255))
    }

    @Test
    fun `the payload exit detail stays short enough for one line`() {
        assertEquals(" \u2014 signal 9 (SIGKILL)", payloadExitDetail(137))
        assertEquals("", payloadExitDetail(1))
    }

    @Test
    fun `evidence keeps only the tail and clips it`() {
        val log = (1..50).joinToString("\n") { "line $it" }

        assertEquals(listOf("line 47", "line 48", "line 49", "line 50"), failureEvidence(log))
    }

    @Test
    fun `evidence drops blank lines and clips a wide line`() {
        val evidence = failureEvidence("first\n\n   \n" + "y".repeat(400))

        assertEquals(listOf("first", "y".repeat(159) + "\u2026"), evidence)
    }
}
