package ctrl.mietze.veyraroot

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape a run of settings cards has to be in.
 *
 * A group is one rounded container: `Top` draws the rounded top and flat bottom, `Middle` is flat both
 * ways, `Bottom` closes it. The compiler is happy with any order, so a card filed as `Top` between two
 * others is invisible until someone looks at the screen - which is exactly how the payloads group came
 * to draw its sources card as the start of a second list.
 *
 * The positions are read out of the sources because that is where the decision is written. A group is
 * drawn inside one composable, so each file is checked on its own.
 */
class SettingsCardGroupTest {

    @Test
    fun `every group opens once and closes once`() {
        val bySource = positionsInSource()
        assertTrue("no card positions found; the scan is looking at the wrong files", bySource.isNotEmpty())

        bySource.forEach { (source, positions) ->
            var groupOpen = false
            positions.forEachIndexed { index, position ->
                when (position) {
                    "Top" -> {
                        assertFalse(
                            "$source: a card opened a group at #$index while another was still open",
                            groupOpen,
                        )
                        groupOpen = true
                    }
                    "Middle" -> assertTrue(
                        "$source: a middle card at #$index has no group to belong to",
                        groupOpen,
                    )
                    "Bottom" -> {
                        assertTrue("$source: a bottom card at #$index has no group to close", groupOpen)
                        groupOpen = false
                    }
                    "GroupedSingle" -> assertFalse(
                        "$source: a card at #$index is a group of its own, inside another group",
                        groupOpen,
                    )
                    else -> throw AssertionError(
                        "$source: card at #$index uses an unhandled position: $position",
                    )
                }
            }
            assertFalse("$source: the last group was never closed", groupOpen)
        }
    }

    @Test
    fun `every group is a top, any number of middles, and a bottom`() {
        val groups = positionsInSource().values
            .flatten()
            .filter { position -> position != "GroupedSingle" }
            .fold(mutableListOf<MutableList<String>>()) { groups, position ->
                if (groups.isEmpty() || groups.last().last() == "Bottom") {
                    groups.add(mutableListOf(position))
                } else {
                    groups.last().add(position)
                }
                groups
            }

        assertTrue("no complete group was found", groups.isNotEmpty())
        groups.forEach { group ->
            assertEquals("a group starts with something other than a top: $group", "Top", group.first())
            assertEquals("a group ends with something other than a bottom: $group", "Bottom", group.last())
            assertEquals(
                "a group holds more than one top: $group",
                1,
                group.count { it == "Top" },
            )
        }
    }

    @Test
    fun `the shape a card rests at is the shape anything drawn around it has to use`() {
        // The outline a settings jump draws reads this, so the values are pinned: an outline on the
        // wrong curve sits off the card's edge and reads as a second border rather than as a pointer.
        assertEquals(24.dp, settingsCardRestingRadius(SettingsCardPosition.Top, top = true))
        assertEquals(6.dp, settingsCardRestingRadius(SettingsCardPosition.Top, top = false))
        assertEquals(6.dp, settingsCardRestingRadius(SettingsCardPosition.Middle, top = true))
        assertEquals(6.dp, settingsCardRestingRadius(SettingsCardPosition.Middle, top = false))
        assertEquals(6.dp, settingsCardRestingRadius(SettingsCardPosition.Bottom, top = true))
        assertEquals(24.dp, settingsCardRestingRadius(SettingsCardPosition.Bottom, top = false))
        // A group of one is rounded at both ends.
        assertEquals(24.dp, settingsCardRestingRadius(SettingsCardPosition.GroupedSingle, top = true))
        assertEquals(24.dp, settingsCardRestingRadius(SettingsCardPosition.GroupedSingle, top = false))
        // A card on its own is smaller all round than the ends of a group.
        assertEquals(16.dp, settingsCardRestingRadius(SettingsCardPosition.Single, top = true))
        assertEquals(16.dp, settingsCardRestingRadius(SettingsCardPosition.Single, top = false))
    }

    @Test
    fun `the restart sheet's rows are the settings list's rows`() {
        // The six ways out were six separately-rounded `Surface`s of their own, which is the one thing this
        // vocabulary exists to avoid. Checked as source rather than as a screenshot because the failure is
        // invisible in a diff and compiles cleanly: the rows would simply go back to a second idea of what a
        // row is, one screen away from the first, and nothing on the page would look obviously wrong.
        val sheet = sourceRoot()
            .walkTopDown()
            .first { file -> file.isFile && file.name == "RebootUi.kt" }
            .readText()

        assertTrue("the sheet's rows are not cards any more", sheet.contains("Card("))
        assertTrue(
            "the rows take no position in a group, so they cannot share the group's curve",
            sheet.contains("rebootRowPosition("),
        )
        assertTrue(
            "the rows draw a curve of their own instead of the settings card's, press swell included",
            sheet.contains("expressiveClickableCardShape("),
        )
        assertFalse("the sheet has a Surface of its own again", sheet.contains("Surface("))
    }

    /**
     * The positions in the order each file declares them.
     *
     * Only the cards themselves count, and only those that name a position: a card that leaves it out
     * is a single on its own and belongs to no group. Matching the assignment rather than the enum
     * keeps the shape helper's own comparisons out of the sequence.
     */
    private fun positionsInSource(): Map<String, List<String>> = sourceRoot()
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .mapNotNull { file ->
            val positions = CARD_POSITION.findAll(file.readText())
                .map { match -> match.groupValues[1] }
                .filter { position -> position != "Single" }
                .toList()
            if (positions.isEmpty()) null else file.name to positions
        }
        .toMap()

    private fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
        )
        return candidates.firstOrNull(File::isDirectory)
            ?: throw AssertionError(
                "the sources were not found from ${File(".").absolutePath}",
            )
    }

    private companion object {
        /** The assignment, so a comparison in the shape helper is not read as a card. */
        val CARD_POSITION = Regex("""position = SettingsCardPosition\.(\w+)""")
    }
}
