package app.jourjour.ui.wallet

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.jourjour.R
import app.jourjour.core.balance.WalletBalances
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.walletName
import app.jourjour.data.entity.BalanceSnapshot
import app.jourjour.data.entity.BalanceSnapshotDetails
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import app.jourjour.repository.BalanceSnapshotRepository
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.PaymentMethodRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.common.Commit
import app.jourjour.ui.common.WalletForm
import app.jourjour.ui.common.WalletFormCheck
import app.jourjour.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * The latest recount of a wallet and how far it was from what should have been left.
 *
 * [expected] is null when it cannot be worked out: a half paid in some other currency
 * moves the wallet by whatever the bank charged, and until that has been written down
 * there is no telling what should have been there.
 */
@Immutable
data class SnapshotSummary(
    val dateTime: LocalDateTime,
    val actual: Money,
    val expected: Money?,
    val notes: String = "",
) {
    val difference: Money? get() = expected?.let { actual - it }
}

/** What a wallet holds now: its opening balance plus income, less expenses. */
@Immutable
data class WalletBalance(
    val wallet: TripWalletDetails,
    val balance: Money,
    /** How many halves are still waiting for the amount the bank charged. */
    val pendingCount: Int = 0,
    val lastSnapshot: SnapshotSummary? = null,
) {
    val currency: Currency get() = wallet.currency

    /**
     * Whether the balance is worth showing. A half paid in another currency moves the
     * wallet by what the bank charged, so until that is known the figure would be a guess
     * dressed up as a total.
     */
    val isKnown: Boolean get() = pendingCount == 0

    /**
     * Whether anything can be reconciled against this wallet. Only a payment method that
     * lets the bank convert is ever charged an amount other than the one entered.
     */
    val canReconcile: Boolean get() = !wallet.paymentMethod.fixedCurrency
}

/** The open recount form of a wallet. */
@Immutable
data class SnapshotSheetState(
    val wallet: TripWalletDetails,
    /** What should have been there, or null while something is waiting to be reconciled. */
    val expected: Money?,
    val actual: String = "",
    val notes: String = "",
    /** What the previous recount of this wallet found, shown above the new one. */
    val last: SnapshotSummary? = null,
) {
    val currency: Currency get() = wallet.currency
}

/** The open wallet form: making a wallet, or changing one the trip already has. */
@Immutable
data class WalletSheetState(
    /** The wallet being changed, or zero while one is being made. */
    val walletId: Long = 0,
    val form: WalletForm = WalletForm(),
) {
    val isNew: Boolean get() = walletId == 0L
}

@Immutable
data class WalletsState(
    val balances: List<WalletBalance> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val isLoading: Boolean = true,
    val snapshotSheet: SnapshotSheetState? = null,
    val walletSheet: WalletSheetState? = null,
)

/**
 * The wallets of a trip: what each holds now, what a recount found, and the forms that
 * make, change and remove them.
 *
 * A balance is never stored. It is the opening balance plus what came in and less what
 * went out, worked out from the entries every time, so that correcting an entry corrects
 * the balance with it.
 */
@HiltViewModel
class WalletsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val snapshotRepository: BalanceSnapshotRepository,
    entryRepository: EntryRepository,
    currencyRepository: CurrencyRepository,
    paymentMethodRepository: PaymentMethodRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    /** What the screen has open on top of the list, as opposed to what the database says. */
    private data class Overlays(
        val snapshotSheet: SnapshotSheetState? = null,
        val walletSheet: WalletSheetState? = null,
    )

    private val overlays = MutableStateFlow(Overlays())

    private val commit = Commit()
    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What the screen has to say after an action: a line for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<WalletsState> = combine(
        tripRepository.observeWallets(tripId),
        entryRepository.observeByTrip(tripId),
        snapshotRepository.observeByTrip(tripId),
        currencyRepository.observeAll(),
        paymentMethodRepository.observeAll(),
        overlays,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val wallets = values[0] as List<TripWalletDetails>
        @Suppress("UNCHECKED_CAST")
        val entries = values[1] as List<EntryDetails>
        @Suppress("UNCHECKED_CAST")
        val snapshots = values[2] as List<BalanceSnapshotDetails>
        @Suppress("UNCHECKED_CAST")
        val currencies = values[3] as List<Currency>
        @Suppress("UNCHECKED_CAST")
        val paymentMethods = values[4] as List<PaymentMethod>
        val open = values[5] as Overlays

        WalletsState(
            balances = balances(wallets, entries, snapshots),
            currencies = currencies,
            paymentMethods = paymentMethods,
            isLoading = false,
            snapshotSheet = open.snapshotSheet,
            walletSheet = open.walletSheet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = WalletsState(),
    )

    private fun balances(
        wallets: List<TripWalletDetails>,
        entries: List<EntryDetails>,
        snapshots: List<BalanceSnapshotDetails>,
    ): List<WalletBalance> {
        val plain = entries.map { it.entry }
        return wallets.map { wallet ->
            val lastSnapshot = snapshots
                .firstOrNull { it.snapshot.walletId == wallet.wallet.id }
                ?.let { details ->
                    // What was expected at that moment is recomputed rather than stored:
                    // entries may have been corrected afterwards, and the discrepancy has to
                    // take that into account.
                    val then = WalletBalances.at(wallet.wallet, plain, details.snapshot.dateTime)
                    SnapshotSummary(
                        dateTime = details.snapshot.dateTime,
                        actual = Money(details.snapshot.amountMilli),
                        expected = then.money.takeIf { then.isKnown },
                        notes = details.snapshot.notes,
                    )
                }

            val now = WalletBalances.at(wallet.wallet, plain)
            WalletBalance(
                wallet = wallet,
                balance = now.money,
                pendingCount = now.pending,
                lastSnapshot = lastSnapshot,
            )
        }
    }

        // region Recounting a wallet

    fun openSnapshotSheet(balance: WalletBalance) {
        overlays.update {
            it.copy(
                snapshotSheet = SnapshotSheetState(
                    wallet = balance.wallet,
                    expected = balance.balance.takeIf { balance.isKnown },
                    // Prefilled with what is expected: most recounts confirm it, and then
                    // all that is left to do is press Save. Nothing to prefill with while
                    // something is waiting to be reconciled.
                    actual = if (balance.isKnown) {
                        MoneyFormat.formatAmountForInput(
                            balance.balance,
                            balance.currency.decimals,
                        )
                    } else {
                        ""
                    },
                    last = balance.lastSnapshot,
                )
            )
        }
    }

    fun closeSnapshotSheet() = overlays.update { it.copy(snapshotSheet = null) }

    fun updateSnapshotActual(value: String) = updateSnapshotSheet { it.copy(actual = value) }

    fun updateSnapshotNotes(value: String) = updateSnapshotSheet { it.copy(notes = value) }

    private fun updateSnapshotSheet(transform: (SnapshotSheetState) -> SnapshotSheetState) =
        overlays.update { it.copy(snapshotSheet = it.snapshotSheet?.let(transform)) }

    fun saveSnapshot() {
        val sheet = overlays.value.snapshotSheet ?: return
        val amount = MoneyFormat.parseAmount(sheet.actual, sheet.currency.decimals) ?: return

        commit.launch(viewModelScope) {
            snapshotRepository.insert(
                BalanceSnapshot(
                    walletId = sheet.wallet.wallet.id,
                    dateTime = LocalDateTime.now(),
                    amountMilli = amount.milli,
                    notes = sheet.notes.trim(),
                )
            )
            closeSnapshotSheet()
        }
    }

    // endregion

    // region Making and changing a wallet

    fun openWalletSheet(balance: WalletBalance? = null) {
        val wallet = balance?.wallet?.wallet
        val fields = if (wallet == null) {
            WalletForm()
        } else {
            WalletForm.of(
                wallet.paymentMethodId,
                wallet.currencyCode,
                wallet.name,
                Money(wallet.openingBalanceMilli),
                balance.currency.decimals,
            )
        }
        val sheet = WalletSheetState(walletId = wallet?.id ?: 0, form = fields.named())
        overlays.update { it.copy(walletSheet = sheet) }
    }

    fun closeWalletSheet() = overlays.update { it.copy(walletSheet = null) }

    fun walletSheetSelectPaymentMethod(paymentMethod: PaymentMethod) =
        updateWalletForm { it.selectPaymentMethod(paymentMethod.id) }

    fun walletSheetSelectCurrency(code: String?) = updateWalletForm { it.selectCurrency(code) }

    fun walletSheetUpdateName(value: String) = updateWalletForm { it.typeName(value) }

    fun walletSheetUpdateBalance(value: String) = updateWalletForm { it.typeOpeningBalance(value) }

    /** Changes the fields, keeping the name in step with the pair until someone types one. */
    private fun updateWalletForm(transform: (WalletForm) -> WalletForm) = overlays.update {
        val sheet = it.walletSheet ?: return@update it
        it.copy(walletSheet = sheet.copy(form = transform(sheet.form).named()))
    }

    private fun WalletForm.named(): WalletForm {
        val dictionaries = state.value
        return withGeneratedName(
            context,
            dictionaries.paymentMethods.firstOrNull { it.id == paymentMethodId },
            dictionaries.currencies.firstOrNull { it.code == currencyCode },
        )
    }

    fun confirmWallet() {
        val current = state.value
        val sheet = current.walletSheet ?: return
        val checked = sheet.form.check(
            context,
            current.paymentMethods.firstOrNull { it.id == sheet.form.paymentMethodId },
            current.currencies.firstOrNull { it.code == sheet.form.currencyCode },
        ) { label ->
            current.balances.any {
                it.wallet.wallet.id != sheet.walletId && walletName(context, it.wallet) == label
            }
        }
        val accepted = when (checked) {
            is WalletFormCheck.Refused -> {
                overlays.update { it.copy(walletSheet = sheet.copy(form = checked.form)) }
                return
            }
            is WalletFormCheck.Accepted -> checked
        }

        val existing = current.balances.firstOrNull { it.wallet.wallet.id == sheet.walletId }
        val wallet = TripWallet(
            id = sheet.walletId,
            tripId = tripId,
            paymentMethodId = accepted.paymentMethodId,
            // A wallet that exists keeps the currency it was created with, whatever the
            // sheet says: entries and recounts are amounts in it.
            currencyCode = existing?.wallet?.wallet?.currencyCode ?: accepted.currencyCode,
            name = accepted.name,
            openingBalanceMilli = accepted.openingBalance.milli,
            lastUsedAt = existing?.wallet?.wallet?.lastUsedAt,
        )

        commit.launch(viewModelScope) {
            try {
                if (sheet.isNew) tripRepository.addWallet(wallet) else {
                    tripRepository.updateWallet(wallet)
                }
                closeWalletSheet()
            } catch (e: Exception) {
                _messages.send(
                    context.getString(R.string.error_save_trip, e.localizedMessage.orEmpty())
                )
            }
        }
    }

    /**
     * Takes away the wallet the form is open on, which is the only place it is offered
     * from: a row of the list has no delete of its own to hit by accident.
     *
     * A wallet an entry points at stays where it is and the form says why — deleting it
     * would cut those entries loose. The form is left open on the refusal, since the
     * wallet is still there to be looked at.
     */
    fun deleteWallet() {
        val sheet = overlays.value.walletSheet ?: return
        val wallet = state.value.balances
            .firstOrNull { it.wallet.wallet.id == sheet.walletId }
            ?.wallet
            ?.wallet
            ?: return
        viewModelScope.launch {
            if (tripRepository.walletIsUsed(wallet.id)) {
                _messages.send(context.getString(R.string.error_wallet_in_use))
                return@launch
            }
            tripRepository.deleteWallet(wallet)
            closeWalletSheet()
        }
    }

    // endregion
}
