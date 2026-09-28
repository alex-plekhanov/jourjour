package app.jourjour.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod

/**
 * The form of a wallet: a payment method, a currency, a name and what it started with.
 *
 * It is reached from two places — the trip form, where wallets are set up in advance, and
 * the entry form, where the first one is often made on the spot — so it lives here rather
 * than in either of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletFormSheet(
    @StringRes titleRes: Int,
    @StringRes confirmRes: Int,
    form: WalletForm,
    paymentMethods: List<PaymentMethod>,
    currencies: List<Currency>,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onSelectCurrency: (String?) -> Unit,
    onNameChange: (String) -> Unit,
    onBalanceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** A wallet that already exists keeps its currency: amounts are written in it. */
    currencyLocked: Boolean = false,
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
            PaymentMethodPickerField(
                selectedId = form.paymentMethodId,
                paymentMethods = paymentMethods,
                onSelect = onSelectPaymentMethod,
                errorRes = form.paymentMethodError,
            )
            CurrencyPickerField(
                selectedCode = form.currencyCode,
                currencies = currencies,
                onSelect = onSelectCurrency,
                errorRes = form.currencyError,
                hintRes = if (currencyLocked) R.string.hint_wallet_currency_locked else null,
                enabled = !currencyLocked,
            )
            FormTextField(
                value = form.name,
                onValueChange = onNameChange,
                labelRes = R.string.field_wallet_name,
                errorRes = form.nameError,
            )
            MoneyField(
                value = form.openingBalance,
                onValueChange = onBalanceChange,
                currency = currencies.firstOrNull { it.code == form.currencyCode },
                labelRes = R.string.field_opening_balance,
                allowNegative = true,
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
