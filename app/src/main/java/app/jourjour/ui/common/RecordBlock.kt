package app.jourjour.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * One record of a list on a tab of a trip — an event of the plan, a line of the budget, an
 * item of the checklist, a wallet, an entry — and the page it stands on.
 *
 * They are the same kind of thing to read and the same thing to do with: one record among
 * others, which opens on a tap. So they are one block, set in from the edges, lifted off
 * the page by a hair of shadow and filled the lighter shade, under bands of the deeper one
 * that head them. What is free, unbooked or merely a heading is the page; what somebody
 * wrote down stands on it.
 */

/** The corner a record has; a field is of the same family. */
val BLOCK_CORNER = 12.dp

/**
 * How far a block stands off the page.
 *
 * Enough to be seen against empty time and no more: a column of raised blocks a hair
 * apart is read as a run of things, and any deeper a shadow would start reading as a stack
 * of cards laid over one another.
 */
val BLOCK_ELEVATION = 2.dp

/** How far a block is set in from the edges of the screen. */
val BLOCK_MARGIN = 12.dp

/** What keeps one block from running into the next without putting a hole between them. */
val BLOCK_GAP = 3.dp

/**
 * Makes what it is put on a record block: set in, lifted, filled.
 *
 * Anything that moves the block about — a drag carrying it — goes before this, and what
 * the block responds to — a tap — after it, so that the press is clipped to the corners
 * the block lifts rather than washing over the margin around it.
 *
 * @param shape the rounded corners by default; a block cut square where it carries on into
 *   the next — an event running past midnight — gives its own.
 */
@Composable
fun Modifier.recordBlock(shape: Shape = RoundedCornerShape(BLOCK_CORNER)): Modifier = this
    .padding(horizontal = BLOCK_MARGIN, vertical = BLOCK_GAP)
    .shadow(BLOCK_ELEVATION, shape)
    .background(MaterialTheme.colorScheme.surfaceContainer)
