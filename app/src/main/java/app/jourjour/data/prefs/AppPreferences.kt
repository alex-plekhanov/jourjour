package app.jourjour.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appStateDataStore by preferencesDataStore(
    name = "app_state",
    // A file that can no longer be read is started over rather than left to throw:
    // everything in it is a convenience, and the start-up screen that waits on it would
    // otherwise never arrive.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Where the app was left off.
 *
 * It reopens on a trip's expenses when that is what was last being worked on, and on the
 * list of trips in every other case.
 */
sealed interface LastLocation {
    data object TripList : LastLocation

    data class TripEntries(val tripId: Long) : LastLocation
}

/** How receipt photos are treated when they arrive from the camera. */
data class PhotoSettings(
    val compress: Boolean = true,
    /** The longest side a stored photo is shrunk to, in pixels. */
    val maxSide: Int = DEFAULT_MAX_SIDE,
) {
    companion object {
        const val DEFAULT_MAX_SIDE = 1600

        /** Below the lower bound a receipt stops being readable; above it the gain is gone. */
        val ALLOWED_MAX_SIDE = 480..4096
    }
}

@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** What is on disk — or nothing, when the disk cannot be read; see the handler above. */
    private val data: Flow<Preferences> = context.appStateDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val lastLocation: Flow<LastLocation> = data.map { preferences ->
        preferences[LAST_TRIP_ID]
            ?.let(LastLocation::TripEntries)
            ?: LastLocation.TripList
    }

    /**
     * The currency a trip's totals are brought to, or null while nobody has chosen one.
     *
     * A code rather than a currency of its own: the dictionary owns currencies, and a
     * setting holding a copy of one could disagree with it. A code since deleted from the
     * dictionary reads back as a currency nothing can be converted to, which comes to the
     * same thing as none being set.
     */
    val baseCurrency: Flow<String?> = data.map { it[BASE_CURRENCY] }

    val photoSettings: Flow<PhotoSettings> = data.map { preferences ->
        PhotoSettings(
            compress = preferences[COMPRESS_PHOTOS] ?: true,
            maxSide = preferences[PHOTO_MAX_SIDE] ?: PhotoSettings.DEFAULT_MAX_SIDE,
        )
    }

    /**
     * The columns the last spreadsheet was written with, by name.
     *
     * Empty means nobody has chosen yet, which the screen reads as all of them. An empty
     * choice cannot be saved — a sheet of no columns is not worth writing — so the two
     * cases never collide. Names rather than positions: a column that is added or moved
     * must not silently turn a saved choice into a different one.
     */
    val spreadsheetColumns: Flow<Set<String>> = data.map {
        it[SPREADSHEET_COLUMNS].orEmpty()
    }

    /**
     * Whether the dump items are on the menu. Off by default: dumps are for moving what was
     * entered on one install to another while the app is being developed, and the two
     * items would only puzzle anybody else.
     */
    val dumpsEnabled: Flow<Boolean> = data.map { it[DUMPS_ENABLED] ?: false }

    /**
     * @param tripId the trip whose expenses are being worked on, or null for any other
     * screen.
     */
    suspend fun setLastLocation(tripId: Long?) {
        context.appStateDataStore.edit { preferences ->
            if (tripId == null) {
                preferences.remove(LAST_TRIP_ID)
            } else {
                preferences[LAST_TRIP_ID] = tripId
            }
        }
    }

    /** @param code null to leave every trip's totals in the currencies they were spent in. */
    suspend fun setBaseCurrency(code: String?) {
        context.appStateDataStore.edit { preferences ->
            if (code == null) {
                preferences.remove(BASE_CURRENCY)
            } else {
                preferences[BASE_CURRENCY] = code
            }
        }
    }

    suspend fun setPhotoSettings(settings: PhotoSettings) {
        context.appStateDataStore.edit { preferences ->
            preferences[COMPRESS_PHOTOS] = settings.compress
            preferences[PHOTO_MAX_SIDE] = settings.maxSide
        }
    }

    suspend fun setDumpsEnabled(enabled: Boolean) {
        context.appStateDataStore.edit { it[DUMPS_ENABLED] = enabled }
    }

    suspend fun setSpreadsheetColumns(names: Set<String>) {
        context.appStateDataStore.edit { preferences ->
            preferences[SPREADSHEET_COLUMNS] = names
        }
    }

    private companion object {
        val LAST_TRIP_ID = longPreferencesKey("last_trip_id")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val COMPRESS_PHOTOS = booleanPreferencesKey("compress_photos")
        val PHOTO_MAX_SIDE = intPreferencesKey("photo_max_side")
        val SPREADSHEET_COLUMNS = stringSetPreferencesKey("spreadsheet_columns")
        val DUMPS_ENABLED = booleanPreferencesKey("dumps_enabled")
    }
}
