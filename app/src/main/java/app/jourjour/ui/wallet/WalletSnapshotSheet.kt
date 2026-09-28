package app.jourjour.ui.wallet

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.Currency
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.rememberDateTimeFormatter
import app.jourjour.ui.common.FormTextField
import app.jourjour.ui.common.MoneyField
import java.time.format.FormatStyle

/**
 * Recounting a wallet: a person counts the cash and puts in how much there actually is.
 * The discrepancy appears at once, so it is clear straight away whether anything needs
 * hunting down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSnapshotSheet(
    sheet: SnapshotSheetState,
    onActualChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actual = MoneyFormat.parseAmount(sheet.actual, sheet.currency.decimals)
    val expected = sheet.expected
    val difference = if (actual != null && expected != null) actual - expected else null

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
            Text(
                text = stringResource(
                    R.string.title_wallet_snapshot,
                    walletName(sheet.wallet),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            sheet.last?.let { LastSnapshot(summary = it, currency = sheet.currency) }

            // With something still waiting to be reconciled there is no telling what should
            // have been there, so the recount is taken as a plain count and nothing is
            // measured against it.
            if (expected == null) {
                Text(
                    text = stringResource(R.string.snapshot_expected_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                SheetRow(
                    labelRes = R.string.snapshot_expected,
                    value = MoneyFormat.formatAmountWithCurrency(expected, sheet.currency),
                )
            }

            MoneyField(
                value = sheet.actual,
                onValueChange = onActualChange,
                currency = sheet.currency,
                labelRes = R.string.field_actual_amount,
                allowNegative = true,
            )

            difference?.let { DifferenceLine(difference = it, currency = sheet.currency) }

            FormTextField(
                value = sheet.notes,
                onValueChange = onNotesChange,
                labelRes = R.string.field_notes,
                singleLine = false,
            )

            PrimaryButton(
                onClick = onSave,
                text = stringResource(R.string.action_save),
                enabled = actual != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}

/**
 * What the previous recount of this wallet found.
 *
 * The discrepancy is not the one that was seen back then: it is recomputed against the
 * entries as they stand now, so an entry corrected or back-dated since shows up here as
 * the recount no longer matching.
 */
@Composable
private fun LastSnapshot(summary: SnapshotSummary, currency: Currency) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(
                R.string.snapshot_last,
                summary.dateTime.format(rememberDateTimeFormatter(FormatStyle.MEDIUM, FormatStyle.SHORT)),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SheetRow(
            labelRes = R.string.snapshot_counted,
            value = MoneyFormat.formatAmountWithCurrency(summary.actual, currency),
        )
        summary.difference?.let { DifferenceLine(difference = it, currency = currency) }
        if (summary.notes.isNotBlank()) {
            Text(
                text = summary.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * How far a count is from what the records say: "matches", or the difference. Green for
 * the one thing a person hopes to see, the colour of an error for anything else.
 */
@Composable
private fun DifferenceLine(difference: Money, currency: Currency) {
    Text(
        text = if (difference.isZero) {
            stringResource(R.string.snapshot_matches)
        } else {
            stringResource(
                R.string.snapshot_difference,
                MoneyFormat.formatAmountWithCurrency(difference, currency),
            )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (difference.isZero) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}

/** A label on the left and its amount on the right. */
@Composable
private fun SheetRow(@StringRes labelRes: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The line under a wallet balance: when the last recount was and what it found. The moment
 * is part of the sentence rather than left out — without it a wallet looks as though it
 * agrees, or disagrees, for ever, when in fact it is one reading from one evening.
 *
 * With nothing to measure the reading against it says only what was counted.
 */
@Composable
fun snapshotSummaryText(summary: SnapshotSummary, currencyDecimals: Int): String {
    val moment = summary.dateTime.format(rememberDateTimeFormatter(FormatStyle.MEDIUM, FormatStyle.SHORT))
    val difference = summary.difference ?: return stringResource(
        R.string.snapshot_summary_counted,
        moment,
        MoneyFormat.formatAmount(summary.actual, currencyDecimals),
    )
    return if (difference.isZero) {
        stringResource(R.string.snapshot_summary_matches, moment)
    } else {
        stringResource(
            R.string.snapshot_summary_difference,
            moment,
            MoneyFormat.formatAmount(difference, currencyDecimals),
        )
    }
}

