package app.jourjour.ui.statistics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import app.jourjour.core.stats.BaseTotals
import app.jourjour.core.stats.CategoryTotals
import app.jourjour.core.stats.CurrencyReport
import app.jourjour.core.stats.TripStatistics
import app.jourjour.core.stats.expenseChartCategories
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import app.jourjour.ui.navigation.Route
import javax.inject.Inject

@Immutable
data class StatisticsState(
    val tripName: String = "",
    val reports: List<CurrencyReport> = emptyList(),
    /**
     * The trip as one figure, or the reason it cannot be given as one. Null where the
     * question does not arise: no base currency, or a trip spent in it alone.
     */
    val baseTotals: BaseTotals? = null,
    /** What the pie is drawn from; empty where a circle would compare unlike things. */
    val chartCategories: List<CategoryTotals> = emptyList(),
    val isLoading: Boolean = true,
)

/** What a trip came to, recomputed whenever an entry changes underneath it. */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    tripRepository: TripRepository,
    entryRepository: EntryRepository,
    currencyRepository: CurrencyRepository,
    appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.toRoute<Route.Statistics>().tripId

    val state: StateFlow<StatisticsState> = combine(
        tripRepository.observeById(tripId),
        entryRepository.observeByTrip(tripId),
        tripRepository.observeRates(tripId),
        appPreferences.baseCurrency,
        currencyRepository.observeAll(),
    ) { trip, entries, rates, baseCode, currencies ->
        val reports = TripStatistics.of(entries)
        val baseTotals = BaseTotals.of(
            entries = entries,
            // A code the dictionary no longer carries converts to nothing, which is the
            // same answer as no base currency having been chosen at all.
            base = currencies.firstOrNull { it.code == baseCode },
            rates = rates,
        )
        StatisticsState(
            tripName = trip?.name.orEmpty(),
            reports = reports,
            baseTotals = baseTotals,
            chartCategories = expenseChartCategories(baseTotals, reports),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StatisticsState(),
    )
}
