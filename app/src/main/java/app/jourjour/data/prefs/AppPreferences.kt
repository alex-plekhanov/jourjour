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
import java.time.LocalTime
import java.time.ZoneId
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
 * It reopens on the trip that was last being worked on, and on the list of trips in every
 * other case. Which tab of a trip it opens is a separate question and a separate answer:
 * see [lastTab].
 */
sealed interface LastLocation {
    data object TripList : LastLocation

    data class OnTrip(val tripId: Long) : LastLocation
}


/** How receipt photos are treated when they arrive from the camera. */
data class PhotoSettings(
    val compress: Boolean = true,
    /** The longest side a stored photo is shrunk to, in pixels. */
    val maxSide: Int = DEFAULT_MAX_SIDE,
    /** What a stored photo is encoded at, from 1 to 100 as JPEG counts it. */
    val quality: Int = DEFAULT_QUALITY,
    /**
     * Whether a shot is looked at before the entry keeps it.
     *
     * Off by default, and not because looking is a bad idea: the camera shows a
     * confirmation screen of its own that nothing here can switch off, so this is a second
     * one after it. Worth asking for when receipts come out on their side — a receipt
     * photographed straight down gives the phone nothing to work the orientation out from —
     * and worth leaving off for anybody whose shots come out the right way up.
     */
    val review: Boolean = false,
) {
    companion object {
        const val DEFAULT_MAX_SIDE = 1600

        /**
         * A receipt is kept in order to be read back — a line of figures, often in small
         * print — so it is not the place to save bytes.
         */
        const val DEFAULT_QUALITY = 75

        /** Below the lower bound a receipt stops being readable; above it the gain is gone. */
        val ALLOWED_MAX_SIDE = 480..4096

        /**
         * The whole of what JPEG accepts, less the bottom tenth: below that a receipt is
         * blocks of grey where the figures were, which is not a setting, it is a fault.
         */
        val ALLOWED_QUALITY = 10..100
    }
}

/** Nine in the morning, and an hour: what a schedule assumes until it is told otherwise. */
val DEFAULT_DAY_BEGINS: LocalTime = LocalTime.of(9, 0)
const val DEFAULT_EVENT_LENGTH = 60

/** What a duration may sensibly be: a minute at the least, a fortnight at the most. */
val ALLOWED_EVENT_MINUTES = 1..20_160

@Singleton
class AppPreferences @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** What is on disk — or nothing, when the disk cannot be read; see the handler above. */
    private val data: Flow<Preferences> = context.appStateDataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val lastLocation: Flow<LastLocation> = data.map { preferences ->
        preferences[LAST_TRIP_ID]
            ?.let(LastLocation::OnTrip)
            ?: LastLocation.TripList
    }

    /**
     * The tab last worked on, whichever trip it was on, or null while nobody has opened
     * one yet. It belongs to the person rather than to the trip: somebody keeping an eye
     * on the plan is keeping an eye on the plan of whatever trip they open next.
     *
     * A name rather than a position, for the reason the spreadsheet's columns are: a tab
     * added or moved must not silently turn a saved answer into a different one. A name no
     * tab answers to any more reads as nothing remembered.
     */
    val lastTab: Flow<String?> = data.map { it[LAST_TAB] }

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
            quality = preferences[PHOTO_QUALITY] ?: PhotoSettings.DEFAULT_QUALITY,
            review = preferences[REVIEW_PHOTOS] ?: false,
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
     * The same, for the plan's own spreadsheet. Kept apart from the entries' choice: the
     * two are different tables, and a column ticked on one says nothing about the other.
     */
    val planColumns: Flow<Set<String>> = data.map { it[PLAN_COLUMNS].orEmpty() }

    /**
     * Whether the dump items are on the menu. Off by default: dumps are for moving what was
     * entered on one install to another while the app is being developed, and the two
     * items would only puzzle anybody else.
     */
    val dumpsEnabled: Flow<Boolean> = data.map { it[DUMPS_ENABLED] ?: false }

    /**
     * Where the clocks stand before a trip has moved them: the zone every schedule begins
     * in, and the one an event is read in while nothing has said otherwise.
     *
     * It has to be a setting rather than the phone's own zone, because the phone's zone is
     * where a person is *now* — which, halfway through a trip, is the wrong end of it. The
     * phone answers for it only until somebody has said otherwise, which is what the fall
     * back to the device is for.
     */
    val homeZone: Flow<ZoneId> = data.map { preferences ->
        preferences[HOME_ZONE]
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
    }

    /**
     * The hour a person's day is taken to start at, which is what an empty day of a
     * schedule is offered as. Every other gap has an event to run from.
     */
    val dayBegins: Flow<LocalTime> = data.map { preferences ->
        preferences[DAY_BEGINS]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: DEFAULT_DAY_BEGINS
    }

    /**
     * How long an event is offered as before anybody has said otherwise. An hour is what
     * most things are, and a tap on an empty afternoon means "something goes here" rather
     * than "four hours of it".
     */
    val defaultEventMinutes: Flow<Int> = data.map {
        it[DEFAULT_EVENT_MINUTES] ?: DEFAULT_EVENT_LENGTH
    }

    /**
     * Whether a schedule opens read-only once the trip it belongs to has begun.
     *
     * The two things a schedule is for happen at different times: writing it down, which
     * is done before, and reading what is next, which is done during. On the road the
     * second is all anybody wants, and everything the first needs — the empty hours, the
     * chains, the way in to a new event — is in the way of it.
     */
    val viewWhenStarted: Flow<Boolean> = data.map { it[VIEW_WHEN_STARTED] ?: true }

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

    suspend fun setLastTab(name: String) {
        context.appStateDataStore.edit { it[LAST_TAB] = name }
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
            preferences[PHOTO_QUALITY] = settings.quality
            preferences[REVIEW_PHOTOS] = settings.review
        }
    }

    suspend fun setViewWhenStarted(value: Boolean) {
        context.appStateDataStore.edit { it[VIEW_WHEN_STARTED] = value }
    }

    suspend fun setDayBegins(time: LocalTime) {
        context.appStateDataStore.edit { it[DAY_BEGINS] = time.toSecondOfDay() / 60 }
    }

    suspend fun setDefaultEventMinutes(minutes: Int) {
        context.appStateDataStore.edit { it[DEFAULT_EVENT_MINUTES] = minutes }
    }

    suspend fun setHomeZone(zone: ZoneId) {
        context.appStateDataStore.edit { it[HOME_ZONE] = zone.id }
    }

    suspend fun setDumpsEnabled(enabled: Boolean) {
        context.appStateDataStore.edit { it[DUMPS_ENABLED] = enabled }
    }

    suspend fun setPlanColumns(names: Set<String>) {
        context.appStateDataStore.edit { it[PLAN_COLUMNS] = names }
    }

    suspend fun setSpreadsheetColumns(names: Set<String>) {
        context.appStateDataStore.edit { preferences ->
            preferences[SPREADSHEET_COLUMNS] = names
        }
    }

    private companion object {
        val LAST_TRIP_ID = longPreferencesKey("last_trip_id")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val COMPRESS_PHOTOS = booleanPreferencesKey("compress_photos")
        val PHOTO_MAX_SIDE = intPreferencesKey("photo_max_side")
        val PHOTO_QUALITY = intPreferencesKey("photo_quality")
        val REVIEW_PHOTOS = booleanPreferencesKey("review_photos")
        val SPREADSHEET_COLUMNS = stringSetPreferencesKey("spreadsheet_columns")
        val PLAN_COLUMNS = stringSetPreferencesKey("plan_columns")
        val DUMPS_ENABLED = booleanPreferencesKey("dumps_enabled")
        val HOME_ZONE = stringPreferencesKey("home_zone")
        val DAY_BEGINS = intPreferencesKey("day_begins")
        val DEFAULT_EVENT_MINUTES = intPreferencesKey("default_event_minutes")
        val VIEW_WHEN_STARTED = booleanPreferencesKey("view_when_started")
    }
}
