package app.jourjour.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * Picking something up and putting it down somewhere else, which two of the trip's lists
 * are rearranged by: the plan and the checklist.
 *
 * The gesture is the same on both and so is the arithmetic under it — where on the screen
 * the finger is, how fast the list should come to a finger held near one of its ends, and
 * the mark that says where letting go would put it. What the places *mean* is each
 * screen's own, and stays there.
 *
 * **The finger is the only thing measured.** What is being carried is drawn over the list,
 * as far below the finger as the finger was below its top when it was picked up, and from
 * nothing else. It is not moved about inside the list: the line the gesture lives on is
 * kept in the list for as long as it lasts, but a list puts a line it is keeping off the
 * screen wherever it has room for it rather than where it belongs, and a block drawn
 * relative to that — or carried by the steps a gesture reports in its own moving space —
 * slid away from the finger the moment it left the screen.
 */

/**
 * Where a drag has got to — picked up, carried, let go, or taken away — and where the
 * finger is on the screen.
 *
 * Let go and taken away are two different endings and only the first one moves anything.
 * A gesture is taken away when the thing it lives on leaves the screen, or something else
 * claims the finger: nobody let go of anything, and a drop made then would land wherever
 * the mark happened to be at the moment — which, with another drag under way, is a place
 * that drag was aiming for.
 */
sealed interface Dragging {
    data class Grabbed(val pointerY: Float) : Dragging
    data class Moved(val pointerY: Float) : Dragging
    data object Dropped : Dragging
    data object Cancelled : Dragging
}

/** How thick the mark is that says where a dragged thing would land. */
val AIM_THICKNESS = 3.dp

/**
 * How much of its colour the mark takes: enough to be seen, light enough to stay a hint
 * about what would happen rather than a line somebody has drawn.
 */
const val AIM_STROKE_ALPHA = 0.45f

/** How much of the colour a whole thing takes when it is the target, rather than an edge. */
const val AIM_FILL_ALPHA = 0.18f

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
 * Held before it is moved: a list is scrolled with the same finger, and a thing that slid
 * out of place on a flick would be unusable.
 *
 * Reports where the finger is on the screen, worked out at the moment it is asked from
 * where the thing it is on stands then. Never from the steps the gesture reports between
 * one moment and the next: those are measured on the thing itself, and a thing a list has
 * just moved elsewhere reports the move as a step of the finger.
 *
 * @param key what the thing is, so that the gesture starts afresh when the row is handed a
 *   different one.
 */
@Composable
fun Modifier.draggedAfterLongPress(key: Any?, onDrag: (Dragging) -> Unit): Modifier {
    val report by rememberUpdatedState(onDrag)
    val here = remember { Here() }
    return this
        // On the same node as the gesture, and so in the same space: nothing between the
        // two moves one without the other.
        .onGloballyPositioned { here.coordinates = it }
        .pointerInput(key) {
            fun onScreen(local: Offset): Float =
                here.coordinates?.takeIf { it.isAttached }?.localToWindow(local)?.y ?: local.y
            detectDragGesturesAfterLongPress(
                onDragStart = { report(Dragging.Grabbed(onScreen(it))) },
                onDragEnd = { report(Dragging.Dropped) },
                onDragCancel = { report(Dragging.Cancelled) },
                onDrag = { change, _ ->
                    change.consume()
                    report(Dragging.Moved(onScreen(change.position)))
                },
            )
        }
}

/** Where a draggable thing stands, kept out of the state so that measuring it redraws nothing. */
private class Here {
    var coordinates: LayoutCoordinates? = null
}

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
 * The box that says a dragged thing would go into the whole of what is under it: filled
 * lightly and outlined, over it for the same reason the strip is.
 *
 * @param fill the colour the inside takes; @param stroke the colour of the outline.
 */
fun Modifier.aimedWhole(fill: Color, stroke: Color): Modifier = drawWithContent {
    drawContent()
    val thickness = AIM_THICKNESS.toPx()
    drawRect(fill)
    // Inset by half its thickness, so that the whole outline falls inside what it marks
    // rather than half of it on the neighbours.
    drawRect(
        stroke,
        topLeft = Offset(thickness / 2, thickness / 2),
        size = Size(size.width - thickness, size.height - thickness),
        style = Stroke(width = thickness),
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
