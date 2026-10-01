package app.jourjour.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.IconTarget
import app.jourjour.data.entity.ScheduleDayNote
import app.jourjour.data.entity.ScheduleEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The one schema an installed app may be sitting on, brought up to the current one.
 *
 * The database is the one thing in the app that cannot be rewritten with the next release:
 * what is in it belongs to somebody, and a migration that does not leave the tables as
 * Room expects fails on their phone rather than here. So the migration runs against a real
 * database of the version that shipped, with rows in it, and the result is opened through
 * Room — which compares every table, column and index against the schema this build was
 * compiled with and throws if one of them is out.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun a_released_database_gains_everything_built_since() {
        helper.createDatabase(NAME, 1).use { db ->
            db.insert("currencies", SQLiteDatabase.CONFLICT_ABORT, values("code" to "THB", "name" to "Thai Baht", "symbol" to "฿", "decimals" to 2, "sort_order" to 0))
            db.insert("currencies", SQLiteDatabase.CONFLICT_ABORT, values("code" to "RUB", "name" to "Russian Ruble", "symbol" to "₽", "decimals" to 2, "sort_order" to 1))
            // The categories the migration tells apart: one it is about, one it is not,
            // and one it is about whose icon somebody has already chosen for themselves.
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values("id" to 1L, "name" to "Accommodation", "icon" to "🏨", "is_expense" to 1, "is_income" to 0, "track_quantity" to 0, "name_key" to "category_accommodation", "sort_order" to 0))
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values("id" to 2L, "name" to "Transport", "icon" to "🚕", "is_expense" to 1, "is_income" to 0, "track_quantity" to 0, "name_key" to "category_transport", "sort_order" to 1))
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values("id" to 3L, "name" to "Hostel", "icon" to "🏠", "is_expense" to 1, "is_income" to 0, "track_quantity" to 0, "name_key" to "category_accommodation", "sort_order" to 2))
            db.insert("icon_presets", SQLiteDatabase.CONFLICT_ABORT, values("target" to "CATEGORY", "emoji" to "🏨", "sort_order" to 2))
            db.insert("trips", SQLiteDatabase.CONFLICT_ABORT, values("id" to 1L, "name" to "Phuket", "start_date" to 20_575L, "end_date" to 20_584L, "created_at" to 0L))
            db.insert("trip_rates", SQLiteDatabase.CONFLICT_ABORT, values("id" to 1L, "trip_id" to 1L, "from_currency_code" to "THB", "to_currency_code" to "RUB", "rate_milli" to 2_450L, "multiplier" to 1L))
        }

        helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2)

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            NAME,
        ).addMigrations(*MIGRATIONS).build()

        runBlocking {
            // The trip is still there, which is the point of migrating rather than
            // rebuilding, and so is the rate — with the note it had nothing to say in.
            assertEquals(listOf("Phuket"), db.tripDao().getAll().map { it.name })
            val rate = db.tripRateDao().getByTrip(1).single()
            assertEquals(2_450L, rate.rateMilli)
            assertEquals("", rate.note)
            assertEquals(false, rate.noteFromLookup)

            // A note written now comes back as written, which is what the new columns are for.
            db.tripRateDao().update(rate.copy(note = "The kiosk by the market"))
            assertEquals("The kiosk by the market", db.tripRateDao().getByTrip(1).single().note)

            // Nights are counted from now on — and only where the room is.
            val categories = db.categoryDao().getAll()
            assertEquals(listOf(1L, 3L), categories.filter { it.trackQuantity }.map { it.id })

            // The hotel becomes a bed; an icon somebody chose for themselves is left as
            // they chose it, and the picker is offered the bed without losing the hotel.
            assertEquals("🛏️", categories.first { it.id == 1L }.icon)
            assertEquals("🏠", categories.first { it.id == 3L }.icon)
            assertEquals(
                listOf("🏨", "🛏️"),
                db.iconPresetDao().observeEmoji(IconTarget.CATEGORY).first(),
            )

            // A budget line, which points at the dictionaries that were there already:
            // the references are what a migration gets wrong.
            val budgetId = db.budgetDao().insert(
                BudgetItem(
                    tripId = 1,
                    categoryId = 1,
                    title = "Ten nights",
                    quantityMilli = 10_000,
                    amountMilli = 500_000,
                    currencyCode = "THB",
                )
            )
            assertEquals("Ten nights", db.budgetDao().getById(budgetId)?.item?.title)

            // An event, with the planned expense pointing at the same two dictionaries.
            val eventId = db.scheduleDao().insert(
                ScheduleEvent(
                    tripId = 1,
                    title = "Airport transfer",
                    startsAt = 1_790_000_000,
                    durationMinutes = 90,
                    startsInZone = "Asia/Bangkok",
                    categoryId = 2,
                    amountMilli = 800_000,
                    currencyCode = "THB",
                )
            )
            val saved = db.scheduleDao().getById(eventId)
            assertEquals("Airport transfer", saved?.event?.title)
            assertEquals("Transport", saved?.category?.name)

            // And the link that loses itself rather than its event when a predecessor goes.
            val next = db.scheduleDao().insert(
                ScheduleEvent(tripId = 1, title = "Check in", startsAt = 1_790_005_400, durationMinutes = 20, followsId = eventId)
            )
            db.scheduleDao().deleteById(eventId)
            assertEquals(null, db.scheduleDao().getById(next)?.event?.followsId)

            // One line per day and no more: the second thing said about a date replaces
            // the first rather than hiding behind it.
            val date = LocalDate.of(2026, 5, 2)
            db.scheduleDao().describeDay(ScheduleDayNote(tripId = 1, date = date, summary = "Flight"))
            db.scheduleDao().describeDay(ScheduleDayNote(tripId = 1, date = date, summary = "Phuket"))
            assertEquals(listOf("Phuket"), db.scheduleDao().daysOf(1).map { it.summary })

            // Everything of a trip goes with the trip, tables and all.
            db.tripDao().delete(requireNotNull(db.tripDao().getById(1)))
            assertEquals(emptyList<String>(), db.scheduleDao().daysOf(1).map { it.summary })
            assertEquals(emptyList<String>(), db.budgetDao().getByTrip(1).map { it.item.title })
        }
        db.close()
    }

    private fun values(vararg pairs: Pair<String, Any>) = ContentValues().apply {
        pairs.forEach { (column, value) ->
            when (value) {
                is Long -> put(column, value)
                is Int -> put(column, value)
                else -> put(column, value.toString())
            }
        }
    }

    private companion object {
        /** Not the app's own database: the helper creates and deletes this one. */
        const val NAME = "migration-test.db"
    }
}
