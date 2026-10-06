package ctrl.mietze.veyraroot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A screen's list, with the button that returns it to the top.
 *
 * Every scrolling page goes through this instead of placing the button itself, because the button has to be
 * a sibling of the list rather than a thing the list draws: it floats over the content and must not scroll
 * with it. The state is the caller's, since a page that jumps to a card needs to drive the same state this
 * draws over.
 *
 * The list's own bottom padding is extended by the room the button and the floating navigation bar need, so
 * the last card cannot end up sitting under either. Pages keep their own padding and do not have to know
 * what is drawn over them.
 */
@Composable
internal fun PageList(
    padding: PaddingValues,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(modifier = modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + pageBottomClearance(NAV_BAR_HEIGHT),
            ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        BackToTopFab(
            listState = listState,
            modifier = Modifier.align(Alignment.BottomEnd).pageBottomInset(NAV_BAR_HEIGHT),
        )
    }
}

/**
 * Whether a list has moved, which is the rule the button keys off.
 *
 * Held in one place because the pages that share the wrapper and the one that does not both use it, and the
 * failure of a mismatch is a button that is not there when the list has run off the screen.
 */
@Composable
internal fun rememberScrolledState(listState: LazyListState): State<Boolean> = remember(listState) {
    derivedStateOf {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    }
}

/** A list state for a page that draws its own list rather than going through [PageList]. */
@Composable
internal fun rememberPageListState(): LazyListState = rememberLazyListState()

/**
 * A screen that scrolls as a column, with the button that returns it to the top and a place for the bar that
 * floats over it.
 *
 * The same job [PageList] does for a list, for a screen whose content is not one - and the reason the button
 * is drawn here rather than at the call site: it has to be a sibling of the scrolling column, not a child of
 * it, so that it stays put while the column moves under it.
 */
@Composable
internal fun PageColumn(
    padding: PaddingValues,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    /**
     * The bar that floats over this screen, drawn over the column and measured for the room it takes.
     *
     * A slot rather than a page item for the same reason [PageList] keeps its button out of the list: a bar
     * that scrolls with the content is not a bar - and on the screen this exists for, the bar was the thing
     * the log ended against. The page ends clear of it by the bar's own measured height, so a screen that
     * floats one does not have to know how tall it is: a row of one button is not a row of three. A bar that
     * draws nothing measures zero, which is what makes an empty one cost nothing.
     */
    bar: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var barHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
            verticalArrangement = verticalArrangement,
        ) {
            content()
            // The room the button and the bar take, as the column's own last item: it has to be inside the
            // scroll, or the page could not be scrolled far enough to bring its end above the bar.
            if (barHeight > 0.dp) {
                Spacer(Modifier.height(barHeight + BACK_TO_TOP_CLEARANCE))
            }
        }
        BackToTopFab(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.BottomEnd).pageBottomInset(barHeight),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    barHeight = with(density) { coordinates.size.height.toDp() }
                },
            content = bar,
        )
    }
}

/**
 * The button that takes a long list back to its start.
 *
 * It draws itself only once the list has actually moved. One that is always there covers the row it sits
 * over for no reason, and it is at the top already - which is where it would take you.
 *
 * Animated rather than a jump: the list is what is being read, and losing it in one frame makes it hard to
 * tell where the top of it was.
 */
@Composable
internal fun BackToTopFab(listState: LazyListState, modifier: Modifier = Modifier) {
    val scrolled by rememberScrolledState(listState)
    BackToTopButton(visible = scrolled, modifier = modifier) {
        listState.animateScrollToItem(0)
    }
}

/** The same button for a screen that scrolls a column rather than a list. */
@Composable
internal fun BackToTopFab(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    BackToTopButton(visible = scrolled, modifier = modifier) {
        scrollState.animateScrollTo(0)
    }
}

@Composable
private fun BackToTopButton(
    visible: Boolean,
    modifier: Modifier,
    scrollToTop: suspend () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        SmallFloatingActionButton(
            onClick = {
                clickHaptic(view)
                scope.launch { scrollToTop() }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.action_back_to_top),
            )
        }
    }
}

/**
 * What the button takes of a list's own bottom padding.
 *
 * A small button is 40dp and sits 20dp off the edge, so this leaves the last row a little air above it.
 */
private val BACK_TO_TOP_CLEARANCE = 72.dp

/**
 * The room a page's last row keeps under it: the button's clearance, plus the bar the button now sits above.
 *
 * Both are needed rather than the larger of the two - the button stands on top of the bar, so the row it must
 * not sit under is the higher of the two, and that is the sum.
 *
 * The height of the bar is the caller's, because the two screens that float one do not float the same one: the
 * tab bar is a pill of four fixed items and the run screen's is whatever the run has to offer.
 */
private fun pageBottomClearance(barHeight: Dp): Dp = BACK_TO_TOP_CLEARANCE + barHeight

/**
 * Where the button sits in a page's bottom corner: its own air, then the bar's height, because the bar is
 * drawn over the page rather than beside it and the button would otherwise be behind the pill.
 */
private fun Modifier.pageBottomInset(barHeight: Dp): Modifier =
    padding(20.dp).padding(bottom = barHeight)
