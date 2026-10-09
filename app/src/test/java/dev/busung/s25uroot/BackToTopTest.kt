package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The back-to-top button: where it is drawn, and the one screen it must give way to.
 *
 * Both rules fail silently if they break. A button drawn by hand on a screen that needs it gated looks
 * right in every screenshot and only misbehaves under a thumb, and the run screen's stand-down is the
 * difference between a control that can be pressed and one that cannot - which is the failure this app has
 * already had once, in a run that could not be stopped.
 */
class BackToTopTest {

    @Test
    fun `the button is drawn in one place, so no screen can place its own`() {
        val owners = sourceFiles()
            .filter { it.readText().contains("SmallFloatingActionButton") }
            .map(File::getName)

        assertEquals(listOf("ScrollToTop.kt"), owners)
    }

    @Test
    fun `the run screen no longer measures where its controls begin`() {
        // It used to, and the rule that had to hold was that the measurement came before the buttons. The
        // whole mechanism went when the controls moved into a bar over the page: nothing on the page is a
        // control now, so there is nothing for the button to yield to - and no measurement that can be
        // taken in the wrong place.
        val text = source("InstallActivity.kt")

        assertFalse(text.contains("controlsTop"))
        assertFalse(text.contains("positionInWindow"))
    }

    @Test
    fun `the shared column has one rule for the button, not a rule per screen`() {
        val shared = source("ScrollToTop.kt")

        assertFalse(
            "the column can still be told to stand the button down, which is the mechanism the bar replaced",
            shared.contains("standDown"),
        )
    }

    @Test
    fun `every tab page goes through the wrapper, and the one that does not draws the button itself`() {
        val main = source("MainActivity.kt")

        // Overview, run detail, Logs, Settings and CVeyra Access use the wrapper. History remains
        // the one list that draws the shared button itself because selection controls occupy that corner.
        assertEquals(5, main.split("PageList(").size - 1)
        // Four pages own their state through the helper; Settings and CVeyra Access keep list state
        // explicitly because they also use it for navigation/jump behavior.
        assertEquals(4, main.split("rememberPageListState()").size - 1)
        assertEquals(1, main.split("BackToTopFab(").size - 1)
    }

    private fun source(name: String): String {
        val file = sourceFiles().firstOrNull { it.name == name }
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file.readText()
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)
}
