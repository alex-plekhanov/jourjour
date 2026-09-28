package app.jourjour.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.NoBaseCurrencyWarning
import app.jourjour.ui.common.WhyNoTotal

/** Wide enough for a three-letter code and the arrow that opens the picker. */
private val CURRENCY_FIELD_WIDTH = 112.dp

/**
 * So much of one currency in another, through the rates of this trip.
 *
 * It lives in the budget's menu for want of a better place — it belongs to no one tab —
 * and it says at the top whose rates it is using: these are figures a person entered for
 * their own reckoning, and reading them as the money changer's would be a mistake worth
 * making once.
 *
 * All four fields are live. Typing into either amount works the other one out; choosing
 * either currency leaves the amount beside it alone and works the far one out again, since
 * the figure just typed is the one that was meant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterSheetContent(
    converter: ConverterSheet,
    viewModel: BudgetViewModel,
    /** Where rates are entered, for the line that says there are none. */
    onEditTrip: () -> Unit,
    /** Where the base currency is chosen, for the same reason. */
    onSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeConverter,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.title_converter),
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                converter.base == null -> NoBaseCurrencyWarning(onSettings)

                converter.currencies.size < 2 -> {
                    val linkWord = stringResource(R.string.link_trip_form)
                    WhyNoTotal(
                        sentence = stringResource(R.string.warning_no_rates_at_all, linkWord),
                        linkWord = linkWord,
                        colour = MaterialTheme.colorScheme.onSurfaceVariant,
                        onFollow = onEditTrip,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.hint_converter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    AmountRow(
                        amount = converter.from,
                        onAmountChange = viewModel::converterUpdateFrom,
                        code = converter.fromCode,
                        onSelect = viewModel::converterSelectFrom,
                        converter = converter,
                    )
                    AmountRow(
                        amount = converter.to,
                        onAmountChange = viewModel::converterUpdateTo,
                        code = converter.toCode,
                        onSelect = viewModel::converterSelectTo,
                        converter = converter,
                    )
                }
            }
        }
    }
}

/** One side of the conversion: the figure, and the money it is in. */
@Composable
private fun AmountRow(
    amount: String,
    onAmountChange: (String) -> Unit,
    code: String?,
    onSelect: (String?) -> Unit,
    converter: ConverterSheet,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoneyField(
            value = amount,
            onValueChange = onAmountChange,
            currency = converter.currencies.firstOrNull { it.code == code },
            labelRes = R.string.field_amount,
            modifier = Modifier.weight(1f),
            showSymbol = false,
        )
        CurrencyPickerField(
            selectedCode = code,
            // Only what this trip can convert: everything it holds a rate for, and the
            // base currency those rates point at.
            currencies = converter.currencies,
            onSelect = onSelect,
            modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
            compact = true,
        )
    }
}
