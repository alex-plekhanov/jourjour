package app.jourjour.core.text

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.TripWalletDetails

/**
 * What a wallet is called.
 *
 * A name a person typed is used as typed. Otherwise it is built from the payment method
 * and the currency — "Cash (US Dollar)" — and built every time rather than stored, so it
 * follows the device language the way the two names it is made of do.
 */
fun walletName(
    context: Context,
    name: String?,
    paymentMethod: PaymentMethod?,
    currency: Currency?,
): String = name?.takeIf { it.isNotBlank() }
    ?: context.getString(
        R.string.wallet_default_name,
        paymentMethod?.let { resolveName(context, it.name, it.nameKey) }.orEmpty(),
        currency?.let { resolveName(context, it.name, it.nameKey) }.orEmpty(),
    )

fun walletName(context: Context, details: TripWalletDetails): String =
    walletName(context, details.wallet.name, details.paymentMethod, details.currency)

/**
 * What the wallet form puts in the name field while nobody has typed a name of their own.
 *
 * It is shown rather than left blank so that the name a wallet will go by is in plain
 * sight — and so that a refusal to take a name already in use points at something. Blank
 * until there is a payment method and a currency to build it from.
 */
fun generatedWalletName(
    context: Context,
    paymentMethod: PaymentMethod?,
    currency: Currency?,
): String = if (paymentMethod == null || currency == null) {
    ""
} else {
    walletName(context, name = null, paymentMethod = paymentMethod, currency = currency)
}

@Composable
fun walletName(details: TripWalletDetails): String {
    val context = LocalContext.current
    return remember(context, details) { walletName(context, details) }
}
