package app.jourjour.ui.trip

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.ui.common.CurrencyPickerField
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.MoneyField
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.SecondaryButton
import app.jourjour.ui.common.rememberDateFormatter
import java.time.format.FormatStyle

/**
 * The form of one rate, written as the equation it is: so many units of one currency on
 * the first row, what they come to in the base currency on the second.
 *
 * Laid out like the amount row of an entry — a figure on the left, its currency on the
 * right — because it is the same thing to read, and a form that put the currency
 * elsewhere would have to be read twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateFormSheet(
    @StringRes titleRes: Int,
    @StringRes confirmRes: Int,
    form: RateForm,
    currencies: List<Currency>,
    /** The base currency as the settings have it now, for telling the person it has moved. */
    baseCurrency: String?,
    onSelectCurrency: (String?) -> Unit,
    onMultiplierChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onFetch: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MoneyField(
                    value = form.multiplier,
                    onValueChange = onMultiplierChange,
                    // Whole notes, and no currency: the code stands beside the field, and
                    // a count is not an amount to be settled into two decimal places.
                    currency = null,
                    allowFraction = false,
                    labelRes = R.string.field_rate_multiplier,
                    errorRes = form.multiplierError,
                    modifier = Modifier.weight(1f),
                )
                CurrencyPickerField(
                    selectedCode = form.fromCurrencyCode,
                    currencies = currencies,
                    onSelect = onSelectCurrency,
                    errorRes = form.fromError,
                    modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
                    compact = true,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MoneyField(
                    value = form.rate,
                    onValueChange = onRateChange,
                    // Left as typed, to all three decimals it may carry: unlike an amount
                    // a rate is not rounded to the shape of the currency it is written in.
                    currency = null,
                    labelRes = R.string.field_rate_value,
                    errorRes = form.rateError,
                    modifier = Modifier.weight(1f),
                )
                CurrencyPickerField(
                    selectedCode = form.toCurrencyCode,
                    currencies = currencies,
                    onSelect = {},
                    // Named for what it is rather than repeating "currency": the label is
                    // what says why this half of the row cannot be touched.
                    labelRes = R.string.field_base_currency_compact,
                    // Read, not chosen: a rate points at the base currency it was made
                    // against, and moving that end would change what the figure means.
                    enabled = false,
                    modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
                    compact = true,
                )
            }

            if (baseCurrency != null && baseCurrency != form.toCurrencyCode) {
                Text(
                    text = stringResource(
                        R.string.warning_rate_other_base,
                        form.toCurrencyCode,
                        baseCurrency,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SecondaryButton(
                onClick = onFetch,
                text = stringResource(R.string.action_fetch_rate),
                iconPainter = painterResource(R.drawable.ic_download),
                enabled = form.canFetch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            when {
                form.isFetching -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.hint_fetching_rate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                form.lookupError != null -> Text(
                    text = stringResource(form.lookupError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                // Which day the figure belongs to, because it is often not the day that
                // was asked for: the source publishes once a day, on working days.
                form.fetchedDate != null -> Text(
                    text = stringResource(
                        R.string.hint_fetched_rate_date,
                        rememberDateFormatter(FormatStyle.MEDIUM).format(form.fetchedDate),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Under the button that fills it in, because that is often what does: a
            // lookup writes the day its figure belongs to here, where the trip's form
            // will show it, rather than on a sheet nobody sees again.
            FormTextField(
                value = form.note,
                onValueChange = onNoteChange,
                labelRes = R.string.field_rate_note,
                singleLine = false,
            )

            PrimaryButton(
                onClick = onConfirm,
                text = stringResource(confirmRes),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}

/** As wide as the currency column of the entry form, which this row is modelled on. */
private val CURRENCY_FIELD_WIDTH = 112.dp
