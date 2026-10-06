package ctrl.mietze.veyraroot

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings page, as the parts it is read by.
 *
 * The page is long enough that finding one switch in it meant scrolling past everything else, and most
 * visits are for one thing. So each part is a section that can be collapsed to a single heading - the
 * headings of consecutive collapsed sections being one dense card - and stays as it was left.
 *
 * Every section is **open** unless it is collapsed, and the preference stores the collapsed ones: nothing
 * stored then means the whole page open, which is where a settings page belongs, and the one arrangement the
 * page cannot be caught in is the one where eight rows stand for switches nobody can see. [icon] is the
 * glyph a row is found by at a glance, which is what makes the closed page readable at all.
 *
 * [targets] is what makes a closed section safe for the cards other screens point at. A jump is a scroll to
 * a row's key, and a closed section has no rows at all - so the section that holds a card is the first
 * thing a jump opens, and the list of cards is kept here rather than inferred from the page, where the two
 * could drift apart one card at a time.
 */
internal enum class SettingsSection(
    @StringRes val title: Int,
    /**
     * The glyph the index row is found by.
     *
     * One per section, and unlike the words around it a glyph cannot be read slowly: two sections sharing one
     * would be two rows the eye keeps landing on the wrong one of. A section's own first card may well use the
     * same icon - that is the row saying what it opens.
     */
    val icon: ImageVector,
    /** The cards another screen can ask for by key, which are drawn inside this section. */
    val targets: List<String> = emptyList(),
) {
    Appearance(R.string.appearance, Icons.Rounded.Palette),
    Payloads(R.string.settings_section_payloads, Icons.Rounded.Folder),
    Run(R.string.settings_section_run, Icons.Rounded.Bolt, targets = listOf(SettingsTarget.PartitionReadOnly)),
    Shizuku(R.string.settings_section_cveyra, Icons.Rounded.Terminal),
    WirelessAdb(R.string.settings_section_adb_manager, Icons.Rounded.Link),
    Root(R.string.settings_section_root, Icons.Rounded.Security),
    Recovery(R.string.settings_recovery, Icons.Rounded.RestartAlt),
    System(R.string.settings_section_system, Icons.Rounded.Settings);

    companion object {
        /**
         * The sections a page shows, given the ones it has collapsed.
         *
         * The closed set is what is stored because it is the shorter list and because of what an empty one
         * has to mean: with the open set stored, "nothing stored" and "everything closed by hand" are the
         * same value, so the page could not tell a fresh install from a deliberate collapse - and the fresh
         * install would have shown the collapsed page. Inverting the storage gives the default for free and
         * keeps the other state storable.
         */
        fun open(closed: Set<SettingsSection>): Set<SettingsSection> = entries.filterNot { it in closed }.toSet()

        /**
         * The headings' layout: which card each one belongs to, and where the page's gaps go.
         *
         * A **closed** section is a row of the index card - the dense block of headings - and an **open** one
         * is a card of its own with its content under it. So the page is a sequence of runs: consecutive
         * closed sections share one card and sit flush at the seam, a section that is open stands apart with a
         * group's gap above and below, and a run of one is a card with both its ends rounded.
         *
         * Nested rather than flush is what the open section cannot be: its content is made of cards, and a
         * rounded card inside a card leaves the step that reads as a notch - full-width bar, narrower rows,
         * full-width bar again. Standing the open section apart is the one arrangement with no step in it.
         *
         * Written out here rather than decided at each of the eight call sites because the answer is about the
         * *neighbours*: a row is square-bottomed only when another row of the same card follows it, and eight
         * numbers written by hand cannot know that. Adding, moving or opening a section is then a change to
         * this rule rather than to eight places that each half-remember it.
         */
        fun indexRows(open: Set<SettingsSection>): Map<SettingsSection, SettingsIndexRow> {
            // The page as its cards: consecutive closed sections share one, and each open section is one.
            val blocks = mutableListOf<List<SettingsSection>>()
            var run = mutableListOf<SettingsSection>()
            entries.forEach { section ->
                if (section in open) {
                    if (run.isNotEmpty()) {
                        blocks += run
                        run = mutableListOf()
                    }
                    blocks += listOf(section)
                } else {
                    run += section
                }
            }
            if (run.isNotEmpty()) blocks += run

            val rows = mutableMapOf<SettingsSection, SettingsIndexRow>()
            blocks.forEachIndexed { blockIndex, block ->
                block.forEachIndexed { index, section ->
                    rows[section] = SettingsIndexRow(
                        position = when {
                            block.size == 1 -> SettingsCardPosition.GroupedSingle
                            index == 0 -> SettingsCardPosition.Top
                            index == block.lastIndex -> SettingsCardPosition.Bottom
                            else -> SettingsCardPosition.Middle
                        },
                        // The first block of the page owes nothing: the title above it ends in a gap of its
                        // own. The last one owes nothing either - the page's bottom clearance is below it -
                        // and an open section's gap below is paid by the content that follows its heading.
                        endsBlock = index == block.lastIndex &&
                            blockIndex != blocks.lastIndex &&
                            block.first() !in open,
                    )
                }
            }
            return rows
        }

        /**
         * The section a jump is aimed at, or null when no section claims that key.
         *
         * Null rather than a default, because guessing would scroll somewhere the caller did not ask for;
         * a key with no section is a card that moved without its entry here being moved with it.
         */
        fun holding(target: String): SettingsSection? = entries.firstOrNull { target in it.targets }

        /**
         * The sections named by [names], which is the shape they are stored in.
         *
         * By name rather than by ordinal, so a stored set survives sections being reordered in a later
         * build - and unknown names are dropped, so one left by a build that had a section this one does
         * not is a section that no longer opens rather than a crash on the way into the page.
         */
        fun named(names: Set<String>): Set<SettingsSection> = entries.filter { it.name in names }.toSet()
    }
}

/**
 * How the page draws one section's heading, given what its neighbours are doing.
 */
internal data class SettingsIndexRow(
    /** The corners the heading's card rests at. */
    val position: SettingsCardPosition,
    /**
     * True when the gap below this heading belongs to it, because nothing after it can pay one.
     *
     * Gaps are paid by the row *above* them, which is what lets the row below start flush. It matters most
     * for a pinned heading: a sticky heading is placed at the very top of the list, so a gap it paid for
     * itself would be a band of page above it every time it stuck, and a heading that only pays downwards
     * meets the top of the screen exactly.
     */
    val endsBlock: Boolean,
)

