package app.jourjour.ui.reconcile

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.EntryDetails
import app.jourjour.repository.CurrencyRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.common.Commit
import app.jourjour.ui.common.EditorEvent
import app.jourjour.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.TripWalletDetails
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.TripRepository
import java.time.LocalDateTime
import javax.inject.Inject

/** One operation waiting for the amount the banking app will report. */
@Immutable
data class SettlementRow(
    val entryId: Long,
    /** The expense half of an entry or the income one: a refund needs reconciling too. */
    val isExpense: Boolean,
    val dateTime: LocalDateTime,
    val title: String,
    val spent: Money,
    val spentCurrency: Currency,
    val accountCurrency: Currency,
    val settlement: String,
) {
    val key: String get() = key(entryId, isExpense)

    companion object {
        fun key(entryId: Long, isExpense: Boolean): String =
            "${if (isExpense) "e" else "i"}:$entryId"
    }
}

@Immutable
data class ReconcileState(
    val wallet: TripWalletDetails? = null,
    val rows: List<SettlementRow> = emptyList(),
    val isLoading: Boolean = true,
    val hasChanges: Boolean = false,
) {
    val accountCurrency: Currency? get() = wallet?.currency

    /** How much the account has been charged so far; income offsets the charges. */
    val settledTotal: Money
        get() = rows.fold(Money.ZERO) { sum, row ->
            val amount = MoneyFormat.parseAmount(row.settlement, row.accountCurrency.decimals)
                ?: Money.ZERO
            if (row.isExpense) sum + amount else sum - amount
        }

    val missingCount: Int get() = rows.count { it.settlement.isBlank() }

    /** Spent in the local currencies, each on its own: nothing can add them together. */
    val spentTotals: List<Pair<Money, Currency>>
        get() = rows
            .groupBy { it.spentCurrency.code }
            .map { (_, currencyRows) ->
                val total = currencyRows.fold(Money.ZERO) { sum, row ->
                    if (row.isExpense) sum + row.spent else sum - row.spent
                }
                total to currencyRows.first().spentCurrency
            }
}

/**
 * Reconciling what the account behind one wallet was charged.
 *
 * A bank charges its account in the wallet's own currency and does not report the amount
 * straight away, so an entry leaves it blank and it is filled in later, in bulk, once the
 * operations show up in the banking app. Only the halves paid in some other currency land
 * here: everything else was charged the very amount that was entered.
 */
@HiltViewModel
class ReconcileViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val entryRepository: EntryRepository,
    tripRepository: TripRepository,
    currencyRepository: CurrencyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val walletId: Long = savedStateHandle.toRoute<Route.Reconcile>().walletId

    /** Typed but not yet saved, keyed by [SettlementRow.key]. */
    private val drafts = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * The trip is reached through the wallet rather than carried in the route: a wallet
     * belongs to exactly one trip, and passing both would let the two disagree.
     */
    val state: StateFlow<ReconcileState> = tripRepository.observeWallet(walletId)
        .flatMapLatest { wallet ->
            if (wallet == null) {
                flowOf(ReconcileState(isLoading = false))
            } else {
                combine(
                    entryRepository.observeByTrip(wallet.wallet.tripId),
                    currencyRepository.observeAll(),
                    drafts,
                ) { entries, currencies, drafts ->
                    ReconcileState(
                        wallet = wallet,
                        rows = rows(wallet, entries, currencies, drafts),
                        isLoading = false,
                        hasChanges = drafts.isNotEmpty(),
                    )
                }
            }
        }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ReconcileState(),
    )

    private val commit = Commit()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private fun rows(
        wallet: TripWalletDetails,
        entries: List<EntryDetails>,
        currencies: List<Currency>,
        drafts: Map<String, String>,
    ): List<SettlementRow> = entries
        .flatMap { details -> settlementRows(details, currencies, drafts) }
        .filter { (rowWallet, _) -> rowWallet.wallet.id == wallet.wallet.id }
        .map { it.second }

    private fun settlementRows(
        details: EntryDetails,
        currencies: List<Currency>,
        drafts: Map<String, String>,
    ): List<Pair<TripWalletDetails, SettlementRow>> {
        val entry = details.entry

        fun row(
            isExpense: Boolean,
            wallet: TripWalletDetails?,
            amountMilli: Long?,
            spentCurrency: Currency?,
            settlementMilli: Long?,
        ): Pair<TripWalletDetails, SettlementRow>? {
            if (wallet == null || amountMilli == null || spentCurrency == null) return null
            // Nothing to reconcile when payment was made in the wallet's own currency.
            if (spentCurrency.code == wallet.currency.code) return null
            val accountCurrency = currencies.firstOrNull { it.code == wallet.currency.code }
                ?: return null

            val key = SettlementRow.key(entry.id, isExpense)
            return wallet to SettlementRow(
                entryId = entry.id,
                isExpense = isExpense,
                dateTime = entry.dateTime,
                title = entry.title.ifBlank {
                    resolveName(context, details.category.name, details.category.nameKey)
                },
                spent = Money(amountMilli),
                spentCurrency = spentCurrency,
                accountCurrency = accountCurrency,
                settlement = drafts[key] ?: settlementMilli
                    ?.let { MoneyFormat.formatAmountForInput(
                        Money(it), accountCurrency.decimals) }
                    .orEmpty(),
            )
        }

        return listOfNotNull(
            row(
                isExpense = true,
                wallet = details.expenseWallet,
                amountMilli = entry.expenseAmountMilli,
                spentCurrency = details.expenseCurrency,
                settlementMilli = entry.expenseSettlementAmountMilli,
            ),
            row(
                isExpense = false,
                wallet = details.incomeWallet,
                amountMilli = entry.incomeAmountMilli,
                spentCurrency = details.incomeCurrency,
                settlementMilli = entry.incomeSettlementAmountMilli,
            ),
        )
    }

    fun updateSettlement(row: SettlementRow, value: String) {
        drafts.update { it + (row.key to value) }
    }

    fun save() {
        val currentDrafts = drafts.value
        if (currentDrafts.isEmpty()) return
        val rows = state.value.rows.associateBy { it.key }

        commit.launch(viewModelScope) {
            try {
                currentDrafts.forEach { (key, value) ->
                    val row = rows[key] ?: return@forEach
                    // An emptied field clears the reconciliation: it may have been a mistake.
                    val milli = MoneyFormat.parseAmount(value, row.accountCurrency.decimals)?.milli
                    if (row.isExpense) {
                        entryRepository.setExpenseSettlement(row.entryId, milli)
                    } else {
                        entryRepository.setIncomeSettlement(row.entryId, milli)
                    }
                }
                drafts.value = emptyMap()
                _events.send(EditorEvent.Closed)
            } catch (e: Exception) {
                _events.send(
                    EditorEvent.Error(
                        context.getString(
                            R.string.error_save_settlement,
                            e.localizedMessage.orEmpty(),
                        )
                    )
                )
            }
        }
    }
}