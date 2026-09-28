package app.jourjour.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.jourjour.R
import app.jourjour.data.entity.Currency
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.prefs.PhotoSettings
import app.jourjour.repository.CurrencyRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import javax.inject.Inject

@Immutable
data class SettingsState(
    /** The currency trip totals are converted to, or null while none is chosen. */
    val baseCurrency: String? = null,
    /** The dictionary, for the picker to offer. */
    val currencies: List<Currency> = emptyList(),
    val compress: Boolean = true,
    /** A string while it is typed: an empty field means unfilled, not zero. */
    val maxSide: String = PhotoSettings.DEFAULT_MAX_SIDE.toString(),
    @param:StringRes val maxSideError: Int? = null,
    /** Whether the dump items are on the trip list's menu. */
    val dumpsEnabled: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * The settings: the base currency, receipt photos, and the switch that puts the dumps on
 * the menu.
 *
 * Changes are written as they are made: there is nothing here to get half-done and no
 * reason to make anyone press Save for a switch.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    /** What has been typed and switched here; the dictionary is joined on below. */
    private val form = MutableStateFlow(SettingsState())

    val state: StateFlow<SettingsState> =
        combine(form, currencyRepository.observeAll()) { form, currencies ->
            form.copy(currencies = currencies)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = form.value,
        )

    init {
        viewModelScope.launch {
            val settings = appPreferences.photoSettings.first()
            form.value = SettingsState(
                baseCurrency = appPreferences.baseCurrency.first(),
                compress = settings.compress,
                maxSide = settings.maxSide.toString(),
                dumpsEnabled = appPreferences.dumpsEnabled.first(),
                isLoading = false,
            )
        }
    }

    fun updateBaseCurrency(code: String?) {
        form.update { it.copy(baseCurrency = code) }
        viewModelScope.launch { appPreferences.setBaseCurrency(code) }
    }

    fun updateDumpsEnabled(value: Boolean) {
        form.update { it.copy(dumpsEnabled = value) }
        viewModelScope.launch { appPreferences.setDumpsEnabled(value) }
    }

    fun updateCompress(value: Boolean) {
        form.update { it.copy(compress = value) }
        persist()
    }

    fun updateMaxSide(value: String) {
        form.update { state ->
            val digits = value.filter(Char::isDigit).take(MAX_DIGITS)
            state.copy(maxSide = digits, maxSideError = errorFor(digits))
        }
        persist()
    }

    private fun errorFor(value: String): Int? {
        val parsed = value.toIntOrNull()
        return R.string.error_photo_max_side
            .takeIf { parsed == null || parsed !in PhotoSettings.ALLOWED_MAX_SIDE }
    }

    /** A value outside the sensible range is left in the field but never stored. */
    private fun persist() {
        val state = form.value
        val maxSide = state.maxSide.toIntOrNull() ?: return
        if (maxSide !in PhotoSettings.ALLOWED_MAX_SIDE) return

        viewModelScope.launch {
            appPreferences.setPhotoSettings(
                PhotoSettings(compress = state.compress, maxSide = maxSide)
            )
        }
    }

    private companion object {
        const val MAX_DIGITS = 4
    }
}
