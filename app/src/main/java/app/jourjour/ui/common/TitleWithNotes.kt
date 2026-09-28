package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.theme.FieldShape

/**
 * Two fields stacked into one block: rounded outside, square where they meet. Used by a
 * title and its notes, and by an amount and what it settled for.
 */
val StackedTopShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
val StackedBottomShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

/**
 * The round chevron that folds a field away, sized so that half of it covers the lower
 * border of the field above. Shared so that every fold on a form is the same size.
 */
val FOLD_TOGGLE_SIZE = 32.dp

/** Notes open at two lines: at one line they would be indistinguishable from the title. */
private const val NOTES_MIN_LINES = 2

/**
 * A title with its notes folded away behind a chevron standing on the title's lower
 * border, and a star that makes the title a favourite of whatever it belongs to.
 *
 * The chevron sits on the border of the field it extends rather than below it: notes are a
 * longer version of the title, and a button standing on its own further down would read as
 * belonging to whatever happened to follow.
 */
@Composable
fun TitleWithNotesField(
    title: String,
    notes: String,
    isFavorite: Boolean,
    isNotesVisible: Boolean,
    onTitleChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleNotes: () -> Unit,
    notesFocus: FocusRequester,
    @StringRes labelRes: Int = R.string.field_entry_title,
) {
    val favoriteDescription = stringResource(
        if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite
    )
    val notesDescription = stringResource(
        if (isNotesVisible) R.string.action_hide_notes else R.string.action_show_notes
    )
    // Where the two fields meet, which is where the control goes. It is measured rather
    // than assumed: the height of a text field follows the font the device is set to.
    var titleHeight by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { FieldLabel(stringResource(labelRes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                trailingIcon = {
                    IconButton(
                        onClick = onToggleFavorite,
                        enabled = title.isNotBlank(),
                    ) {
                        // The star is drawn with glyphs rather than icons:
                        // material-icons-core carries only the filled star, and the
                        // outlined one would drag in the whole extended set.
                        Text(
                            text = if (isFavorite) "★" else "☆",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.semantics {
                                contentDescription = favoriteDescription
                            },
                        )
                    }
                },
                // With the notes open the two fields are one block, so the edge they share
                // is left square and only the outside of the pair is rounded.
                shape = if (isNotesVisible) StackedTopShape else FieldShape,
                colors = formFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { titleHeight = it.height },
            )
            if (isNotesVisible) {
                // Flush against the title: notes are a longer version of it, not a
                // separate part of the form. Two lines from the outset, because one line
                // is what the title already is.
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { FieldLabel(stringResource(R.string.field_notes)) },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    minLines = NOTES_MIN_LINES,
                    shape = StackedBottomShape,
                    colors = formFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(notesFocus),
                )
            } else {
                // Room for the lower half of the control, which has nothing to sit on
                // while the notes are away.
                Spacer(modifier = Modifier.height(FOLD_TOGGLE_SIZE / 2))
            }
        }
        // The circle is filled with the page colour so the border passes behind it rather
        // than through it, the way the floating label cuts the border at the top.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, titleHeight - FOLD_TOGGLE_SIZE.roundToPx() / 2) }
                .size(FOLD_TOGGLE_SIZE),
        ) {
            IconButton(onClick = onToggleNotes) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_double_down),
                    contentDescription = notesDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Half a turn points the same glyph the other way: down to open, up
                    // to close.
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (isNotesVisible) 180f else 0f),
                )
            }
        }
    }
}

/** The category's saved titles, each a tap away from filling the title in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoriteTitles(favorites: List<String>, onPick: (String) -> Unit) {
    if (favorites.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        favorites.forEach { favorite ->
            FavoriteChip(onClick = { onPick(favorite) }, label = favorite)
        }
    }
}
