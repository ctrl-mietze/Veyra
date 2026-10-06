package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a tap on a notification about a run lands, and the two records that decide it.
 *
 * The bug this covers is not a crash: it is a tap that opens a screen about a *different* run, or offers to
 * start a second install while the notification was describing the first. That is why most of this is read
 * off the sources - the wrong destination compiles, reads correctly in a diff, and is only visible on a phone.
 */
class RunDeepLinkTest {

    private fun entry(
        id: String,
        result: InstallRunResult,
        startedAtMillis: Long = 1_700_000_000_000,
    ) = InstallHistoryEntry(
        id = id,
        startedAtMillis = startedAtMillis,
        completedAtMillis = null,
        result = result,
        log = "",
    )

    @Test
    fun `a record names the run it is about, and a blank name is no name`() {
        assertEquals("run-1", RunHolder.of("boot-a", "4242", "run-1")?.entryId)
        assertNull("a blank id is an id nobody can match", RunHolder.of("boot-a", "4242", "  ")?.entryId)
        assertNull("an absent id is the same as a blank one", RunHolder.of("boot-a", "4242")?.entryId)
        assertEquals(
            "the rest of the record is unchanged by the id",
            RunHolder(bootToken = "boot-a", pid = 4242),
            RunHolder.of(" boot-a ", " 4242 ", null),
        )
    }

    @Test
    fun `the run in flight keeps its entry, and the interrupted ones are closed`() {
        val entries = listOf(
            entry("live", InstallRunResult.Running, startedAtMillis = 3),
            entry("killed", InstallRunResult.Running, startedAtMillis = 2),
            entry("done", InstallRunResult.Succeeded, startedAtMillis = 1),
        )

        val closed = entries.closingInterruptedRuns(RunHolder("boot-a", 4242, "live"))

        assertEquals(
            "the live run was closed as an interrupted one",
            InstallRunResult.Running,
            closed.first { it.id == "live" }.result,
        )
        assertEquals(
            "a run whose process is gone is still closed",
            InstallRunResult.Failed,
            closed.first { it.id == "killed" }.result,
        )
        assertEquals(
            "a finished run is not touched",
            InstallRunResult.Succeeded,
            closed.first { it.id == "done" }.result,
        )
    }

    @Test
    fun `with no run in flight every unfinished entry is closed`() {
        val entries = listOf(entry("a", InstallRunResult.Running), entry("b", InstallRunResult.Running))

        // The record names no run, and the entry-less record is the case that must not accidentally spare
        // anything: a holder with a null entry id is "somebody is running", not "this entry is".
        assertEquals(
            listOf(InstallRunResult.Failed, InstallRunResult.Failed),
            entries.closingInterruptedRuns(RunHolder("boot-a", 4242, null)).map { it.result },
        )
        assertEquals(
            listOf(InstallRunResult.Failed, InstallRunResult.Failed),
            entries.closingInterruptedRuns(null).map { it.result },
        )
    }

    @Test
    fun `a notification names its run and sends every tap through the run screen`() {
        val notification = source("src/main/java/dev/busung/s25uroot/RunNotification.kt")

        assertTrue(
            "the notification no longer opens the run screen",
            notification.contains("liveRunPendingIntent(context, runId)"),
        )
        // The outcome was the one tap that skipped the screen, and skipping it is what cost the failure card:
        // the screen for a run this process has shows the stage, the reason and the three answers, and the
        // record it went to instead has only the log and the verdict. The screen forwards when it is not the
        // screen for that run, so nothing is lost by sending every tap the same way - and a destination
        // chosen here from the actions flag is a decision about this process made by a file that cannot see it.
        assertTrue(
            "the outcome no longer lands on the run screen, so a failed run opens a log instead of its answers",
            !notification.contains("runRecordPendingIntent(context, runId)"),
        )
        assertFalse(
            "a destination with no run in it is back: that is the tap that opens the wrong screen",
            notification.contains("Intent(context, InstallActivity::class.java)"),
        )
    }

    @Test
    fun `the boot gate names its run and lets the run screen decide what it can do with it`() {
        val bootGate = source("src/main/java/dev/busung/s25uroot/AutoRootService.kt")

        assertTrue(
            "the boot gate's notification no longer sends its run to the run screen",
            bootGate.contains("liveRunPendingIntent(this, it)"),
        )
        assertTrue(
            "a notification posted before the run exists has no run to name, and goes Home as it did",
            bootGate.contains("runRecordPendingIntent(this, null)"),
        )
    }

    @Test
    fun `every call into the notification names the run it is about`() {
        val viewModel = source("src/main/java/dev/busung/s25uroot/InstallViewModel.kt")
        val receiver = source("src/main/java/dev/busung/s25uroot/RunActionReceiver.kt")
        val bootGate = source("src/main/java/dev/busung/s25uroot/AutoRootService.kt")

        assertTrue(
            "the run's own notification is posted without naming the run",
            viewModel.contains("runId = activeRunId"),
        )
        assertTrue(
            "the two actions' update does not name the run",
            receiver.contains("runId = activeRunId(context)"),
        )
        assertTrue(
            "the boot gate's notification does not name the run it is about",
            bootGate.contains("liveRunPendingIntent(this, it)"),
        )
        assertTrue(
            "the run keeps its entry id for the notification posted after it ends",
            viewModel.contains("var activeRunId: String?") &&
                viewModel.contains("activeRunId = entry.id"),
        )
    }

    @Test
    fun `the run screen follows a run it does not have, and hands over one that is not live`() {
        val screen = source("src/main/java/dev/busung/s25uroot/InstallActivity.kt")

        assertTrue(
            "the run screen no longer reads the run a notification named",
            screen.contains("intent.getStringExtra(EXTRA_RUN_ID)"),
        )
        assertTrue(
            "a notification for a run another process is writing no longer reaches the follow loop, so the " +
                "screen would fall back to one about a run of its own",
            screen.contains("handedOverRun") && screen.contains("followedRun("),
        )
        assertTrue(
            "a run that has ended or is gone is handed to its record, which is where a verdict lives",
            screen.contains("runRecordIntent(this@InstallActivity, wanted)"),
        )
    }

    @Test
    fun `the record a notification opens is live while the run is`() {
        val activity = source("src/main/java/dev/busung/s25uroot/MainActivity.kt")

        assertTrue(
            "the record is opened on arrival from the intent",
            activity.contains("openedRunEntry = openedRunId"),
        )
        assertTrue(
            "the record is no longer re-read, so a boot run's log would stand still on the screen",
            activity.contains("onReloadHistory = installViewModel::reloadHistory"),
        )
        assertTrue(
            "the page no longer opens the entry the notification named",
            activity.contains("val wanted = openEntryId ?: return@LaunchedEffect"),
        )
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
