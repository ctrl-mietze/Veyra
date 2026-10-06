package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one vocabulary four surfaces now share. */
class RunVerdictTest {

    @Test
    fun `a run in flight is one verdict whatever stage it is in`() {
        // Busy is the state the app is in for five of the nine phases, and the surface that shows it says
        // "running": a stage-by-stage colour would be movement for its own sake.
        InstallPhase.entries.forEach { phase ->
            val busy = phase in setOf(
                InstallPhase.Checking,
                InstallPhase.Settling,
                InstallPhase.Downloading,
                InstallPhase.Exploiting,
                InstallPhase.LoadingKernelSu,
            )
            if (busy) assertEquals("$phase", RunVerdict.Running, runVerdict(phase, busy))
        }
    }

    @Test
    fun `the four ways a run can end get four different verdicts`() {
        assertEquals(RunVerdict.Succeeded, runVerdict(InstallPhase.Installed, busy = false))
        assertEquals(RunVerdict.RootOnly, runVerdict(InstallPhase.RootOnly, busy = false))
        assertEquals(RunVerdict.Failed, runVerdict(InstallPhase.Failed, busy = false))
        assertEquals(RunVerdict.Stopped, runVerdict(InstallPhase.Stopped, busy = false))
    }

    @Test
    fun `root without KernelSU is neither of the two outcomes it sits between`() {
        // The reason the vocabulary has this state at all: it is a run that worked and a phone that is not
        // running KernelSU, and both neighbours would be a lie about one half of that.
        val rootOnly = runVerdict(InstallPhase.RootOnly, busy = false)
        assertTrue(rootOnly != RunVerdict.Succeeded)
        assertTrue(rootOnly != RunVerdict.Failed)
    }

    @Test
    fun `a screen with nothing behind it is not a failure`() {
        assertEquals(RunVerdict.Idle, runVerdict(InstallPhase.Ready, busy = false))
    }

    @Test
    fun `the first frame the app draws is not a run in flight`() {
        // The state the ViewModel is constructed with, which is what the install button draws before the probe
        // has answered. It opened on `Checking`, a step of a run - so for as long as the probe took, the button
        // was a run: a spinner, no title, a tap the card ignored, and a Stop bar on the run screen.
        val first = InstallUiState()

        assertEquals(InstallPhase.Probing, first.phase)
        assertFalse("the first frame is busy, so the card treats it as a run", first.busy)
        assertEquals(RunVerdict.Idle, runVerdict(first.phase, first.busy))
        assertFalse(
            "the first frame offers a stop for a run that has not started",
            runControlsOffered(first.phase, first.busy),
        )
        assertEquals("nothing has been attempted, so no progress is claimed", 0f, installProgress(first.phase, null))
    }

    @Test
    fun `the state the app opens on carries its own words`() {
        // `message` is what the button writes on itself, and nothing filled it in until the probe published - so
        // the empty title was not the phase's fault alone. The words live with the state that says them, since a
        // caller constructing a state cannot be trusted to remember to pass a title.
        val viewModel = source("InstallViewModel.kt").replace(Regex("\\s+"), " ")

        assertTrue(
            "the state the app opens on has no title to draw",
            viewModel.contains("InstallUiState(message = app.getString(R.string.status_checking_device))"),
        )
        assertTrue(
            "the run's own first step publishes an untitled card again",
            viewModel.contains(
                "InstallPhase.Checking, message = app.getString(R.string.status_checking_device)",
            ),
        )
    }

    @Test
    fun `every stored result maps to a verdict of its own`() {
        // Exhaustive by construction - this is what makes the history rows and the live card agree - and the
        // assertion that matters is that the mapping is one to one rather than collapsing two into one.
        val verdicts = InstallRunResult.entries.map { runVerdict(it) }
        assertEquals(InstallRunResult.entries.size, verdicts.toSet().size)
        assertEquals(RunVerdict.Running, runVerdict(InstallRunResult.Running))
        assertEquals(RunVerdict.Succeeded, runVerdict(InstallRunResult.Succeeded))
        assertEquals(RunVerdict.RootOnly, runVerdict(InstallRunResult.RootOnly))
        assertEquals(RunVerdict.Failed, runVerdict(InstallRunResult.Failed))
        assertEquals(RunVerdict.Stopped, runVerdict(InstallRunResult.Stopped))
    }

    @Test
    fun `no verdict is painted in the role kept for warnings`() {
        // The verdict is the colour of the install button itself, so this is what a screenshot of a run is
        // mostly made of. Checked as source because `verdictColors` reads the theme and cannot be called from
        // a plain unit test, and because the failure is invisible until somebody looks at a phone mid-run: a
        // run painted in the caution role reads as something having gone wrong when nothing has.
        val colors = source("RunVerdict.kt").substringAfter("internal fun verdictColors")

        assertFalse(
            "a verdict is wearing the caution role again, which a run in flight is not",
            colors.contains("tertiary"),
        )
        // The branch itself, with the line breaks normalised so this pins the role rather than a formatting.
        val running = colors.substringAfter("RunVerdict.Running ->")
            .substringBefore("RunVerdict.Succeeded ->")
            .replace(Regex("\\s+"), " ")
        assertTrue(
            "the running verdict is no longer on the accent",
            running.contains("VerdictColors(scheme.primaryContainer"),
        )
    }

    @Test
    fun `a verdict and its label are one to one`() {
        // The label is what the notification's title and the history chip are made of, and two verdicts
        // wearing one word is the disagreement this file exists to end.
        val labels = RunVerdict.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    private fun source(name: String): String {
        val file = listOf(File("src/main/java"), File("app/src/main/java"))
            .filter(File::isDirectory)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
            .firstOrNull()
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }
}
