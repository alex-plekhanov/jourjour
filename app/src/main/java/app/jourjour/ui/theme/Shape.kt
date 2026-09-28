package app.jourjour.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corners are the second thing that ranks a control, after its colour. A field is the
 * squarest, a favourite is rounder, a button is a lozenge — so a favourite reads as
 * something between a place to type and a thing to press, which is what it is.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Text fields, and the pickers that pose as them. */
val FieldShape = RoundedCornerShape(12.dp)

/**
 * Favourites. A chip stands 32dp tall, so 16dp of corner is a full lozenge — the very shape
 * a button has, which left the two telling each other apart by half a tone. Squarer than
 * that, and the tone is no longer carrying the difference on its own.
 */
val ChipShape = RoundedCornerShape(12.dp)
