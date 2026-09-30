package app.jourjour.ui.common

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod

/**
 * The same form of a wallet over the whole screen, for the tab where a wallet is the
 * thing being worked on rather than a detail of something else.
 *
 * It is a screen over the tab rather than a destination of its own — the way the form of
 * an event is — so Back is wired here: shutting it is what Back means while it is open.
 * Delete sits in the top bar, away from the button people came to press, as on every
 * other form; it is what the list used to carry beside every row.
 */
@Composable
fun WalletFormScreen(
    @StringRes titleRes: Int,
    form: WalletForm,
    paymentMethods: List<PaymentMethod>,
    currencies: List<Currency>,
    snackbarHostState: SnackbarHostState,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onSelectCurrency: (String?) -> Unit,
    onNameChange: (String) -> Unit,
    onBalanceChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    /** null while the wallet is being made: there is nothing to delete yet. */
    onDelete: (() -> Unit)? = null,
    currencyLocked: Boolean = false,
) {
    BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize()) {
        EditorScaffold(
            title = stringResource(titleRes),
            onBack = onClose,
            onSave = onSave,
            snackbarHostState = snackbarHostState,
            onDelete = onDelete,
        ) {
            WalletFormFields(
                form = form,
                paymentMethods = paymentMethods,
                currencies = currencies,
                onSelectPaymentMethod = onSelectPaymentMethod,
                onSelectCurrency = onSelectCurrency,
                onNameChange = onNameChange,
                onBalanceChange = onBalanceChange,
                currencyLocked = currencyLocked,
            )
        }
    }
}
