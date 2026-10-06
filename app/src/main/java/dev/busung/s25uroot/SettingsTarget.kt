package ctrl.mietze.veyraroot

import androidx.compose.foundation.lazy.LazyListState

/**
 * A card in the settings list that another screen can point at.
 *
 * Both directions this app has for the read-only wall end on a switch that lives about twenty rows
 * into a list: the run screen's failure card, and a repair action refused in Settings itself. Telling
 * someone which switch it is and leaving them to find it is not the fix - they are on a screen that
 * just failed, and what they need is the row. So the row is named, its key travels with the intent, and
 * the settings page scrolls to it and outlines it for a moment.
 *
 * The value is also the item's own key in the list, which is what makes the jump possible without a
 * hand-counted index that would go stale the next time a card is added above it.
 */
internal object SettingsTarget {

    /** The intent extra that carries one. Namespaced, because the run screen sets it. */
    const val EXTRA = "ctrl.mietze.veyraroot.settings_target"

    /** Protect image partitions: the switch that marks boot, super and vbmeta read-only. */
    const val PartitionReadOnly = "partition_read_only"

    /** Full-screen root provider migration page. */
    const val RootMigration = "root_migration"

    /** VeyraKSU add-on page. */
    const val VeyraKsu = "veyra_ksu"

    /** CVeyra Access / Permission Provider full-screen page. */
    const val CVeyraAccess = "cveyra_access"

    /** Direct CVeyra Management entry, used by the center navigation mark. */
    const val CVeyraManagement = "cveyra_management"

    /**
     * Every target this build knows.
     *
     * The one place both the intent filter and the section map read, so a new target cannot be added as a
     * constant and forgotten in one of them: a card no section claims is a jump that opens nothing and
     * scrolls nowhere.
     */
    val all = listOf(PartitionReadOnly, RootMigration, VeyraKsu, CVeyraAccess, CVeyraManagement)

    /**
     * The target an extra names, or null when it names nothing this build knows.
     *
     * Null rather than a pass-through, so an extra from a newer build - or a stray string from
     * anywhere else - lands on the settings page rather than on a scroll to nowhere.
     */
    fun named(raw: String?): String? = raw?.takeIf { it in all }
}

/** How long a card that was jumped to stays outlined. Long enough to find, short enough not to nag. */
internal const val SETTINGS_HIGHLIGHT_MILLIS = 2_000L

/**
 * The most rows a jump will walk before giving up.
 *
 * A guard rather than a measurement: the search asks the list what it has composed and steps one row
 * at a time when the target is not in it, and a list that never contains the key would otherwise spin
 * for as long as the screen is open. Far above the number of rows this screen has, so it cannot cut a
 * real search short.
 */
private const val JUMP_SEARCH_LIMIT = 60

/**
 * How long a jump waits for the section it opened to put its rows in the list.
 *
 * The search asks the list what it has and gives up at the end of it, so a list that has not been rebuilt
 * yet - ten rows where it needs twelve - answers "not here" for a card that is about to be there. This is
 * the bound on waiting for those rows: a frame or two in practice, and a jump that never lands rather than
 * a highlight left standing over a card nobody was taken to.
 */
internal const val JUMP_OPEN_WAIT_MILLIS = 1_000L

/**
 * The next row to bring on screen when looking for one that is not composed yet, or null when there is
 * nowhere left to look.
 *
 * A [LazyListState] knows two things about itself: which items it has composed, and how many there
 * are. A card below the fold is not composed at all, so "is the target on screen" cannot answer
 * whether the list *has* it - the only way through is to walk the list one screenful at a time until
 * the key appears or the end is reached. This is the step of that walk, kept here so the rule can be
 * tested on its own: an empty reading starts at the top, the last row means the search is over, and
 * anything else moves on by one.
 */
internal fun nextScrollSearchStep(visibleLastIndex: Int?, totalItems: Int): Int? = when {
    totalItems <= 0 -> null
    visibleLastIndex == null -> 0
    visibleLastIndex >= totalItems - 1 -> null
    else -> visibleLastIndex + 1
}

/**
 * Where a card that was jumped to has to sit, given how tall the heading pinned over it is.
 *
 * A negative offset, and that sign is the whole point. A sticky heading is drawn *over* the top of the list
 * rather than as a row of it, so a jump that puts the card at the top of the viewport puts it behind the
 * heading - the card that was asked for ends up the one thing on screen nobody can read. Compose measures
 * this offset the other way round, where positive scrolls the item further up, so the sign is also the part
 * that is easy to get wrong: a positive offset here would bury the card twice over instead of uncovering it.
 *
 * Zero when the heading has not been measured: that is a jump to the top of the list, which is where this
 * feature started, rather than a jump into a hole.
 */
internal fun jumpLandingOffset(pinnedHeadingHeight: Int): Int = -(pinnedHeadingHeight.coerceAtLeast(0))

/**
 * Brings the card keyed [key] into view, one screenful at a time, and stops if the list does not have it.
 *
 * [pinnedHeadingHeight] is the room the card needs above it. It is passed in rather than assumed because the
 * heading is a row of this page whose height is a fact about the theme and the type scale, and this file has
 * no way to measure either.
 *
 * Silent about failure on purpose: what the caller does afterwards - outline the card - is worth doing
 * whether or not the scroll moved, and a jump that could not find its row is a bug in a key, not
 * something the person holding the phone can act on.
 */
internal suspend fun jumpToSettingCard(
    list: LazyListState,
    key: String,
    pinnedHeadingHeight: Int,
) {
    repeat(JUMP_SEARCH_LIMIT) {
        val info = list.layoutInfo
        val onScreen = info.visibleItemsInfo.firstOrNull { item -> item.key == key }
        if (onScreen != null) {
            list.animateScrollToItem(onScreen.index, jumpLandingOffset(pinnedHeadingHeight))
            return
        }
        val step = nextScrollSearchStep(
            visibleLastIndex = info.visibleItemsInfo.lastOrNull()?.index,
            totalItems = info.totalItemsCount,
        ) ?: return
        list.scrollToItem(step)
    }
}
