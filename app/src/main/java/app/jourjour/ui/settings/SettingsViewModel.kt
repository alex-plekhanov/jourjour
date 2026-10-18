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
import app.jourjour.data.prefs.ALLOWED_EVENT_MINUTES
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.prefs.DEFAULT_DAY_BEGINS
import app.jourjour.data.prefs.DEFAULT_EVENT_LENGTH
import app.jourjour.data.prefs.PhotoSettings
import app.jourjour.repository.CurrencyRepository
import app.jourjour.repository.ScheduleRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import java.time.LocalTime
import java.time.ZoneId
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
    /** How hard a stored photo is encoded. A string while it is typed, as the size is. */
    val quality: String = PhotoSettings.DEFAULT_QUALITY.toString(),
    @param:StringRes val qualityError: Int? = null,
    /** Whether a shot is looked at before the entry keeps it. */
    val review: Boolean = false,
    /** Where the clocks stand before a trip has moved them. */
    val homeZone: ZoneId = ZoneId.systemDefault(),
    /** The hour an empty day of a schedule is offered from. */
    val dayBegins: LocalTime = DEFAULT_DAY_BEGINS,
    /** How long a new event is offered as. A string while it is typed, as the photo size is. */
    val eventMinutes: String = DEFAULT_EVENT_LENGTH.toString(),
    @param:StringRes val eventMinutesError: Int? = null,
    /** Whether a schedule opens read-only once its trip has begun. */
    val viewWhenStarted: Boolean = false,
    /**
     * The zone waiting on an answer, where one was picked and there are schedules to
     * rewrite. The field goes on showing the old one until it is confirmed: nothing has
     * been changed yet, and a field that had already moved would be saying otherwise.
     */
    val askedZone: ZoneId? = null,
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
    private val scheduleRepository: ScheduleRepository,
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
                quality = settings.quality.toString(),
                review = settings.review,
                homeZone = appPreferences.homeZone.first(),
                dayBegins = appPreferences.dayBegins.first(),
                eventMinutes = appPreferences.defaultEventMinutes.first().toString(),
                viewWhenStarted = appPreferences.viewWhenStarted.first(),
                dumpsEnabled = appPreferences.dumpsEnabled.first(),
                isLoading = false,
            )
        }
    }

    fun updateBaseCurrency(code: String?) {
        form.update { it.copy(baseCurrency = code) }
        viewModelScope.launch { appPreferences.setBaseCurrency(code) }
    }

    /**
     * Changing where home is rewrites every schedule that has anything in it — and moves
     * nothing anybody can see. What a person typed was an hour on a clock, and that hour
     * stands; what changes is the moment it means, which every schedule stores and none of
     * them shows. Leaving it out would walk their plans sideways instead.
     *
     * Which is exactly why it is asked about first wherever there is a schedule to rewrite:
     * a setting that silently rewrote every trip in the app, and showed nothing for it, is
     * not something to find out about afterwards. Where there is nothing to rewrite there
     * is nothing to ask.
     */
    fun updateHomeZone(zone: ZoneId) {
        if (zone == form.value.homeZone) return
        viewModelScope.launch {
            if (scheduleRepository.anyEvents()) {
                form.update { it.copy(askedZone = zone) }
            } else {
                rehome(zone)
            }
        }
    }

    fun confirmHomeZone() {
        val zone = form.value.askedZone ?: return
        viewModelScope.launch { rehome(zone) }
    }

    fun cancelHomeZone() = form.update { it.copy(askedZone = null) }

    private suspend fun rehome(zone: ZoneId) {
        val was = form.value.homeZone
        form.update { it.copy(homeZone = zone, askedZone = null) }
        scheduleRepository.rehome(was, zone)
        appPreferences.setHomeZone(zone)
    }

    fun updateDayBegins(time: LocalTime) {
        form.update { it.copy(dayBegins = time) }
        viewModelScope.launch { appPreferences.setDayBegins(time) }
    }

    /** A length outside the sensible range is left in the field but never stored. */
    fun updateEventMinutes(value: String) {
        val digits = value.filter(Char::isDigit).take(MINUTE_DIGITS)
        val parsed = digits.toIntOrNull()
        val error = R.string.error_event_minutes
            .takeIf { parsed == null || parsed !in ALLOWED_EVENT_MINUTES }
        form.update { it.copy(eventMinutes = digits, eventMinutesError = error) }
        if (error == null && parsed != null) {
            viewModelScope.launch { appPreferences.setDefaultEventMinutes(parsed) }
        }
    }

    fun updateViewWhenStarted(value: Boolean) {
        form.update { it.copy(viewWhenStarted = value) }
        viewModelScope.launch { appPreferences.setViewWhenStarted(value) }
    }

    fun updateDumpsEnabled(value: Boolean) {
        form.update { it.copy(dumpsEnabled = value) }
        viewModelScope.launch { appPreferences.setDumpsEnabled(value) }
    }

    fun updateCompress(value: Boolean) {
        form.update { it.copy(compress = value) }
        persist()
    }

    fun updateReview(value: Boolean) {
        form.update { it.copy(review = value) }
        persist()
    }

    fun updateMaxSide(value: String) {
        form.update { state ->
            val digits = value.filter(Char::isDigit).take(MAX_DIGITS)
            state.copy(maxSide = digits, maxSideError = maxSideErrorFor(digits))
        }
        persist()
    }

    fun updateQuality(value: String) {
        form.update { state ->
            val digits = value.filter(Char::isDigit).take(QUALITY_DIGITS)
            state.copy(quality = digits, qualityError = qualityErrorFor(digits))
        }
        persist()
    }

    private fun maxSideErrorFor(value: String): Int? {
        val parsed = value.toIntOrNull()
        return R.string.error_photo_max_side
            .takeIf { parsed == null || parsed !in PhotoSettings.ALLOWED_MAX_SIDE }
    }

    private fun qualityErrorFor(value: String): Int? {
        val parsed = value.toIntOrNull()
        return R.string.error_photo_quality
            .takeIf { parsed == null || parsed !in PhotoSettings.ALLOWED_QUALITY }
    }

    /**
     * A value outside the sensible range is left in the field but never stored — and
     * neither is the other one beside it: the two go into one record, so writing it with
     * a figure still being typed would save half an answer.
     */
    private fun persist() {
        val state = form.value
        val maxSide = state.maxSide.toIntOrNull() ?: return
        if (maxSide !in PhotoSettings.ALLOWED_MAX_SIDE) return
        val quality = state.quality.toIntOrNull() ?: return
        if (quality !in PhotoSettings.ALLOWED_QUALITY) return

        viewModelScope.launch {
            appPreferences.setPhotoSettings(
                PhotoSettings(
                    compress = state.compress,
                    maxSide = maxSide,
                    quality = quality,
                    review = state.review,
                )
            )
        }
    }

    private companion object {
        const val MAX_DIGITS = 4
        const val QUALITY_DIGITS = 3
        const val MINUTE_DIGITS = 5
    }
}
