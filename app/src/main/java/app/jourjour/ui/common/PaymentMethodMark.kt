package app.jourjour.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.data.entity.PaymentMethod

/**
 * How much room a way of paying takes beside a figure.
 *
 * Wide enough for an emoji and the same whether there is one or not, which is the whole
 * point of it: it keeps the figures of a column ending at one place.
 */
val METHOD_MARK_WIDTH = 20.dp

/**
 * What stands for the money nobody has said how to pay: a question mark where an icon
 * goes. Built from its code point so that the source of this file stays plain.
 */
val UNSET_METHOD_MARK: String = Char(0x2753).toString()

/**
 * The way of paying, to the right of what it belongs to — or the room it would have taken,
 * where nobody has said.
 *
 * The room is kept either way so that the figures above and below line up: a column of
 * amounts where some have an icon after them and some end flush is a column read twice.
 * Unscaled, as the tab captions are: it is a mark beside a figure and not something
 * anybody reads, and grown half again it would push the figures about.
 */
@Composable
fun PaymentMethodMark(
    method: PaymentMethod?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    UnscaledText {
        Text(
            text = method?.icon.orEmpty(),
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = modifier
                .padding(start = 4.dp)
                .width(METHOD_MARK_WIDTH),
        )
    }
}
