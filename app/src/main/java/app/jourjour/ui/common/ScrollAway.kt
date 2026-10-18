package app.jourjour.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether anything under the floating buttons is being scrolled, which is when they step
 * out of the way: a list being read is read for the rows the buttons stand over, and
 * nobody adds a row while still looking for the place.
 *
 * It listens to the scroll as it bubbles up rather than to a list's own state, so a screen
 * of several lists — the tabs of a trip — needs one of these and no wiring per tab.
 *
 * A scroll is over when its fling is, and that alone would do, except that a fling stopped
 * by a tap never reports the end of it. The quiet spell is what catches those: a scroll
 * that has moved nothing for that long is not going on.
 */
@Stable
class ScrollAwayState internal constructor(private val scope: CoroutineScope) {
    var scrolling by mutableStateOf(false)
        private set

    private var settle: Job? = null

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // Only what actually moved a list: a pull against its end, or a sideways
            // swipe between tabs, leaves the rows where they were and the buttons with them.
            if (consumed.y != 0f) {
                scrolling = true
                settle?.cancel()
                settle = scope.launch {
                    delay(QUIET_SPELL_MS)
                    scrolling = false
                }
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle?.cancel()
            scrolling = false
            return Velocity.Zero
        }
    }

    private companion object {
        const val QUIET_SPELL_MS = 400L
    }
}

@Composable
fun rememberScrollAwayState(): ScrollAwayState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ScrollAwayState(scope) }
}

/**
 * Shrinks a floating button away to its own centre while [state] says the list is moving,
 * and grows it back once it stops. Each button is shrunk on its own rather than the row
 * they stand in, so that two of them side by side each close up where they are instead of
 * both being drawn in towards the middle of the screen.
 *
 * [enabled] false keeps the button up whatever the list does — the search while it is a
 * field being typed into, which vanishing would take the keyboard away from.
 */
@Composable
fun Modifier.scrollAway(state: ScrollAwayState, enabled: Boolean = true): Modifier {
    val shown by animateFloatAsState(
        targetValue = if (enabled && state.scrolling) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "scroll-away",
    )
    return graphicsLayer {
        scaleX = shown
        scaleY = shown
        alpha = shown
    }
}
