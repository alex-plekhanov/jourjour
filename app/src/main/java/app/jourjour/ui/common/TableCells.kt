package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A figure in a column of figures: set against the right edge, on one line, and shrunk
 * rather than broken.
 *
 * The columns of these tables are shares of the width, so a larger system font does not
 * widen them: the text inside is what has to give. Broken across two lines a sum stops
 * being a number at all, and a table where one row is taller than the rest is worse than a
 * table set a point smaller.
 */
@Composable
fun RowScope.NumberCell(
    text: String,
    width: Float,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = weight,
        textAlign = TextAlign.End,
        maxLines = 1,
        autoSize = fitsTheColumn(MaterialTheme.typography.bodyMedium.fontSize),
        modifier = Modifier.weight(width),
    )
}

/** The heading of such a column, which has to fit the same width. */
@Composable
fun RowScope.ColumnTitle(@StringRes titleRes: Int, width: Float) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        autoSize = fitsTheColumn(MaterialTheme.typography.labelSmall.fontSize),
        modifier = Modifier.weight(width),
    )
}

/**
 * Lets a cell shrink rather than break.
 *
 * It only ever shrinks — the ceiling is the size the style asks for, which is the size
 * every cell keeps at the font the phone is normally set to.
 */
@Composable
private fun fitsTheColumn(ceiling: TextUnit): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = SMALLEST_READABLE, maxFontSize = ceiling)

/** How small a figure may be set before it stops being worth setting at all. */
private val SMALLEST_READABLE = 8.sp
