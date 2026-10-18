package app.jourjour.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import app.jourjour.ui.theme.ChipShape

/**
 * The weights a control can have, in one file, so that no screen decides for itself what a
 * button looks like. Darkest first:
 *
 *  1. [PrimaryButton] and [AddFab] — the action the screen exists for.
 *  2. [SecondaryButton] — something useful along the way: take a photo, add a wallet.
 *  3. [FavoriteChip] — a suggestion to accept, half a button and half a value.
 *  4. A text button — an aside inside a card, left to Material as it is.
 *
 * Colour and shape both carry the ranking: a filled button is a lozenge, a favourite is
 * rounded, a field is nearly square.
 *
 * A destructive action inside a form — deleting a photo, dropping every photo of a trip —
 * is a [SecondaryButton] in the error colours: the weight of the actions beside it, and a
 * colour that says what it does. Never the filled button, and never bare red text, which
 * among lozenges reads as a link. Deleting the record itself is not a button at all but
 * the icon in the top bar of its form.
 */

/**
 * Wraps a click so that it first takes the cursor out of whatever field was holding it,
 * which is what puts the keyboard away.
 *
 * Anything that opens something else over a form — a camera, a picker, a sheet, a dialog —
 * has to do this. Without it the keyboard stays up behind whatever was opened, over the
 * very thing the button was pressed to show.
 */
@Composable
fun dismissingFocus(onClick: () -> Unit): () -> Unit {
    val focus = LocalFocusManager.current
    return {
        focus.clearFocus()
        onClick()
    }
}

/** Finishes the job the screen was opened for: save, add, export. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier,
    ) {
        Text(text)
    }
}

/**
 * The one round button on a screen. It keeps Material's shadow: the lift is what says it
 * floats over the list rather than belonging to any row of it.
 */
@Composable
fun AddFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.size(ROUND_BUTTON_SIZE),
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription)
    }
}

/**
 * A step under the filled button: it helps with the form without finishing it. Filled
 * rather than outlined, so that two of them side by side cannot look like different things.
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Button(
        // A secondary action always opens or changes something beside the field being
        // typed in, so it takes the cursor with it. See [dismissingFocus].
        onClick = dismissingFocus(onClick),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
        // Flat: the shadow is what marks out the one button that floats.
        elevation = null,
        modifier = modifier,
    ) {
        if (icon != null) Icon(icon, contentDescription = null)
        if (iconPainter != null) Icon(iconPainter, contentDescription = null)
        if (icon != null || iconPainter != null) Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/**
 * Narrower than [ROUND_BUTTON_SIZE], which is sized to a top bar: this one stands in a row
 * of fields, where a disc as wide as a bar button would be the tallest thing on it.
 */
val SMALL_ROUND_BUTTON_SIZE = 44.dp

/**
 * A [SecondaryButton] with no room for a word: the same rank and the same colours, a disc
 * instead of a lozenge. For a step whose mark says it better than a label would — the two
 * beside the date on the entry form, which walk it to the next entry of the trip.
 */
@Composable
fun SecondaryIconButton(
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        // It changes a field rather than typing in one, so it takes the cursor with it,
        // exactly as the lozenge version does. See [dismissingFocus].
        onClick = dismissingFocus(onClick),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.size(SMALL_ROUND_BUTTON_SIZE),
    ) {
        Icon(painter, contentDescription = contentDescription)
    }
}

/** A saved title, one tap from filling the field in. */
@Composable
fun FavoriteChip(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        shape = ChipShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        // Filled and unbordered: an outline here would read as a field, which is the one
        // thing a favourite must not be mistaken for.
        border = null,
        modifier = modifier,
    )
}

/**
 * An icon button standing on a list row. It is given a disc of its own because a row may
 * carry a photograph underneath, where a bare icon disappears. The disc is a shade off the
 * card rather than a tint: it stands on a card that is deliberately neutral, and a blue
 * coin on grey is the one place in the app where the blue has nothing to rank itself
 * against.
 */
@Composable
fun RowIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceBright),
    ) {
        icon()
    }
}
