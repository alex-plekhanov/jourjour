package app.jourjour.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Picking something up and putting it down somewhere else, which two of the trip's lists
 * are rearranged by: the plan and the checklist.
 *
 * The gesture is the same on both and so is the arithmetic under it — how far the finger
 * has carried the thing, where on the screen it is, how fast the list should come to a
 * finger held near one of its ends, and the mark that says where letting go would put it.
 * What the places *mean* is each screen's own, and stays there.
 */

/** Where a drag has got to: grabbed, carried so far, let go. */
sealed interface Dragging {
    data object Grabbed : Dragging
    data class Moved(val dy: Float, val pointerY: Float) : Dragging
    data object Dropped : Dragging
}

/** How thick the mark is that says where a dragged thing would land. */
val AIM_THICKNESS = 3.dp

/**
 * How much of its colour the mark takes: enough to be seen, light enough to stay a hint
 * about what would happen rather than a line somebody has drawn.
 */
const val AIM_STROKE_ALPHA = 0.45f

/**
 * How solid a thing is while it is being carried.
 *
 * It is under the finger and over the list, and the place it would land is often directly
 * beneath it — the line under the one being carried is exactly as far away as that line is
 * tall. Carried opaque, it would hide the very thing it is being aimed at.
 */
const val CARRIED_ALPHA = 0.55f

/** How near an end of the list a held finger brings the list to itself. */
val AUTO_SCROLL_EDGE = 80.dp

/** How far the list moves in a frame at the very edge, and proportionally less before it. */
val AUTO_SCROLL_STEP = 7.dp

/**
 * The strip that says a dragged thing would line up with this edge of what is under it.
 *
 * Drawn over what it marks rather than behind it: a list is made of bands and blocks with
 * colours of their own, and a mark drawn behind one of those would be underneath it.
 */
fun Modifier.aimedAlong(atTop: Boolean, colour: Color): Modifier = drawWithContent {
    drawContent()
    val thickness = AIM_THICKNESS.toPx()
    drawRect(
        colour,
        topLeft = Offset(0f, if (atTop) 0f else size.height - thickness),
        size = Size(size.width, thickness),
    )
}

/**
 * How far the list should move this frame to come to a finger held near one of its ends.
 *
 * Nothing at all through the middle of the list, and faster the nearer the edge, so that
 * the speed is asked for rather than tripped over: a drag that strays into the top band by
 * a hair moves the list by a hair.
 */
fun edgeStep(
    pointer: Float,
    viewport: ClosedFloatingPointRange<Float>,
    edge: Float,
    fastest: Float,
): Float {
    if (viewport.endInclusive <= viewport.start) return 0f
    val above = viewport.start + edge - pointer
    if (above > 0f) return -fastest * (above / edge).coerceAtMost(1f)
    val below = pointer - (viewport.endInclusive - edge)
    if (below > 0f) return fastest * (below / edge).coerceAtMost(1f)
    return 0f
}
