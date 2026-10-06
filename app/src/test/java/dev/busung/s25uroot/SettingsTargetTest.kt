package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules behind "Open setting".
 *
 * A link that lands on the settings page and leaves the reader to find the row is the thing this
 * feature exists to avoid, so the rules are here: which extras are honoured, how far the search for a
 * row walks before it gives up, and where the row is left once it is found. The middle one is worth its
 * own test because it is the part that can fail silently - a search that stops one row short looks
 * exactly like a card that is not there - and the last one because a card that lands behind the heading
 * the jump itself pinned is a jump to a card nobody can read, which looks like a link that did nothing.
 */
class SettingsTargetTest {

    @Test
    fun `a target is taken only from the names this build knows`() {
        assertEquals(
            SettingsTarget.PartitionReadOnly,
            SettingsTarget.named(SettingsTarget.PartitionReadOnly),
        )
        assertNull(SettingsTarget.named(null))
        assertNull(SettingsTarget.named(""))
        // A key from a newer build, or a stray string from anywhere else, is not a jump to nowhere.
        assertNull(SettingsTarget.named("some_card_added_later"))
    }

    @Test
    fun `the search starts at the top, moves a row at a time, and stops at the end`() {
        // Nothing composed yet: the first look is at the top of the list.
        assertEquals(0, nextScrollSearchStep(visibleLastIndex = null, totalItems = 30))
        assertEquals(9, nextScrollSearchStep(visibleLastIndex = 8, totalItems = 30))
        // The last row has been looked at, so there is nothing left to search.
        assertNull(nextScrollSearchStep(visibleLastIndex = 29, totalItems = 30))
        assertNull(nextScrollSearchStep(visibleLastIndex = 30, totalItems = 30))
        // An empty list is not a place to look.
        assertNull(nextScrollSearchStep(visibleLastIndex = null, totalItems = 0))
        assertNull(nextScrollSearchStep(visibleLastIndex = 0, totalItems = 0))
    }

    @Test
    fun `a card that was jumped to lands below the heading pinned over it`() {
        // The heading is drawn over the top of the list rather than as a row of it, so the room above the
        // card is exactly the heading's height. Compose measures this offset forwards - positive scrolls the
        // item further up - so the sign is the easy thing to invert, and inverting it buries the card twice
        // over instead of uncovering it.
        assertEquals(-72, jumpLandingOffset(pinnedHeadingHeight = 72))
        // Nothing measured yet is no offset, which is a jump to the top of the list rather than past it.
        assertEquals(0, jumpLandingOffset(pinnedHeadingHeight = 0))
        // A height read back as negative is not a reason to scroll the card off the other side.
        assertEquals(0, jumpLandingOffset(pinnedHeadingHeight = -4))
        // And the room is never taken from below the card: the offset only ever pushes it down.
        (0..400).forEach { height ->
            assertTrue("a heading $height tall scrolls the card up", jumpLandingOffset(height) <= 0)
        }
    }

    @Test
    fun `the jump is given a measured heading rather than an assumed one`() {
        // The offset is only ever as good as the number passed in: an unmeasured heading means every jump
        // lands behind it, which is the bug this fixes and looks exactly like the feature being off.
        val page = source("MainActivity.kt")

        assertTrue(
            "the jump is not told how tall the heading over its card is",
            page.contains("jumpToSettingCard(settingsList, wanted, pinnedHeadingHeight)"),
        )
        assertTrue(
            "nothing measures a pinned heading, so the offset is always zero",
            page.contains("onPinnedHeight(coordinates.size.height)"),
        )
    }

    private fun source(name: String): String = candidateRoots()
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
        .firstOrNull()
        ?.readText()
        ?: throw AssertionError("$name was not found; the scan is looking at the wrong directory")

    private fun candidateRoots(): List<File> = listOf(
        File("src/main/java"),
        File("app/src/main/java"),
    ).filter(File::isDirectory)
}
