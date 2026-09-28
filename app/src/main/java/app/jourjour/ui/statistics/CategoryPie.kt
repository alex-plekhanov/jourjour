package app.jourjour.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.core.stats.CategoryTotals
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.Category
import kotlin.math.roundToInt

/**
 * What the trip was spent on, as shares of one circle.
 *
 * Expenses only, and [categories] arrives holding only what was spent. A pie divides a
 * whole into its parts, and expenses and incomes are not parts of one whole — netting them
 * would make a category that was paid and refunded disappear, and setting them side by side
 * would draw a circle that is not a total of anything. What came in is in the tables above,
 * where it can be read as itself.
 *
 * The slices go largest first, unlike the tables, which keep the order of the dictionary.
 * A table is read to look a category up; a chart is read to see which of them is the big
 * one, and that is only a glance if the big one starts at twelve o'clock.
 */
@Composable
fun CategoryPie(categories: List<CategoryTotals>, modifier: Modifier = Modifier) {
    val spent = categories.sortedByDescending { it.expense.milli }
    val total = spent.fold(0L) { sum, it -> sum + it.expense.milli }
    if (total <= 0) return

    val slices = spent.mapIndexed { index, totals ->
        Slice(
            category = totals.category,
            share = totals.expense.milli.toFloat() / total,
            color = sliceColor(index, spent.size),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Pie(slices)
        Legend(slices)
    }
}

/** One category's share of the circle. */
private data class Slice(val category: Category, val share: Float, val color: Color)

@Composable
private fun Pie(slices: List<Slice>) {
    Canvas(modifier = Modifier.size(PIE_SIZE)) {
        // From twelve o'clock and clockwise, which is where a reader starts a dial.
        var start = -90f
        slices.forEach { slice ->
            val sweep = slice.share * FULL_CIRCLE
            drawArc(
                color = slice.color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
            )
            start += sweep
        }
    }
}

/**
 * Which colour was which, and what each came to as a share.
 *
 * The share and not the amount: the amount is in the table above, and repeating it here
 * would make a second table out of what is meant to be a key to the picture.
 */
@Composable
private fun Legend(slices: List<Slice>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(slice.color)
                Text(
                    text = "${slice.category.icon} " +
                        localizedName(slice.category.name, slice.category.nameKey),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = "${(slice.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** The swatch that ties a line of the legend to a slice of the circle. */
@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(RoundedCornerShape(SWATCH_CORNER))
            .background(color),
    )
}

/**
 * A colour per slice, spaced evenly round the wheel from the app's own blue.
 *
 * Generated rather than listed, because the number of categories a trip uses is not known
 * in advance and a list would either run out or leave two neighbours the same colour. One
 * saturation and one brightness throughout, so that the slices read as a set rather than
 * as a rainbow, and mid-toned so that the same palette carries on a light ground and on a
 * dark one.
 */
private fun sliceColor(index: Int, count: Int): Color =
    Color.hsv(
        hue = (PRIMARY_HUE + index * FULL_CIRCLE / count) % FULL_CIRCLE,
        saturation = SLICE_SATURATION,
        value = SLICE_VALUE,
    )

/** The hue of the app's primary blue, so the chart starts in the app's own colour. */
private const val PRIMARY_HUE = 218f
private const val FULL_CIRCLE = 360f
private const val SLICE_SATURATION = 0.55f
private const val SLICE_VALUE = 0.80f

private val PIE_SIZE = 200.dp
private val SWATCH_SIZE = 14.dp
private val SWATCH_CORNER = 3.dp
