package app.jourjour.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable

/**
 * What a screen with a text field pads itself by, so that it ends above the keyboard.
 *
 * The window is drawn edge to edge, so the system no longer shrinks it for the keyboard,
 * and the screen has to. The navigation bar is left out of the sum: whatever sits at the
 * foot of the screen — a tab bar, a save bar, the scaffold's own content — already makes
 * room for it, and would otherwise do so twice, leaving a blank band above the keyboard.
 */
@Composable
fun keyboardInsets(): WindowInsets = WindowInsets.ime.exclude(WindowInsets.navigationBars)
