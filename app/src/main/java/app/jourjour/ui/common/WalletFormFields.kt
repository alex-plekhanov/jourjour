package app.jourjour.ui.common

import androidx.compose.runtime.Composable
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod

/**
 * The fields of a wallet, in the order the questions come: what it is paid with, in what
 * money, what it is called and what was in it to begin with.
 *
 * They live apart from any frame around them because the same form is asked for in two
 * shapes. Where a wallet is one thing among many on a form already open — the trip's
 * wallets, the first wallet made from inside an entry — it is a sheet over that form;
 * where it is the whole of what a person came to do, on the wallets tab, it is a screen
 * of its own with Delete in the top bar. Same fields, same rules, one place to change them.
 */
@Composable
fun WalletFormFields(
    form: WalletForm,
    paymentMethods: List<PaymentMethod>,
    currencies: List<Currency>,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onSelectCurrency: (String?) -> Unit,
    onNameChange: (String) -> Unit,
    onBalanceChange: (String) -> Unit,
    /** A wallet that already exists keeps its currency: amounts are written in it. */
    currencyLocked: Boolean = false,
) {
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
}
