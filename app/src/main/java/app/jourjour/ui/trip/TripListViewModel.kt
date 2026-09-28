package app.jourjour.ui.trip

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.jourjour.R
import app.jourjour.core.dump.Dump
import app.jourjour.core.dump.DumpArchive
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.data.entity.Trip
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.repository.DumpRepository
import app.jourjour.repository.TripRepository
import app.jourjour.ui.common.STOP_TIMEOUT_MS
import javax.inject.Inject

@Immutable
data class TripListState(
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = true,
    /** Whether the menu offers the dumps; a setting, off unless someone turned it on. */
    val dumpsEnabled: Boolean = false,
)

@HiltViewModel
class TripListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    tripRepository: TripRepository,
    appPreferences: AppPreferences,
    private val dumpRepository: DumpRepository,
    private val photoStorage: PhotoStorage,
    /** Handed on to the rows: each one draws its own cover. */
    val coverStorage: TripCoverStorage,
) : ViewModel() {

    val state: StateFlow<TripListState> = combine(
        tripRepository.observeAll(),
        appPreferences.dumpsEnabled,
    ) { trips, dumpsEnabled ->
        TripListState(trips = trips, isLoading = false, dumpsEnabled = dumpsEnabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TripListState(),
    )

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** What the screen has to say after a dump went out or came in: a line for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    // region Dumps

    /** Writes the whole database and its photos to [target] as one archive. */
    fun exportDump(target: Uri) {
        viewModelScope.launch {
            try {
                val dump = dumpRepository.export()
                val photos = withContext(Dispatchers.IO) {
                    val receipts = dump.trips.flatMap { it.entries }.mapNotNull { it.photoFile }
                    val covers = dump.trips.mapNotNull { it.photoFile }
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        DumpArchive.write(
                            dump = dump,
                            receipts = receipts.associateWith(photoStorage::file),
                            covers = covers.associateWith(coverStorage::file),
                            target = out,
                        )
                    } ?: error(target.toString())
                }
                _messages.send(context.getString(R.string.dump_exported, dump.trips.size, photos))
            } catch (e: Exception) {
                _messages.send(context.getString(R.string.error_export, e.localizedMessage.orEmpty()))
            }
        }
    }

    /**
     * Reads a dump from [source] and adds what is not there yet. An archive is read twice:
     * once for the document, and once more for the photos the rows just written point at,
     * which is not known until they are in. A bare document reads too, photos or no.
     */
    fun importDump(source: Uri) {
        viewModelScope.launch {
            try {
                val contents = withContext(Dispatchers.IO) { read(source) }
                val outcome = dumpRepository.import(contents.dump, contents.receipts, contents.covers)
                val message = when (outcome) {
                    is DumpRepository.Outcome.Done -> {
                        val photos = withContext(Dispatchers.IO) { extract(source, outcome) }
                        // The rows of the trips just added are on screen already, and drew no
                        // cover: the pictures reach the folders only now. Saying so sends them
                        // looking again.
                        photoStorage.noteArrivals()
                        coverStorage.noteArrivals()
                        context.getString(R.string.dump_imported, outcome.added, outcome.skipped, photos)
                    }
                    is DumpRepository.Outcome.NewerVersion ->
                        context.getString(R.string.error_dump_version, outcome.found, outcome.expected)
                }
                _messages.send(message)
            } catch (e: Exception) {
                _messages.send(context.getString(R.string.error_import, e.localizedMessage.orEmpty()))
            }
        }
    }

    private fun read(source: Uri): DumpArchive.Contents {
        val stream = context.contentResolver.openInputStream(source) ?: error(source.toString())
        return stream.use { input ->
            val buffered = input.buffered()
            buffered.mark(ARCHIVE_HEAD)
            val head = ByteArray(ARCHIVE_HEAD).let { it.copyOf(buffered.read(it).coerceAtLeast(0)) }
            buffered.reset()
            if (DumpArchive.isArchive(head)) {
                DumpArchive.read(buffered)
            } else {
                DumpArchive.Contents(
                    dump = Dump.decode(buffered.readBytes().toString(Charsets.UTF_8)),
                    receipts = emptySet(),
                    covers = emptySet(),
                )
            }
        }
    }

    private fun extract(source: Uri, outcome: DumpRepository.Outcome.Done): Int {
        if (outcome.receipts.isEmpty() && outcome.covers.isEmpty()) return 0
        val stream = context.contentResolver.openInputStream(source) ?: error(source.toString())
        return stream.use { input ->
            DumpArchive.extract(
                source = input,
                receipts = { name -> photoStorage.file(name).takeIf { name in outcome.receipts } },
                covers = { name -> coverStorage.file(name).takeIf { name in outcome.covers } },
            )
        }
    }

    private companion object {
        /** Enough of a file to tell an archive from a document. */
        const val ARCHIVE_HEAD = 4
    }

    // endregion
}
