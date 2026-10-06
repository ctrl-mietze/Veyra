package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The run screen's bar: when it exists, and the two things that have to hold about where its controls are.
 *
 * The rule fails quietly in one direction only - a phase that offers something but is not listed draws no bar,
 * and the screen then has controls nowhere: not on the page any more, and not in the bar either. It is the
 * kind of gap that shows up as a run that cannot be stopped, which is the one thing a hung run needs.
 *
 * The other two are properties of the source and both were bugs once: the controls sat at the end of the
 * scrolling page, so the log ended against them, and the button over the page had to be told where they began
 * - a measurement that had to be taken in the right place to work at all.
 *
 * The third source property is the bar's own container, which is gone: it held the controls inside a rounded
 * band, and every one of them already has a shape.
 */
class RunActionBarTest {

    @Test
    fun `every phase that offers a control says so`() {
        // Busy is every phase the app is working through, including the settle wait, and each has a stop.
        InstallPhase.entries.forEach { phase ->
            val busy = phase in setOf(
                InstallPhase.Checking,
                InstallPhase.Settling,
                InstallPhase.Downloading,
                InstallPhase.Exploiting,
                InstallPhase.LoadingKernelSu,
            )
            val expected = busy || phase in setOf(
                InstallPhase.Failed,
                InstallPhase.Stopped,
                InstallPhase.Installed,
                InstallPhase.RootOnly,
            )

            assertEquals("$phase", expected, runControlsOffered(phase, busy))
        }
    }

    @Test
    fun `the screen open before a run has started has no bar`() {
        // The one phase that is neither working nor finished: nothing to stop and nothing to do afterwards,
        // and an empty bar over the page would be the bar pretending otherwise.
        assertFalse(runControlsOffered(InstallPhase.Ready, busy = false))
    }

    @Test
    fun `busy is read from the state rather than recomputed`() {
        // The screen asks the state what it is doing and hands the answer on: a second list of which phases
        // are busy would be a second place for the two to disagree.
        assertTrue(source("InstallActivity.kt").contains("runControlsOffered(installState.phase, installState.busy)"))
    }

    @Test
    fun `the run's controls are in the bar and not at the end of the page`() {
        val text = source("InstallActivity.kt")
        val bar = text.indexOf("RunActionBar")

        assertTrue("nothing draws the run's bar", bar > 0)
        listOf(
            "R.string.action_stop_run",
            "R.string.action_retry",
            "R.string.action_run_now",
            "R.string.action_done",
        ).forEach { control ->
            val at = text.indexOf(control)
            assertTrue("$control is not on this screen any more", at > 0)
            assertTrue("$control is still composed on the page rather than in the bar", at > bar)
        }
    }

    @Test
    fun `the bar draws no progress line over its controls`() {
        val text = source("InstallActivity.kt")

        assertTrue("nothing draws the run's bar", text.contains("RunActionBar"))
        // A hairline across the bar's top edge sat directly over the outermost button, so it read as part of
        // Stop rather than as the run's position - and it was removed for that, not for being wrong. So the
        // property is "the bar is handed nothing at all": with no argument to pass, there is no fraction for a
        // strip to read, and the trailing-lambda call this asserts is also what the statement looks like.
        assertFalse(
            "the bar over the log takes an argument again; a fraction is what put a rule back over Stop",
            text.contains("RunActionBar("),
        )
        assertFalse(
            "RunActionBar has a progress strip in it again",
            source("RunActionBar.kt").contains("RunProgressHairline"),
        )
    }

    @Test
    fun `how far the run has come is still drawn, on the card and in the notification`() {
        // The strip was removed rather than the feature. One definition of where a run is, in
        // `installProgress`, drawn by the status card above the log and by the run's own notification.
        assertTrue(
            "nothing draws the run's progress any more",
            source("InstallActivity.kt").contains("installProgress("),
        )
        assertTrue(
            "the run's notification no longer says how far it has come",
            source("InstallViewModel.kt").contains("installProgress("),
        )
    }

    @Test
    fun `the page is given room for the bar it floats under`() {
        val shared = source("ScrollToTop.kt")

        // Measured in the one place that draws both, so a screen that floats a bar cannot forget to end clear
        // of it - and cannot hard-code a height that stops matching as the bar's own row changes.
        assertTrue(shared.contains("bar: @Composable BoxScope.() -> Unit = {}"))
        assertTrue(shared.contains("Spacer(Modifier.height(barHeight + BACK_TO_TOP_CLEARANCE))"))
        assertTrue(shared.contains("pageBottomInset(barHeight)"))
    }

    @Test
    fun `the bar draws no container around its controls`() {
        val body = source("RunActionBar.kt")

        // The bar was a tinted pill with a shadow, carrying the same shape as the tab bar's. The controls are
        // rounded shapes of their own, so that pill enclosed them a few dp out in a third colour - a band
        // between the button and the page, which is what a border is. Asserted on the bar's own source rather
        // than on a screenshot because the failure is invisible in a diff: one file, one line, and every
        // button on the run screen grows an outline around it.
        assertFalse("the bar has a pill around its controls again", body.contains("Surface("))
        assertFalse("the bar draws a rounded container again", body.contains("RoundedCornerShape"))
        assertFalse(
            "the bar has a shadow again; a shadow draws the same band even with no fill behind it",
            body.contains("shadowElevation"),
        )
        // The fade has to stay, and it is not a container: it has no edge and nothing hugs it. With the pill
        // gone it is the only thing telling the bar's buttons from the log's text behind them.
        assertTrue("nothing separates the bar from the page any more", body.contains("Brush.verticalGradient"))
    }

    private fun source(name: String): String {
        val file = candidateRoots()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
            .firstOrNull()
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)
}
