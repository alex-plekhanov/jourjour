package app.jourjour.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far a halo spreads out from the letters. Wide enough to pull the text away from
 * whatever is behind it, narrow enough not to read as a box around it.
 */
val HALO_BLUR: Dp = 6.dp

/**
 * The style with a soft glow behind the letters, for text that sits over a photograph.
 *
 * The glow is not a shadow, so it goes nowhere: it spreads evenly around each letter in
 * [color], which should be the colour of the surface the photograph fades into, so that
 * where the picture has faded out the halo is not there to be seen, and where it has not
 * the text is set off against it by a rim of its own background.
 */
@Composable
fun TextStyle.withHalo(color: Color, blur: Dp = HALO_BLUR): TextStyle {
    val radius = with(LocalDensity.current) { blur.toPx() }
    return copy(shadow = Shadow(color = color, offset = Offset.Zero, blurRadius = radius))
}
