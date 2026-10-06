package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The run screen showing a run that is happening in another process.
 *
 * The mistake this covers is quiet in both directions. Following an entry nothing is writing would draw a live
 * bar, a live log and a Stop for a run that is not there - on a screen that has no other way of saying so. And
 * refusing to follow one that *is* being written sends the reader back to a log, which is the thing this was
 * built to stop doing.
 */
class FollowedRunTest {

    private fun entry(
        id: String = "run-1",
        result: InstallRunResult = InstallRunResult.Running,
        phase: InstallPhase? = InstallPhase.Exploiting,
        log: String = "[*] Loading KernelSU\n[*] Running kernel exploit",
    ) = InstallHistoryEntry(
        id = id,
        startedAtMillis = 1_700_000_000_000,
        completedAtMillis = null,
        result = result,
        log = log,
        phase = phase,
    )

    private fun holder(entryId: String? = "run-1", pid: Int = 4242) =
        RunHolder(bootToken = "boot-a", pid = pid, entryId = entryId)

    @Test
    fun `a run the record names is followed, and draws the bar it published`() {
        val followed = followedRun(entry(), holder())

        assertEquals(InstallPhase.Exploiting, followed?.state()?.phase)
        assertTrue("a run in flight is a run the screen must be able to stop", followed!!.state().busy)
        assertEquals(
            "the bar is the same one the run screen draws for its own run",
            installProgress(InstallPhase.Exploiting, failureStage = null),
            installProgress(followed.state().phase, followed.state().failure?.stage),
        )
    }

    @Test
    fun `the status line is the run's own last word`() {
        val followed = followedRun(
            entry(log = "[*] Downloading\n[*] Waiting for the device to settle: 0:44\n"),
            holder(),
        )

        assertEquals("[*] Waiting for the device to settle: 0:44", followed?.state()?.message)
    }

    @Test
    fun `a record that names no run is not followed, whatever it says about itself`() {
        assertNull(
            "an entry nobody claims is a run that died with its process",
            followedRun(entry(), holder(entryId = null)),
        )
        assertNull(
            "another run's entry is not this run",
            followedRun(entry(id = "run-2"), holder(entryId = "run-1")),
        )
        assertNull("no holder is no run", followedRun(entry(), null))
        assertNull("no entry is nothing to draw", followedRun(null, holder()))
    }

    @Test
    fun `a run that has ended is not followed, even by a record that still names it`() {
        assertNull(
            "a finished run belongs to its record, which has a verdict and a cause this screen does not",
            followedRun(entry(result = InstallRunResult.Failed), holder()),
        )
    }

    @Test
    fun `a record with no phase is still a run in flight, and the first step is what it reads as`() {
        val followed = followedRun(entry(phase = null, log = ""), holder())

        assertEquals(InstallPhase.Checking, followed?.state()?.phase)
        assertTrue(
            "a stop that cannot be pressed on a run in flight is the one wrong answer here",
            followed!!.state().busy,
        )
    }

    @Test
    fun `the phase a run screen follows is written as the run moves and cleared when it ends`() {
        val viewModel = source("InstallViewModel.kt")

        assertTrue(
            "the running phase is no longer published, so the bar has nothing to draw from",
            viewModel.contains("updateHistory { entry -> entry.copy(phase = phase) }"),
        )
        assertTrue(
            "a finished record keeps a phase, and a screen could draw a live bar for a run that is over",
            viewModel.contains("phase = null,"),
        )
    }

    @Test
    fun `the entry carries the phase through the store`() {
        val store = source("InstallHistory.kt")

        assertTrue("the phase is not written", store.contains("put(\"phase\", entry.phase?.name"))
        assertTrue(
            "the phase is not read back, so following a run would lose the bar on the next launch",
            store.contains("value.optionalString(\"phase\")"),
        )
    }

    private fun source(name: String): String {
        val file = listOf(File("src/main/java/dev/busung/s25uroot/$name"))
            .plus(File("app/src/main/java/dev/busung/s25uroot/$name"))
            .firstOrNull(File::isFile)
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }
}
