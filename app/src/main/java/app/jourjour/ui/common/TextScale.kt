package app.jourjour.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Draws what is inside at the size it was designed at, whatever the device's font size is
 * set to. Everything measured in dp is untouched; only text stops growing.
 *
 * This is for the few words that label something rather than say it — the caption under a
 * tab's icon, the name floating over a field. They are read once, by somebody working out
 * where things are, and from then on the icon or the field's own contents is what is being
 * looked at. Grown by half again they are the one thing that breaks these layouts: a tab
 * caption wraps onto a second line and shoves the bar into the list, a field's label runs
 * out of the border it floats on.
 *
 * It is deliberately not used for anything a person actually reads — a name, an amount, a
 * note, a date, the text of a form. Those grow with the setting, which is the point of the
 * setting.
 */
@Composable
fun UnscaledText(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale == 1f) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = 1f),
        content = content,
    )
}
