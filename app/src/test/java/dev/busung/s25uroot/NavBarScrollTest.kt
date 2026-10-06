package ctrl.mietze.veyraroot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bar's answer to a page being scrolled.
 *
 * Two halves, both of which fail quietly. The rule itself is direction: get the sign wrong and the bar leaves
 * when the user reaches for the top and stays put when they dive - which looks like a bar that ignores them,
 * not like a bug. And the wiring is worse, because a connection attached to the wrong node still compiles and
 * still hides the bar: it just does it for the wrong scroll, on a screen that should not have moved it.
 */
class NavBarScrollTest {

    @Test
    fun `a scroll delta is read as the page's direction, not the finger's`() {
        // Compose reports the distance the content should move: positive is content coming back down, which
        // is the page heading for its top. Reading it as the finger's direction inverts both.
        assertEquals(PageScroll.TowardTop, pageScrollOf(1f))
        assertEquals(PageScroll.TowardEnd, pageScrollOf(-1f))
        assertEquals(PageScroll.Still, pageScrollOf(0f))
    }

    @Test
    fun `going deeper hides the bar and any way back shows it`() {
        assertTrue(navBarHiddenAfter(hidden = false, direction = PageScroll.TowardEnd))
        assertFalse(navBarHiddenAfter(hidden = true, direction = PageScroll.TowardTop))
    }

    @Test
    fun `a scroll that settles leaves the bar where it is`() {
        // The tail of every fling: a bar that responded to it would flicker in and out as the scroll decays.
        assertTrue(navBarHiddenAfter(hidden = true, direction = PageScroll.Still))
        assertFalse(navBarHiddenAfter(hidden = false, direction = PageScroll.Still))
    }

    @Test
    fun `the connection reports a change once, not once per scroll event`() {
        val reported = mutableListOf<Boolean>()
        val connection = navBarScrollConnection { reported += it }

        // Four events of the same direction: one decision, because the bar has two states and the scroll of a
        // fling is dozens of events.
        repeat(4) { connection.onPostScroll(scrollOf(-10f), Offset.Zero, NestedScrollSource.Drag) }
        connection.onPostScroll(scrollOf(10f), Offset.Zero, NestedScrollSource.Drag)

        assertEquals(listOf(true, false), reported)
    }

    /** A scroll the page consumed: negative is the page moving toward its end. */
    private fun scrollOf(dy: Float): Offset = Offset(x = 0f, y = dy)

    @Test
    fun `the bar watches the pages and nothing else`() {
        val main = source("MainActivity.kt")

        assertTrue(
            "the shell no longer listens to a page's scroll, so nothing hides the bar",
            main.contains("navBarScrollConnection"),
        )
        // On the pages rather than on the screen: the payload sheets and the dialogs are siblings of the
        // pages, and scrolling a list inside one of them is not a page moving under the bar.
        val content = main.substringAfter("AnimatedContent(").substringBefore("{ page ->")
        assertTrue(
            "the scroll is watched above the sheets and dialogs too, which hides the bar while one is open",
            content.contains("nestedScroll("),
        )
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
