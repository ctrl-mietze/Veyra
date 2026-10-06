package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the History page does with a run something asked it to open.
 *
 * The failure this guards against is silent in both directions, which is why it is worth a test rather than a
 * comment. A tap on a run's notification names a run, and this page opened it only if the copy of the history
 * it happened to be holding already contained that id - which it may not, because a boot run writes its entry
 * from its own process and the app can have been open before that run began. The tap then did nothing, and
 * what stayed on screen was whatever run was there before: a live install reading as the previous failure. The
 * other half is the follow: a record whose stored verdict says failed can still be the run being written right
 * now, because a launch closes every unfinished entry it thinks was interrupted.
 */
class HistoryOpenLookupTest {

    @Test
    fun `a run this page has not read yet is looked for again, not called missing`() {
        val page = historyPage()

        assertTrue(
            "the page must re-read the history when the run it was asked for is not in the copy it holds",
            page.contains("onReloadHistory()"),
        )
        assertTrue("the look is bounded", page.contains("RUN_LOOKUP_ATTEMPTS"))
    }

    @Test
    fun `a run that never turns up is said out loud instead of leaving the last run on screen`() {
        val page = historyPage()

        assertTrue(page.contains("R.string.history_run_not_here"))
        assertTrue(
            "the request is dropped either way, so returning to this page cannot reopen it",
            page.split("onEntryOpened()").size - 1 >= 2,
        )
    }

    @Test
    fun `the follow trusts the shared record as well as the stored verdict`() {
        val page = historyPage()

        assertTrue(
            "a record another process is still writing is a run in flight, whatever it reads as",
            page.contains("RunInFlight.holder(context)?.entryId"),
        )
    }

    @Test
    fun `the re-read is not left to the live tick alone`() {
        val main = source("MainActivity.kt").readText()

        // Two callers: the tick that follows a run in flight, and the look for a run the page has not read.
        // One caller was the bug.
        assertEquals(2, main.split("onReloadHistory()").size - 1)
    }

    @Test
    fun `the message it says has words written for it`() {
        val strings = stringsXml().readText()

        assertTrue(strings.contains("name=\"history_run_not_here\""))
    }

    private fun stringsXml(): File {
        val file = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).firstOrNull(File::isFile)
        requireNotNull(file) { "strings.xml was not found; the scan is looking at the wrong directory" }
        return file
    }

    /** The page's own body, so an assertion cannot be satisfied by a match somewhere else in the file. */
    private fun historyPage(): String {
        val main = source("MainActivity.kt").readText()
        val start = main.indexOf("private fun HistoryPage(")
        require(start >= 0) { "HistoryPage was not found; the scan is looking at the wrong file" }
        val end = main.indexOf("\nprivate fun HistoryList(", start)
        require(end > start) { "the page's end was not found; the scan assumes the list follows it" }
        return main.substring(start, end)
    }

    private fun source(name: String): File {
        val file = sourceFiles().firstOrNull { it.name == name }
        requireNotNull(file) { "$name was not found; the scan is looking at the wrong directory" }
        return file
    }

    private fun sourceFiles(): List<File> = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)
}
