package app.jourjour.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The palette is written down rather than taken from the wallpaper. Material's dynamic
 * colour would hand every phone a different scheme, and the four steps this app leans on —
 * button, secondary action, favourite, field — would land at whatever distance from each
 * other that phone's wallpaper happened to give them. A form is read by the weight of its
 * controls, so that distance is a design decision, not a device setting.
 */
private val LightColorScheme = lightColorScheme(
    primary = BlueDeep,
    onPrimary = Color.White,
    primaryContainer = BlueAction,
    onPrimaryContainer = BlueOnAction,
    secondary = BlueMidLight,
    onSecondary = Color.White,
    secondaryContainer = BlueChip,
    onSecondaryContainer = BlueOnChip,
    tertiary = BlueMidLight,
    onTertiary = Color.White,
    tertiaryContainer = BlueChip,
    onTertiaryContainer = BlueOnChip,
    background = BlueSurface,
    onBackground = InkLight,
    surface = BlueSurface,
    onSurface = InkLight,
    surfaceVariant = BlueField,
    onSurfaceVariant = InkMutedLight,
    surfaceBright = BlueSurface,
    surfaceDim = Color(0xFFDDE2E8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F7FC),
    // Fields, the tab bar, menus and sheets all sit on this one: the level the eye reads as
    // "somewhere to put something", as opposed to the page behind it.
    surfaceContainer = BlueField,
    surfaceContainerHigh = Color(0xFFE3EBF5),
    // Out of the blue ramp on purpose — see CardNeutralLight.
    surfaceContainerHighest = CardNeutralLight,
    outline = BlueOutline,
    outlineVariant = BlueOutlineSoft,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = BlueChip,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueDeepDark,
    onPrimary = Color.White,
    primaryContainer = BlueActionDark,
    onPrimaryContainer = BlueOnActionDark,
    secondary = BlueMidDark,
    onSecondary = Color(0xFF16304F),
    secondaryContainer = BlueChipDark,
    onSecondaryContainer = BlueOnChipDark,
    tertiary = BlueMidDark,
    onTertiary = Color(0xFF16304F),
    tertiaryContainer = BlueChipDark,
    onTertiaryContainer = BlueOnChipDark,
    background = BlueSurfaceDark,
    onBackground = InkDark,
    surface = BlueSurfaceDark,
    onSurface = InkDark,
    surfaceVariant = BlueFieldDark,
    onSurfaceVariant = InkMutedDark,
    // Light enough to read as a disc on a card, and as neutral as the card itself.
    surfaceBright = Color(0xFF2B2E33),
    surfaceDim = BlueSurfaceDark,
    surfaceContainerLowest = Color(0xFF0C1016),
    surfaceContainerLow = Color(0xFF151A22),
    surfaceContainer = BlueFieldDark,
    surfaceContainerHigh = Color(0xFF1F2734),
    surfaceContainerHighest = CardNeutralDark,
    outline = BlueOutlineDark,
    outlineVariant = BlueOutlineSoftDark,
    inverseSurface = InkDark,
    inverseOnSurface = InverseSurfaceLight,
    inversePrimary = BlueDeep,
    error = ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun JourJourTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        shapes = Shapes,
        typography = Typography,
        content = content,
    )
}
