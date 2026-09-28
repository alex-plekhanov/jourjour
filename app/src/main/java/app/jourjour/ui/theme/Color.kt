package app.jourjour.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One blue, taken down four steps of lightness. The step a thing sits on says how loudly it
 * asks to be pressed: a filled button is the darkest, then a secondary action, then a
 * favourite, then the field a number is typed into, which asks nothing at all.
 *
 * Written out rather than derived, because the four have to stay in that order by eye and a
 * generated ramp keeps sliding out of it.
 */

// Light theme.
val BlueDeep = Color(0xFF1F3D70)
val BlueAction = Color(0xFFC3D5EE)
val BlueOnAction = Color(0xFF10294C)
val BlueChip = Color(0xFFD8E4F5)
val BlueOnChip = Color(0xFF1B3255)
val BlueField = Color(0xFFEAF1FA)
val BlueSurface = Color(0xFFFBFCFE)
val BlueOutline = Color(0xFF7C8A9C)
val BlueOutlineSoft = Color(0xFFBCC8D8)
val InkLight = Color(0xFF191C20)

/**
 * Cards carry photographs, and a tinted card turns a faded picture blue. Theirs is the one
 * neutral in the palette.
 */
val CardNeutralLight = Color(0xFFE6E7EA)

// Dark theme. The filled button keeps its blue instead of turning pale the way Material's
// own dark palettes do: a light bar across the foot of every form glares, and the colour is
// the one thing that says "this is the button that finishes the job".
val BlueDeepDark = Color(0xFF3A62A8)
val BlueActionDark = Color(0xFF2B3B55)
val BlueOnActionDark = Color(0xFFD3E1F6)
val BlueChipDark = Color(0xFF223047)
val BlueOnChipDark = Color(0xFFC6D6EE)
val BlueFieldDark = Color(0xFF1A212C)
val BlueSurfaceDark = Color(0xFF11151B)
val BlueOutlineDark = Color(0xFF8A95A5)
val BlueOutlineSoftDark = Color(0xFF39414E)
val InkDark = Color(0xFFE2E5EA)

val CardNeutralDark = Color(0xFF1D1F22)

// Text that explains rather than names: labels, hints, the dates under a trip.
val InkMutedLight = Color(0xFF49545F)
val InkMutedDark = Color(0xFFA6B0BD)

// The mid tone of the same blue, for what Material paints in the secondary and tertiary
// roles. Named here only so that no component can fall back on the purple baseline.
val BlueMidLight = Color(0xFF4A6389)
val BlueMidDark = Color(0xFF9FB5D4)

// A snackbar is drawn on the opposite of the surface it covers.
val InverseSurfaceLight = Color(0xFF2E3238)
val InverseOnSurfaceLight = Color(0xFFF0F2F5)

// Red is the only other hue in the app: an amount that did not come out, a recount that did
// not agree, a photo about to be deleted.
val ErrorLight = Color(0xFFB3261E)
val ErrorContainerLight = Color(0xFFF9DEDC)
val ErrorDark = Color(0xFFF2B8B5)

// As muted as the secondary action beside it: a destructive button has the weight of the
// actions in its row and a colour that says what it does, and Material's own dark error
// container is saturated enough to read as the filled button, only red.
val ErrorContainerDark = Color(0xFF4F2C2B)
