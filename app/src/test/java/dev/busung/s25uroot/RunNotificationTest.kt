package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The run's notification: that its two buttons reach something, and that it does not outlive the run.
 *
 * Every one of these fails quietly in the same way - a tap on a button that does nothing, or a notification
 * for an install that is not happening - which is why they are assertions about the wiring rather than about
 * the screen: nothing renders an unreachable action as broken.
 */
class RunNotificationTest {

    @Test
    fun `the receiver behind both actions is declared`() {
        val manifest = source("AndroidManifest.xml")
        val declared = manifest.substringAfter("<receiver\n            android:name=\".RunActionReceiver\"", "")

        assertTrue("the run notification's actions have no receiver", declared.isNotEmpty())
        assertTrue(
            "the receiver is reachable from another app",
            declared.substringBefore(">").contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `the actions the notification sends are the ones the receiver answers`() {
        val receiver = source("RunActionReceiver.kt")
        val notification = source("RunNotification.kt")
        val manifest = source("AndroidManifest.xml")

        assertTrue("Stop is not handled", receiver.contains("ACTION_STOP -> {"))
        assertTrue("Copy log is not handled", receiver.contains("ACTION_COPY_LOG -> {"))
        assertTrue(
            "the Stop button sends an action nothing answers",
            notification.contains("RunActionReceiver.ACTION_STOP"),
        )
        assertTrue(
            "the Copy log button sends an action nothing answers",
            notification.contains("RunActionReceiver.ACTION_COPY_LOG"),
        )
        // The other half of the wiring: a receiver class the manifest does not name is never constructed.
        assertTrue(manifest.contains(".RunActionReceiver"))
    }

    @Test
    fun `a stop is polled where a run actually waits`() {
        val viewModel = source("InstallViewModel.kt")

        // Once where it is written and once per long wait, of which the run has two: the boot settle and the
        // exploit's own poll loop. A check that only existed in one of them would leave the other unstoppable
        // from the notification, which is exactly the phase it is for.
        assertEquals(
            "the stop is not polled in the run's two long waits",
            3,
            Regex("stopIfAskedFromOutside\\(\\)").findAll(viewModel).count(),
        )
    }

    @Test
    fun `the notification is posted with the phase and taken down with the run`() {
        val viewModel = source("InstallViewModel.kt")

        // Named arguments rather than positional since the run's own id joined them, which is the thing
        // that decides which run a later tap on it opens.
        assertTrue(viewModel.contains("RunNotification.post("))
        assertTrue(viewModel.contains("progress = installProgress(phase, failureStage = null)"))
        assertTrue(viewModel.contains("runId = activeRunId"))
        assertTrue(viewModel.contains("RunNotification.clear(app)"))
        // The gate has a notification of its own: doubling it is how the shade stops being read.
        assertTrue(viewModel.contains("if (!runIsUnattended)"))
    }

    @Test
    fun `a notification with no run behind it is swept at launch`() {
        // A run killed with its process cannot take its own down, and one left behind would keep someone
        // waiting on an install that is not happening.
        assertTrue(
            source("RootMyGalaxyApplication.kt").contains("RunNotification.clearStale(this)"),
        )
    }

    @Test
    fun `a stop left over from another boot is dropped rather than kept`() {
        // The failure this guards against is the opposite of a missed stop: a request that waits until the
        // next run in this process and stops that one instead.
        assertTrue(source("InstallViewModel.kt").contains("RunStopSignal.clear(app)"))
    }

    private fun source(name: String): String {
        val file = candidateRoots()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
            .firstOrNull()
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main"),
        File("app/src/main"),
    ).filter(File::isDirectory)
}
