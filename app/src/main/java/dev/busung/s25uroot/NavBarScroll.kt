package ctrl.mietze.veyraroot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * The floating bar's answer to a page being scrolled, and the connection the page scroll arrives on.
 *
 * The bar is the app shell's, but the thing that decides where it goes is the page underneath - and the shell
 * has no idea what any page is doing: the list, its state and its direction all belong to the page. The one
 * mechanism that crosses that line without threading a parameter through five screens is nested scroll, which
 * a scrolling list already dispatches to every ancestor that asks for it. So the shell asks, and a page does
 * not have to know that anything is listening.
 *
 * Attached to the pages rather than to the whole screen, which matters: the sheets and dialogs are drawn as
 * siblings of the pages, and scrolling a payload list inside one of them is not a page moving under the bar.
 */

/**
 * Which way a page is moving, as the bar cares about it.
 *
 * Named for the page's own direction rather than the finger's, because that is the thing the bar is following
 * and the two are opposites: a finger dragged up the screen moves the page toward its end.
 */
internal enum class PageScroll {
    /** Back toward the top of the page, which is where the bar belongs. */
    TowardTop,

    /** Deeper into the page, which is where the bar gets in the way. */
    TowardEnd,

    /** Nothing moved, or the scroll is settling. Not a direction, so not a decision. */
    Still,
}

/**
 * The direction a scroll delta is asking for.
 *
 * Compose reports a scroll as the distance the *content* should move: positive is content coming back down -
 * a finger or a fling heading for the top of the list - and negative is going deeper into it.
 */
internal fun pageScrollOf(delta: Float): PageScroll = when {
    delta > 0f -> PageScroll.TowardTop
    delta < 0f -> PageScroll.TowardEnd
    else -> PageScroll.Still
}

/**
 * Whether the bar is away after a page moves [direction].
 *
 * Any movement back toward the top brings it back, and any movement deeper takes it away - no threshold and
 * no accumulator, because the smallest upward movement is the user looking for the thing that left. A settle
 * keeps what it was, so a fling does not flicker the bar in and out as its tail decays.
 */
internal fun navBarHiddenAfter(hidden: Boolean, direction: PageScroll): Boolean = when (direction) {
    PageScroll.TowardEnd -> true
    PageScroll.TowardTop -> false
    PageScroll.Still -> hidden
}

/**
 * The connection that turns a page's scrolling into that decision.
 *
 * [report] is called only when the answer changes - a scroll is dozens of events a frame and the bar has two
 * states - and the amount consumed is read rather than the amount available: available includes the scroll a
 * list at its end refuses, and the bar should not leave because someone pulled at a page that had nowhere
 * left to go. Nothing is consumed here; this only watches.
 */
internal fun navBarScrollConnection(report: (Boolean) -> Unit): NestedScrollConnection {
    var hidden = false
    return object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val next = navBarHiddenAfter(hidden, pageScrollOf(consumed.y))
            if (next != hidden) {
                hidden = next
                report(next)
            }
            return Offset.Zero
        }
    }
}
