package app.jourjour.ui.entry

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.jourjour.R

/** As tall as the round button it starts life as, and as tall as the add button beside it. */
private val SEARCH_HEIGHT = 48.dp

/** What Material lifts a floating action button by, so that this one is lifted as far. */
private val FAB_ELEVATION = 6.dp

/**
 * Finding an entry in a long trip.
 *
 * It begins as a round button beside the one that adds an entry, and opens into a field of
 * the same height and the same lozenge, so that the one is plainly the other grown out: a
 * box appearing from nowhere would be a different control. Closing it is the cross at the
 * far end, which also puts every entry back.
 *
 * The caller gives it the width to grow into — the field fills whatever room is left beside
 * the add button, and the button itself never moves.
 */
@Composable
fun SearchControl(
    query: String?,
    onOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = query != null
    val width by animateFloatAsState(if (open) 1f else 0f, label = "search-width")

    val focus = remember { FocusRequester() }
    // Opening it is a request to type, so the cursor goes there without being asked twice.
    LaunchedEffect(open) {
        if (open) runCatching { focus.requestFocus() }
    }

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            // The same lift the add button has: the two float over the list together, and
            // one of them casting a shadow while the other lies flat would separate them.
            shadowElevation = FAB_ELEVATION,
            modifier = Modifier
                // Grown from the button's own width rather than from nothing. The minimum
                // comes first on purpose: a fraction of the width is an exact measurement,
                // and nothing inside it can widen a field that has been told to be nought.
                .widthIn(min = SEARCH_HEIGHT)
                .fillMaxWidth(width)
                .height(SEARCH_HEIGHT),
        ) {
            if (open) {
                OpenSearch(
                    query = query.orEmpty(),
                    onQueryChange = onQueryChange,
                    onClose = onClose,
                    focus = focus,
                )
            } else {
                IconButton(onClick = onOpen, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focus: FocusRequester,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 16.dp)
                .size(20.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
            cursorBrush = SolidColor(LocalContentColor.current),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .focusRequester(focus),
            decorationBox = { field ->
                // The placeholder is drawn behind the field rather than instead of it, so
                // that the cursor sits where the first letter will.
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hint_search_entries),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalContentColor.current.copy(alpha = PLACEHOLDER_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            },
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close_search),
            )
        }
    }
}

/** Enough to read as a prompt rather than as something already typed. */
private const val PLACEHOLDER_ALPHA = 0.6f
