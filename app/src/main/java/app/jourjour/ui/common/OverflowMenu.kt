package app.jourjour.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R

/** As wide as the edit button on a trip, which the round buttons are sized to match. */
val ROUND_BUTTON_SIZE = 48.dp

/**
 * The three-dot menu of a screen.
 *
 * Items are handed a `dismiss` of their own instead of the menu closing itself on every
 * tap: an item that opens a dialog has to close the menu first, and one that only toggles
 * something may want it left open.
 */
@Composable
fun OverflowMenu(items: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    // Boxed together, because a dropdown opens where it was placed in the layout and the
    // box is what places it under its own button. Left as siblings they are two children
    // of whatever row holds them, and in a row with a stretched child — a list row, where
    // the text takes the space the button does not — the menu is placed at the start of
    // the row and opens against the far edge of the screen.
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}

/**
 * The tail every menu in the app shares: the dictionaries, then the settings, then what
 * the app is. Written once so that the trip list and a trip cannot drift apart.
 *
 * The dictionaries keep the emoji they are marked with elsewhere in the app, so the same
 * thing looks the same wherever it turns up.
 */
@Composable
fun ColumnScope.CommonMenuItems(
    dismiss: () -> Unit,
    onCurrencies: () -> Unit,
    onPaymentMethods: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_currencies)) },
        leadingIcon = { EmojiIcon("💱") },
        onClick = { dismiss(); onCurrencies() },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_payment_methods)) },
        leadingIcon = { EmojiIcon("💳") },
        onClick = { dismiss(); onPaymentMethods() },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_categories)) },
        leadingIcon = { EmojiIcon("🏷") },
        onClick = { dismiss(); onCategories() },
    )

    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_settings)) },
        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
        onClick = { dismiss(); onSettings() },
    )

    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_about)) },
        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
        onClick = { dismiss(); onAbout() },
    )
}

/**
 * An emoji standing in for an icon.
 *
 * Boxed to exactly the square a drawn icon occupies — left to size itself the glyph comes
 * out wider, and the labels of the items using one no longer line up with the rest. The
 * glyph is sized in dp rather than sp so that a larger system font does not push it out of
 * that square while the icons beside it stay as they are.
 */
@Composable
private fun EmojiIcon(emoji: String) {
    Box(
        modifier = Modifier.size(ICON_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = with(LocalDensity.current) { EMOJI_SIZE.toSp() },
        )
    }
}

/** The square a Material icon takes up. */
private val ICON_SIZE = 24.dp

/**
 * Well inside the square. An emoji is a filled colour picture where a Material icon is a
 * thin monochrome outline, so at the same size it weighs down the row it sits in.
 */
private val EMOJI_SIZE = 16.dp
