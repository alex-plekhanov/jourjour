package app.jourjour.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R

/**
 * What a record says beyond its name, as a block of a list shows it — an event of the plan,
 * an item of the checklist.
 *
 * Under the name and quieter than it, since the name is what the eye looks for and this is
 * what it reads once it has found it.
 */

/** The chevron that unfolds the rest of a record's notes. */
val NOTES_TOGGLE_SIZE = 24.dp

/** How much of a record's notes a block shows before it takes asking. */
private const val NOTES_LINES = 2

/**
 * A record's notes, under its name.
 *
 * Two lines is the whole of the question. Notes that fit in two are simply there, with no
 * control at all — a chevron that can only ever say "that was all of it" is worse than no
 * chevron. Longer notes run out instead of stopping: the text fades away down those two
 * lines, which says there is more without spending a line on saying it.
 *
 * @param onOverflow told whether there is more than these lines show, which is what the
 *   block needs in order to decide about its chevron. Reported from the shut state alone:
 *   open, the text has no overflow and the answer would be "nothing more" every time.
 */
@Composable
fun BlockNotes(
    text: String,
    isOpen: Boolean,
    overflows: Boolean,
    onOverflow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isOpen) Int.MAX_VALUE else NOTES_LINES,
        onTextLayout = { if (!isOpen) onOverflow(it.hasVisualOverflow) },
        modifier = modifier
            .fillMaxWidth()
            .then(if (overflows && !isOpen) Modifier.fadingOut() else Modifier),
    )
}

/**
 * The chevron that opens the rest of the notes, or shuts them again.
 *
 * Against the foot of the block and in the middle of it, wherever a block puts it: a
 * control for the whole block, not for the middle of the notes it happens to act on.
 */
@Composable
fun BlockNotesToggle(isOpen: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_double_down),
        contentDescription = stringResource(
            if (isOpen) R.string.action_hide_notes else R.string.action_show_notes
        ),
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier
            .size(NOTES_TOGGLE_SIZE)
            .clip(RoundedCornerShape(NOTES_TOGGLE_SIZE / 2))
            .clickable(onClick = onToggle)
            .rotate(if (isOpen) 180f else 0f),
    )
}

/**
 * Text that runs out rather than stopping: whole at the top, gone at the bottom.
 *
 * Drawn off-screen first and then multiplied by the gradient, which is the only way to
 * fade what has already been painted — an alpha on the text would fade all of it evenly
 * and say nothing about where it was cut off.
 */
private fun Modifier.fadingOut(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
            blendMode = BlendMode.DstIn,
        )
    }
