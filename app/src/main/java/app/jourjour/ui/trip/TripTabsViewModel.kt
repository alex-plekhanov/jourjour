package app.jourjour.ui.trip

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.jourjour.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the screen needs before it can say which tab to open on, and nothing else.
 *
 * [isLoaded] is what the screen waits for: opening one tab and jumping to another a moment
 * later would read as a fault, so nothing is shown until the answer is in.
 */
@Immutable
data class TripTabsState(
    val isLoaded: Boolean = false,
    /** The tab last worked on, or null while nobody has opened one yet. */
    val remembered: String? = null,
)

/**
 * Which tab of a trip to open on, and remembering which one was left.
 *
 * One tab, for the app rather than for each trip: somebody keeping an eye on the plan is
 * keeping an eye on the plan of whatever trip they open next, and a rule that guessed
 * otherwise would be guessing. Where nothing has been remembered yet — a first run — the
 * trip is opened at the stage it is at; see `TripScreen`.
 */
@HiltViewModel
class TripTabsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TripTabsState())
    val state: StateFlow<TripTabsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = TripTabsState(isLoaded = true, remembered = appPreferences.lastTab.first())
        }
    }

    /**
     * Writes down the tab being left. Every move counts, not only the last: there is no
     * moment the app is told it is closing, so the only reliable time to write is now.
     */
    fun remember(name: String) {
        if (_state.value.remembered == name) return
        _state.value = _state.value.copy(remembered = name)
        viewModelScope.launch { appPreferences.setLastTab(name) }
    }
}
