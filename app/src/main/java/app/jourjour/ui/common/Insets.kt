package app.jourjour.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable

/**
 * What a screen with a text field pads itself by, so that it ends above the keyboard.
 *
 * The window is drawn edge to edge, so the system no longer shrinks it for the keyboard,
 * and the screen has to. The whole of the keyboard's inset, navigation bar included: the
 * keyboard covers that bar, and a screen that kept room for it would leave a band of
 * nothing at the foot of the keyboard.
 *
 * Nothing under this pads for the navigation bar twice, either. Padding by an inset
 * consumes it, so while the keyboard is up the bar the save button sits in asks for the
 * navigation bar and is told there is none left to make room for; once the keyboard is
 * down there is nothing consumed and it gets the whole of it, as it always did.
 */
@Composable
fun keyboardInsets(): WindowInsets = WindowInsets.ime
