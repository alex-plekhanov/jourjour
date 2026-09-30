package app.jourjour.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.prefs.LastLocation
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.TripRepository
import javax.inject.Inject

/**
 * Which screen to start on. `null` means the answer is still coming: preferences are read
 * from disk, and drawing the graph before it arrives would flash the trip list and then
 * replace it.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val tripRepository: TripRepository,
    private val entryRepository: EntryRepository,
    private val photoStorage: PhotoStorage,
    private val coverStorage: TripCoverStorage,
) : ViewModel() {

    private val _startTripId = MutableStateFlow<Result?>(null)
    val startTripId: StateFlow<Result?> = _startTripId.asStateFlow()

    /** A wrapper around a nullable value: a null wrapper means the answer is not in yet. */
    @JvmInline
    value class Result(val tripId: Long?)

    init {
        viewModelScope.launch {
            val location = appPreferences.lastLocation.first()
            val tripId = (location as? LastLocation.OnTrip)?.tripId
            // The trip may have been deleted while the app was closed.
            _startTripId.value = Result(tripId?.takeIf { tripRepository.getById(it) != null })

            // A form abandoned after a photo was added, or a crash between writing the
            // file and saving the record, leaves a file nothing points at. Startup is the
            // one moment when the whole set of referenced photos is cheap to ask for.
            photoStorage.deleteOrphans(entryRepository.allPhotoFiles().toSet())
            coverStorage.deleteOrphans(tripRepository.allPhotoFiles().toSet())
        }
    }

    fun recordLocation(tripId: Long?) {
        viewModelScope.launch { appPreferences.setLastLocation(tripId) }
    }
}
