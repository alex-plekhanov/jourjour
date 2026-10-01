package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * What keeps one column of figures off the next.
 *
 * Without it the columns merely touch, which is invisible while the figures are shorter
 * than their columns and reads as one long number the moment they are not: "300 000,00"
 * and "220 614,67" printed end to end.
 */
val FIGURE_GUTTER = 8.dp

/**
 * A figure in a column of figures: set against the right edge, on one line, and shrunk
 * rather than broken.
 *
 * The columns of these tables are shares of the width, so a larger system font does not
 * widen them: the text inside is what has to give. Broken across two lines a sum stops
 * being a number at all, and a table where one row is taller than the rest is worse than a
 * table set a point smaller.
 *
 * The shrinking is for the last point or two. Where it would take more than that, the
 * table stops being a table instead — see [figuresFit].
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
 * One figure of a table that has stopped being one: under the name of the row it belongs
 * to, with the column's heading in front of it instead of over it.
 *
 * Set at the size everything else is. The whole point of giving up the columns is that
 * nothing has to be squeezed to fit them.
 */
@Composable
fun FigureLine(
    @StringRes titleRes: Int,
    text: String,
    weight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = STACKED_INSET, top = 2.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A column of a table of figures: its share of the width, and everything it will print.
 *
 * The headings count as things it prints. A column too narrow for the word over it is as
 * broken as one too narrow for the sums under it.
 */
class FigureColumn(val weight: Float, val texts: List<String>)

/**
 * Whether the table still fits across the width it is given, at the size the reader has
 * asked for.
 *
 * The columns are shares of the width, so a larger system font does not widen them. A
 * little over, a cell shrinks; far over — which is where anybody who has turned the font
 * up finds themselves, since the figures grow and their columns do not — shrinking would
 * be answering a request for larger text with smaller, and the figures would end up
 * smaller than the words beside them. So the table gives up its columns instead, and each
 * row becomes its name with its figures listed under it, every one of them at full size.
 *
 * @param available the width the rows are laid out in, margins already taken off.
 * @param nameWeight the share the leftmost column takes, which prints no figures.
 */
@Composable
fun figuresFit(
    columns: List<FigureColumn>,
    nameWeight: Float,
    available: Dp,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val total = nameWeight + columns.sumOf { it.weight.toDouble() }.toFloat()
    val forFigures = available - FIGURE_GUTTER * columns.size
    return columns.all { column ->
        val width = with(density) { (forFigures * (column.weight / total)).toPx() }
        column.texts.all { text ->
            text.isBlank() || measurer.measure(text, style).size.width <= width
        }
    }
}

/**
 * Lets a cell shrink rather than break.
 *
 * It only ever shrinks — the ceiling is the size the style asks for, which is the size
 * every cell keeps at the font the phone is normally set to.
 */
@Composable
private fun fitsTheColumn(ceiling: TextUnit): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = smallestReadable(), maxFontSize = ceiling)

/**
 * How small a figure may be set before it stops being worth setting at all.
 *
 * A length on the glass rather than a size of text: a floor written in `sp` would be
 * raised by the very setting it is there to make room for, and at a large font a cell
 * would run out of room to shrink into and print its figure cut off instead.
 */
@Composable
private fun smallestReadable(): TextUnit = with(LocalDensity.current) { 8.dp.toSp() }

/** How far a figure listed under a name is set in from it. */
private val STACKED_INSET = 16.dp
