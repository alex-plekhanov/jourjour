package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.data.entity.Currency
import app.jourjour.ui.theme.FieldShape

/**
 * Entering an amount of money. The value stays a string while it is being typed: "12,"
 * and "-" are halfway states that are not numbers yet. Turning the string into
 * [Money] is the view model's job at save time.
 */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: Currency?,
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    @StringRes errorRes: Int? = null,
    /**
     * Marked wrong without a word under it. A message pushes whatever hangs on the field's
     * lower border down; where the form says what is wrong at its foot, the field only
     * turns red.
     */
    isError: Boolean = errorRes != null,
    allowNegative: Boolean = false,
    /**
     * Off where the number counts whole things rather than measuring an amount: a rate
     * quoted per 100 units is a hundred notes, and a hundredth of a note is not a figure
     * anybody means to type.
     */
    allowFraction: Boolean = true,
    /** Off where the currency is named beside the field and the symbol would repeat it. */
    showSymbol: Boolean = true,
    /**
     * What the field means while nothing is typed into it: the figure it stands in for,
     * written where that figure would go and in the grey of something nobody has said.
     *
     * It is the field's own text rather than Material's placeholder, which shows only
     * while the field has the cursor — the opposite of what this is for. Standing there
     * it also keeps the label up on the border where a filled field keeps it, so a field
     * with a default reads as a field with an answer in it, which it has.
     *
     * The cursor takes it away: an empty field is what somebody who has come to type a
     * figure wants, not one they have to clear first.
     */
    hint: String? = null,
    /** Square an edge to join this field to the one above or below it. */
    shape: Shape = FieldShape,
    /**
     * The gap that separates one field from the one stacked above it. Off in a table row,
     * where nothing is stacked above and the gap only pushes the field off centre.
     */
    spacedAbove: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val showingHint = hint != null && value.isBlank() && !focused
    OutlinedTextField(
        value = if (showingHint) requireNotNull(hint) else value,
        onValueChange = { onValueChange(sanitize(it, allowNegative, allowFraction)) },
        label = { FieldLabel(stringResource(labelRes)) },
        suffix = currency?.takeIf { showSymbol }?.let { { Text(it.symbol) } },
        isError = isError,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowFraction) KeyboardType.Decimal else KeyboardType.Number,
        ),
        singleLine = true,
        // Amounts line up on the right, where their last digits can be read against each
        // other, the way they are shown everywhere else in the app. A hint standing in
        // for a figure nobody typed is set in the grey of everything unsaid.
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.End,
            color = if (showingHint) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        ),
        shape = shape,
        colors = formFieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (spacedAbove) FIELD_TOP_SPACING else 0.dp)
            .onFocusChanged { focus ->
                focused = focus.isFocused
                // Leaving the field settles what was typed into the shape the currency
                // has: "5" becomes 5.00 in dollars and stays 5 in yen. Not while typing —
                // a separator the person has just entered would be taken away again.
                if (focus.isFocused || currency == null) return@onFocusChanged
                val amount = MoneyFormat.parseAmount(value, currency.decimals) ?: return@onFocusChanged
                val settled = MoneyFormat.formatAmountForInput(amount, currency.decimals)
                if (settled != value) onValueChange(settled)
            },
    )
}

/** Keeps digits, one decimal separator and a leading minus. */
private fun sanitize(
    input: String,
    allowNegative: Boolean,
    allowFraction: Boolean,
): String = buildString {
    var separatorSeen = false
    input.forEachIndexed { index, ch ->
        when {
            ch.isDigit() -> append(ch)
            (ch == '.' || ch == ',') && allowFraction && !separatorSeen -> {
                separatorSeen = true
                append(ch)
            }
            ch == '-' && allowNegative && index == 0 -> append(ch)
        }
    }
}

/** The gap a stacked field leaves above itself, the same for every form. */
private val FIELD_TOP_SPACING = 8.dp
