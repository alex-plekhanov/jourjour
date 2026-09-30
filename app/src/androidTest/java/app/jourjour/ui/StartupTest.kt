package app.jourjour.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Trip
import app.jourjour.data.prefs.AppPreferences
import app.jourjour.data.prefs.LastLocation
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.repository.EntryRepository
import app.jourjour.repository.TripRepository
import java.time.LocalDate

/**
 * The app has to reopen where it was left: on a trip — and on the tab that was being
 * worked on — or on the list. Both the remembering and the guard against a deleted trip
 * dragging the app onto an empty screen are checked here.
 */
@RunWith(AndroidJUnit4::class)
class StartupTest {

    private lateinit var db: AppDatabase
    private lateinit var preferences: AppPreferences
    private lateinit var tripRepository: TripRepository
    private lateinit var entryRepository: EntryRepository
    private lateinit var photoStorage: PhotoStorage
    private lateinit var coverStorage: TripCoverStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        preferences = AppPreferences(context)
        tripRepository = TripRepository(db, db.tripDao(), db.tripWalletDao(),
            db.tripRateDao(), db.entryDao(), db.currencyDao())
        entryRepository = EntryRepository(db.entryDao())
        photoStorage = PhotoStorage(context)
        coverStorage = TripCoverStorage(context)
    }

    @After
    fun tearDown() = runBlocking {
        // Preferences are shared app-wide, so they are put back the way they were.
        preferences.setLastLocation(null)
        db.close()
    }

    @Test
    fun the_last_location_is_remembered_and_cleared() = runBlocking {
        preferences.setLastLocation(42)
        assertEquals(LastLocation.OnTrip(42), preferences.lastLocation.first())

        preferences.setLastLocation(null)
        assertEquals(LastLocation.TripList, preferences.lastLocation.first())
    }

    /** One tab for the app, whichever trip it was left on. */
    @Test
    fun the_last_tab_is_remembered() = runBlocking {
        preferences.setLastTab("PLAN")
        assertEquals("PLAN", preferences.lastTab.first())

        preferences.setLastTab("CHECKLIST")
        assertEquals("CHECKLIST", preferences.lastTab.first())
    }

    @Test
    fun the_app_opens_on_the_entries_of_a_trip_that_exists() = runBlocking {
        val tripId = newTrip()
        preferences.setLastLocation(tripId)

        assertEquals(tripId, startTripId())
    }

    @Test
    fun a_deleted_trip_falls_back_to_the_list() = runBlocking {
        val tripId = newTrip()
        preferences.setLastLocation(tripId)
        db.tripDao().delete(db.tripDao().getById(tripId)!!)

        assertNull(startTripId())
    }

    @Test
    fun with_no_trip_saved_the_list_opens() = runBlocking {
        preferences.setLastLocation(null)
        assertNull(startTripId())
    }

    private suspend fun startTripId(): Long? =
        StartupViewModel(
            preferences,
            tripRepository,
            entryRepository,
            photoStorage,
            coverStorage,
        ).startTripId.first { it != null }?.tripId

    private suspend fun newTrip(): Long {
        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        return db.tripDao().insert(
            Trip(
                name = "Japan",
                startDate = LocalDate.of(2026, 4, 3),
                endDate = LocalDate.of(2026, 4, 17),
            )
        )
    }
}
