package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The settings page's sections, and the two ways a closed one can go wrong silently.
 *
 * A card another screen points at that no section claims is a jump that opens nothing and scrolls nowhere -
 * the person is put on the settings page with no row to look at, and nothing anywhere says so. And a
 * section whose cards are drawn outside its guard is a section that looks closed while its switches are
 * still on screen, which is how a collapse feature ends up trusted less than no feature at all.
 *
 * Both are checked here rather than on a device, because both are properties of the source.
 */
class SettingsSectionsTest {

    @Test
    fun `every card another screen can ask for belongs to a section`() {
        SettingsTarget.all.forEach { target ->
            assertNotNull(
                "nothing claims $target, so a jump to it would open nothing",
                SettingsSection.holding(target),
            )
        }
    }

    @Test
    fun `no section claims a key that is not a card`() {
        val claimed = SettingsSection.entries.flatMap { it.targets }

        assertEquals(
            "a section names a key no screen can ask for, which is a claim nothing can check",
            emptyList<String>(),
            claimed.filterNot { it in SettingsTarget.all },
        )
    }

    @Test
    fun `the read-only switch is in the run section`() {
        // Named rather than left to the loop above: this is the jump the run screen's failure card makes,
        // and it is the one that has to land on a switch rather than on the top of the page.
        assertEquals(
            SettingsSection.Run,
            SettingsSection.holding(SettingsTarget.PartitionReadOnly),
        )
    }

    @Test
    fun `a key that is not a card has no section`() {
        // Null rather than a default: guessing would scroll somewhere nobody asked for.
        assertNull(SettingsSection.holding("not_a_card"))
        assertNull(SettingsSection.holding(""))
    }

    @Test
    fun `stored names survive, and names from another build are dropped`() {
        val stored = setOf(
            SettingsSection.Root.name,
            SettingsSection.Recovery.name,
            // A section this build no longer has: About moved out of the settings page entirely, and a
            // stored name left by a build that still had it must be dropped rather than crash the page
            // on the way in.
            "About",
            "ASectionThisBuildDoesNotHave",
        )

        assertEquals(
            setOf(SettingsSection.Root, SettingsSection.Recovery),
            SettingsSection.named(stored),
        )
    }

    @Test
    fun `a page with nothing collapsed shows every section`() {
        // The whole page is the default, and it is the default because the collapsed set is what is stored:
        // nothing stored is nothing collapsed. It is asserted against the enum rather than against a number,
        // so a section added later is open on a fresh install without this test being told about it.
        assertEquals(
            SettingsSection.entries.toSet(),
            SettingsSection.open(emptySet()),
        )
    }

    @Test
    fun `every section can be collapsed, and stay that way`() {
        // The other end of the same rule: collapsing all eight is a state, not the absence of one. Stored as
        // the open set this could not be told apart from the fresh install, which is why the two are
        // opposite sets rather than the same one read twice.
        assertEquals(emptySet<SettingsSection>(), SettingsSection.open(SettingsSection.entries.toSet()))

        val collapsedNames = setOf(SettingsSection.Run.name, SettingsSection.Root.name)
        assertEquals(
            SettingsSection.entries.toSet() - SettingsSection.named(collapsedNames),
            SettingsSection.open(SettingsSection.named(collapsedNames)),
        )
    }

    @Test
    fun `every section is found by an icon of its own`() {
        val icons = SettingsSection.entries.map { it.icon }

        assertEquals(
            "two sections share an icon, and a glyph cannot be read slowly: the eye keeps landing on the " +
                "wrong row of the index",
            icons.size,
            icons.distinct().size,
        )
    }

    @Test
    fun `nothing collapsed is one card, so only its ends are rounded`() {
        val rows = SettingsSection.indexRows(open = emptySet())

        assertEquals(SettingsCardPosition.Top, rows.getValue(SettingsSection.entries.first()).position)
        assertEquals(SettingsCardPosition.Bottom, rows.getValue(SettingsSection.entries.last()).position)
        // Everything between them has to be square on both ends, which is what makes the eight rows read as
        // one card rather than as eight: a single Middle row out of place leaves a rounded seam in the
        // middle of it, and nothing on screen says which row that is.
        SettingsSection.entries.drop(1).dropLast(1).forEach { section ->
            assertEquals(
                "${section.name} sits between two others and is drawn as a card of its own",
                SettingsCardPosition.Middle,
                rows.getValue(section).position,
            )
        }
        // And it is the only card on the page, so no row owes a trailing gap: the last one would be paying
        // for space below the end of the list.
        assertFalse(
            "a row pays a gap that has nothing after it to separate",
            rows.values.any { it.endsBlock },
        )
    }

    @Test
    fun `a closed run ending before an open section pays the gap below itself`() {
        // The case that has no body to pay it: a heading, then an open section, with nothing in between that
        // scrolls away - so the run's last row owes the gap or the two cards sit a seam apart.
        val rows = SettingsSection.indexRows(open = setOf(SettingsSection.Root))
        val beforeRoot = SettingsSection.entries.takeWhile { it != SettingsSection.Root }

        assertTrue(
            "the run before ${SettingsSection.Root.name} owes nothing below it, so its last row and the open " +
                "heading are 2dp apart",
            rows.getValue(beforeRoot.last()).endsBlock,
        )
        // The run after it is the last thing on the page, so its own last row owes nothing: the gap above it
        // was paid by the open section's content, which is the only row that could have.
        val afterRoot = SettingsSection.entries.dropWhile { it != SettingsSection.Root }.drop(1)
        assertTrue(afterRoot.isNotEmpty())
        assertFalse(rows.getValue(afterRoot.last()).endsBlock)
    }

    @Test
    fun `an open section is a card of its own, and whatever it splits stays whole`() {
        val open = setOf(SettingsSection.Run)
        val rows = SettingsSection.indexRows(open)
        val before = SettingsSection.entries.takeWhile { it != SettingsSection.Run }
        val after = SettingsSection.entries.dropWhile { it != SettingsSection.Run }.drop(1)

        // The open heading heads its own content instead of sitting inside the index card.
        assertEquals(SettingsCardPosition.GroupedSingle, rows.getValue(SettingsSection.Run).position)
        // Both runs it leaves behind are cards with rounded ends, and the row after each run starts a block
        // - which is the gap that keeps the step the screenshot showed out of the page.
        assertEquals(SettingsCardPosition.Top, rows.getValue(before.first()).position)
        assertEquals(SettingsCardPosition.Bottom, rows.getValue(before.last()).position)
        assertEquals(SettingsCardPosition.Top, rows.getValue(after.first()).position)
        assertEquals(SettingsCardPosition.Bottom, rows.getValue(after.last()).position)
        // Rows inside a run stay flush, which is the dense card the collapsed page is supposed to be.
        assertFalse(rows.getValue(before.dropLast(1).last()).endsBlock)
        // The row that ends the run before the open section pays the gap, because that section's own body is
        // on the other side of it and cannot.
        assertTrue(rows.getValue(before.last()).endsBlock)
    }

    @Test
    fun `a lone collapsed section is a card with both ends rounded`() {
        // Every section open but one: the odd one out cannot share a card with anything, so it is a card of
        // its own rather than a flat row with nothing either side of it to join.
        val open = SettingsSection.entries.toSet() - SettingsSection.Root
        val rows = SettingsSection.indexRows(open)

        assertEquals(SettingsCardPosition.GroupedSingle, rows.getValue(SettingsSection.Root).position)
        SettingsSection.entries.filterNot { it == SettingsSection.Root }.forEach { section ->
            assertEquals(
                "an open section is part of no run, and ${section.name} was laid out as one",
                SettingsCardPosition.GroupedSingle,
                rows.getValue(section).position,
            )
        }
    }

    @Test
    fun `the page's own spacing is the seam between index rows`() {
        val page = source("MainActivity.kt")

        // The page's arrangement is what makes the index one card: at a group's gap the eight rows would be
        // eight cards, which is the layout this replaced. Read from the settings list's own call rather than
        // from anywhere in the file, since a group's inner spacing is the same number for its own reason.
        val arrangement = page.substringAfter("listState = settingsList,")
            .substringAfter("verticalArrangement = ")
            .substringBefore(",")
        assertEquals(
            "the settings page leaves a gap between index rows, so the index is not one card",
            "Arrangement.spacedBy(SETTINGS_CARD_SEAM)",
            arrangement,
        )
    }

    @Test
    fun `the page draws a header for every section`() {
        val called = headerCalls(source("MainActivity.kt"))

        assertEquals(
            "the page draws a header that is not a section, or draws one twice",
            SettingsSection.entries.map { it.name }.toSet(),
            called.toSet(),
        )
        assertEquals("a section has no header, so nothing on the page opens it", called.size, called.distinct().size)
    }

    @Test
    fun `every section's rows sit behind that section's guard`() {
        val page = source("MainActivity.kt")

        SettingsSection.entries.forEach { section ->
            assertTrue(
                "the ${section.name} section has no guarded rows: its cards would stay on screen " +
                    "while the section reads as closed",
                page.contains("if (SettingsSection.${section.name} in openSections) item"),
            )
        }
    }

    @Test
    fun `an open section's heading pins itself and a closed one does not`() {
        val page = source("MainActivity.kt")
        // The function's own body, read to its end rather than through a window tuned to its length: the
        // heading's documentation is part of the function, and a window that has to be widened every time a
        // sentence is added to it is a test that fails for the wrong reason.
        val heading = page.substringAfter("private fun LazyListScope.settingsSectionHeading")
            .substringBefore("\n}\n")

        assertTrue(
            "an open section's heading scrolls away, so a section longer than a screen stops saying whose " +
                "rows are on it",
            heading.contains("stickyHeader {"),
        )
        assertTrue(
            "a closed section's heading is pinned, and it would be held over the rows of the section below it",
            heading.contains("item {"),
        )
        assertTrue(
            "the heading is no longer drawn according to what is open, so nothing decides who pins",
            heading.contains("if (section in openSections)"),
        )
        // One place draws a sticky header: a second one is a row with no content under it pinning itself over
        // somebody else's, which is the state this rule exists to avoid.
        assertEquals(
            "more than one row of the page pins itself, so a row with nothing under it can claim a section",
            1,
            page.split("stickyHeader {").size - 1,
        )
        // And it paints the page behind itself: a sticky heading is drawn over its own section's rows, which
        // are its own width, so its rounded corners are the one place they would show through.
        assertTrue(
            "the pinned heading is transparent around its own corners, so its rows scroll through them",
            page.substringAfter("stickyHeader {").take(900).contains("background(MaterialTheme"),
        )
    }

    @Test
    fun `the settings list is given no room above it, so a pinned heading can meet the top`() {
        val page = source("MainActivity.kt")

        assertTrue(
            "the settings list is padded at the top, and a sticky heading sticks below that padding - which " +
                "is a band the rows below it scroll through above the pinned card",
            page.contains("PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)"),
        )
        assertTrue(
            "the space that used to be the list's padding is not on the title, so the page lost its top margin",
            page.contains("padding(top = SETTINGS_TOP_SPACE, bottom = 18.dp)"),
        )
    }

    /**
     * The sections the page draws a heading for, in the order they appear.
     *
     * Matched with the line wrapping left open, because the call wraps as soon as a row is given a value and
     * a search for the unwrapped text is a test that quietly stops looking at four of the eight rows.
     */
    private fun headerCalls(page: String): List<String> =
        Regex("settingsSectionHeading\\(\\s*SettingsSection\\.(\\w+),")
            .findAll(page)
            .map { it.groupValues[1] }
            .toList()

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
