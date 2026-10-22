package app.jourjour.ui.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.rates.convertBetween
import app.jourjour.core.rates.quotesTo
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.TripRate
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * The currency converter of a trip, offered from the wallets' menu: the wallets are where
 * money in more than one currency is held, and what a pocketful of one comes to in another
 * is asked standing in front of them.
 *
 * Its own view model rather than a part of the wallets': it reads the trip's rates, which
 * nothing else on that tab does, and shares nothing with the balances but the trip.
 */
@HiltViewModel
class ConverterViewModel @Inject constructor(
    tripRepository: TripRepository,
    private val currencyRepository: CurrencyRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Trip>().tripId

    private val sheet = MutableStateFlow<ConverterSheet?>(null)

    /** What a conversion is worked out from, kept current while the sheet is open. */
    private val sources = combine(
        tripRepository.observeRates(tripId),
        currencyRepository.observeAll(),
    ) { rates, currencies -> rates to currencies }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The converter while it is open, and nothing while it is not. */
    val state: StateFlow<ConverterSheet?> = combine(sheet, sources) { open, _ -> open }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Opens the converter on the two currencies a person is most likely to want: the one
     * they were last dealing in, and the one they reckon in.
     *
     * What it offers is what the trip has rates for, and the base currency those rates
     * point at — the only road between two currencies this app has. The rates are the
     * person's own, which is the first thing the sheet says.
     */
    fun open() {
        viewModelScope.launch {
            val (rates, currencies) = sources.filterNotNull().first()
            val baseCode = appPreferences.baseCurrency.first()
            val base = currencies.firstOrNull { it.code == baseCode }
            val convertible = convertible(base, rates, currencies)
            sheet.value = ConverterSheet(
                currencies = convertible,
                base = base,
                // Last used first, and anything rather than nothing: a converter opening
                // on the base currency twice over would convert nothing into itself.
                fromCode = convertible
                    .filter { it.code != base?.code }
                    .maxByOrNull { it.usedAt ?: LocalDateTime.MIN }
                    ?.code,
                toCode = base?.code,
            )
        }
    }

    fun close() {
        sheet.value = null
    }

    /** The far end of a conversion follows the near one: the amount given stands. */
    fun updateFrom(value: String) = sheet.update { open ->
        val sheet = open?.copy(from = value) ?: return@update null
        sheet.copy(to = sheet.convertFrom())
    }

    fun updateTo(value: String) = sheet.update { open ->
        val sheet = open?.copy(to = value) ?: return@update null
        sheet.copy(from = sheet.convertTo())
    }

    /**
     * Choosing a currency leaves the amount beside it where it is and works the other one
     * out again: the figure a person just typed is the one they meant.
     */
    fun selectFrom(code: String?) {
        sheet.update { open ->
            val sheet = open?.copy(fromCode = code) ?: return@update null
            sheet.copy(to = sheet.convertFrom())
        }
        touch(code)
    }

    fun selectTo(code: String?) {
        sheet.update { open ->
            val sheet = open?.copy(toCode = code) ?: return@update null
            sheet.copy(from = sheet.convertTo())
        }
        touch(code)
    }

    private fun ConverterSheet.convertFrom(): String = converted(
        text = from,
        from = fromCurrency,
        to = toCurrency,
        base = base,
    )

    private fun ConverterSheet.convertTo(): String = converted(
        text = to,
        from = toCurrency,
        to = fromCurrency,
        base = base,
    )

    /** One side of the converter worked out from the other, or blank where it cannot be. */
    private fun converted(text: String, from: Currency?, to: Currency?, base: Currency?): String {
        if (from == null || to == null || base == null) return ""
        val rates = sources.value?.first ?: return ""
        val amount = MoneyFormat.parseAmount(text, from.decimals) ?: return ""
        val result = convertBetween(amount, from.code, to.code, quotesTo(base, rates)) ?: return ""
        return MoneyFormat.formatAmountForInput(result, to.decimals)
    }

    /** What a conversion may name: everything with a rate to the base, and the base. */
    private fun convertible(
        base: Currency?,
        rates: List<TripRate>,
        currencies: List<Currency>,
    ): List<Currency> {
        if (base == null) return emptyList()
        val withRates = rates
            .filter { it.toCurrencyCode == base.code }
            .mapNotNull { rate -> currencies.firstOrNull { it.code == rate.fromCurrencyCode } }
        return (withRates + base)
            .distinctBy { it.code }
            .sortedWith(compareBy<Currency> { it.sortOrder }.thenBy { it.code })
    }

    private fun touch(code: String?) {
        code?.let { viewModelScope.launch { currencyRepository.touch(it, LocalDateTime.now()) } }
    }
}
