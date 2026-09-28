package app.jourjour.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.generatedWalletName
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod

/**
 * The fields of the wallet form, whichever screen opened it.
 *
 * Three do — the trip form, where wallets are set up in advance; the wallets tab, where
 * they are changed; and the entry form, where the first one is often made on the spot —
 * and each keeps only what is its own around this: which half asked for it, which draft
 * or which row is being changed. The fields, the name that follows the pair, and the
 * check before the wallet is written are the same everywhere.
 */
@Immutable
data class WalletForm(
    val paymentMethodId: Long? = null,
    val currencyCode: String? = null,
    val name: String = "",
    /**
     * Whether the name is still the one built from the payment method and the currency.
     * While it is, the field follows them; once a person types their own it stops, and
     * the wallet is saved with the typed name rather than with none.
     */
    val nameIsAuto: Boolean = true,
    val openingBalance: String = "",
    @param:StringRes val paymentMethodError: Int? = null,
    @param:StringRes val currencyError: Int? = null,
    @param:StringRes val nameError: Int? = null,
) {
    fun selectPaymentMethod(id: Long): WalletForm =
        copy(paymentMethodId = id, paymentMethodError = null, nameError = null)

    fun selectCurrency(code: String?): WalletForm =
        copy(currencyCode = code, currencyError = null, nameError = null)

    fun typeName(value: String): WalletForm = copy(name = value, nameIsAuto = false, nameError = null)

    fun typeOpeningBalance(value: String): WalletForm = copy(openingBalance = value)

    /** Keeps the name in step with the pair it is built from, until someone types one. */
    fun withGeneratedName(context: Context, paymentMethod: PaymentMethod?, currency: Currency?): WalletForm =
        if (nameIsAuto) copy(name = generatedWalletName(context, paymentMethod, currency)) else this

    /**
     * The name to store: the typed one, or none at all, so that an untouched name goes on
     * being built from the pair and goes on following the device language.
     */
    val typedName: String? get() = name.trim().takeIf { !nameIsAuto && it.isNotEmpty() }

    /** What a wallet with these fields is called: the typed name, or the one the pair gives. */
    fun label(context: Context, paymentMethod: PaymentMethod?, currency: Currency?): String =
        walletName(context, typedName, paymentMethod, currency)

    /**
     * Checks the form. The pair has to be chosen, and the name it comes to must not be one
     * the trip already has — what "already has" means differs, so the caller answers
     * [isNameTaken] for the label: the trip's wallets, its drafts less the one being
     * edited, its rows less the one being changed.
     */
    fun check(
        context: Context,
        paymentMethod: PaymentMethod?,
        currency: Currency?,
        isNameTaken: (label: String) -> Boolean,
    ): WalletFormCheck {
        val paymentMethodId = paymentMethodId
        val currencyCode = currencyCode
        if (paymentMethodId == null || currencyCode == null) {
            return WalletFormCheck.Refused(
                copy(
                    paymentMethodError = R.string.error_payment_method_required
                        .takeIf { paymentMethodId == null },
                    currencyError = R.string.error_currency_required.takeIf { currencyCode == null },
                )
            )
        }
        if (isNameTaken(label(context, paymentMethod, currency))) {
            // The error belongs in the sheet itself: a snackbar would sit behind it, and
            // the refusal would look like a button that simply did nothing.
            return WalletFormCheck.Refused(copy(nameError = R.string.error_wallet_name_taken))
        }
        val decimals = currency?.decimals ?: DEFAULT_DECIMALS
        return WalletFormCheck.Accepted(
            paymentMethodId = paymentMethodId,
            currencyCode = currencyCode,
            name = typedName,
            openingBalance = MoneyFormat.parseAmount(openingBalance, decimals) ?: Money.ZERO,
        )
    }

    companion object {
        private const val DEFAULT_DECIMALS = 2

        /** The form of a wallet that exists, with its balance written the way a field takes it. */
        fun of(
            paymentMethodId: Long,
            currencyCode: String,
            name: String?,
            openingBalance: Money,
            decimals: Int,
        ): WalletForm = WalletForm(
            paymentMethodId = paymentMethodId,
            currencyCode = currencyCode,
            name = name.orEmpty(),
            // A wallet saved without a name of its own goes on taking one from its pair.
            nameIsAuto = name == null,
            openingBalance = MoneyFormat.formatAmountForInput(openingBalance, decimals),
        )
    }
}

/** How the form came out of its check. */
sealed interface WalletFormCheck {
    /** Something is missing or taken; the form says what, field by field. */
    data class Refused(val form: WalletForm) : WalletFormCheck

    /** The wallet as it is to be written. */
    data class Accepted(
        val paymentMethodId: Long,
        val currencyCode: String,
        /** Null for a name that was never typed. */
        val name: String?,
        val openingBalance: Money,
    ) : WalletFormCheck
}
