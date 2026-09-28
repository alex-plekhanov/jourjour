package app.jourjour.ui.settings.currency

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import app.jourjour.core.text.resolveName
import app.jourjour.data.entity.Currency
import app.jourjour.repository.CurrencyRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import javax.inject.Inject

@Immutable
data class CurrencyListState(
    val currencies: List<Currency> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
)

@HiltViewModel
class CurrencyListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<CurrencyListState> =
        combine(currencyRepository.observeAll(), query) { currencies, query ->
            CurrencyListState(
                currencies = currencies.filter { it.matches(query) },
                query = query,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CurrencyListState(),
        )

    fun updateQuery(value: String) {
        query.value = value
    }

    /** Searches the code and the name in the current language alike — "dol" finds USD. */
    private fun Currency.matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return code.contains(needle, ignoreCase = true) ||
            resolveName(context, name, nameKey).contains(needle, ignoreCase = true)
    }
}
