package ctrl.mietze.veyraroot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The run's own controls, in a bar that floats over the page rather than ending it.
 *
 * The same treatment the tabs' pages give their navigation bar, for the same reason: a control that lives at
 * the end of a scrolling page can only be reached by scrolling to it, and on this screen the thing being read
 * is the log - which took the worst of it, ending where the buttons began. As a sibling of the page it costs
 * the page no layout, the log keeps the whole screen, and the controls are where a thumb already is.
 *
 * Which of them exist is not this file's business: it draws a row and the screen fills it. What is here is the
 * fade behind that row and the height it takes.
 *
 * The fade is drawn on the bar's own band rather than by the page, which is what makes it leave with the bar
 * and what stops a long page from arriving at the controls at full contrast. A gradient and not a blur: a blur
 * of a scrolling log means rendering it into an offscreen layer and re-blurring it every frame.
 *
 * There is deliberately **no container** around the controls - no pill, no outline, no surface of the bar's
 * own. One used to be here, carrying the same tinted shape and shadow as the tab bar's, and it was removed
 * for the reason a border is a border: the controls are already rounded shapes of their own, so a pill a few
 * dp outside them is a rounded band in a third colour sitting between the button and the page. On the tab bar
 * the pill has a job - its items are bare icons and labels, and the pill is the only thing giving the row an
 * edge. Here every control brings its own, so the pill enclosed them exactly the way a stroke would. What
 * separates the bar from the page is the fade, and a fade has no edge to read as one.
 *
 * Nothing here says how far the run has come. A hairline across the top edge used to, and it was **removed**
 * rather than moved: it sat directly over Stop in a bar whose buttons are the whole point, and a rule drawn
 * along the top of a button reads as part of that button. How far a run has got is on the status card's own
 * bar above the log, where it is a measurement rather than an ornament.
 */
@Composable
internal fun RunActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Before the insets, so the fade reaches the bottom of the screen rather than stopping at the
            // top of the gesture area - the page under the bar is page the bar is floating over too.
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Whether the run screen has anything to offer for this state, which is whether its bar exists at all.
 *
 * An empty bar over a page is worse than no bar, and this is the one place the question is answered - the
 * screen builds its row from the same phases, so the two cannot disagree about whether there is one.
 *
 * Busy covers every phase the app is working through, including the settle wait; the four at the end are the
 * states a run can finish in, and each has something to press: a retry after a failure or a stop, and a way
 * out after a success. [InstallPhase.Ready] is the one phase that is neither - the screen is open and the run
 * has not started - and that is a page with nothing to press rather than a bar with nothing in it.
 */
internal fun runControlsOffered(phase: InstallPhase, busy: Boolean): Boolean = when {
    busy -> true
    phase == InstallPhase.Failed -> true
    phase == InstallPhase.Stopped -> true
    phase == InstallPhase.Installed -> true
    phase == InstallPhase.RootOnly -> true
    else -> false
}
