package app.jourjour.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.Trip
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The two steps the entry form takes along a trip's own moments.
 *
 * The queries are MIN and MAX over a stored moment, which nothing in the interface gives
 * away, and the whole point of them is the edges: what a step does at the earliest moment
 * of a trip, and what it does where several entries share one.
 */
@RunWith(AndroidJUnit4::class)
class MomentStepsTest {

    private lateinit var db: AppDatabase
    private var tripId = 0L
    private var categoryId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        tripId = db.tripDao().insert(
            Trip(
                name = "Test",
                startDate = LocalDate.of(2026, 4, 3),
                endDate = LocalDate.of(2026, 4, 17),
            )
        )
        categoryId = db.categoryDao().insert(Category(name = "Groceries", icon = "🛒"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun a_step_back_lands_on_the_moment_before() = runBlocking {
        write(LocalDateTime.of(2026, 4, 4, 9, 30))
        write(LocalDateTime.of(2026, 4, 4, 13, 0))

        val before = db.entryDao().momentBefore(tripId, LocalDateTime.of(2026, 4, 4, 13, 0))

        assertEquals(LocalDateTime.of(2026, 4, 4, 9, 30), before)
    }

    @Test
    fun a_step_forward_lands_on_the_moment_after() = runBlocking {
        write(LocalDateTime.of(2026, 4, 4, 9, 30))
        write(LocalDateTime.of(2026, 4, 4, 13, 0))

        val after = db.entryDao().momentAfter(tripId, LocalDateTime.of(2026, 4, 4, 9, 30))

        assertEquals(LocalDateTime.of(2026, 4, 4, 13, 0), after)
    }

    @Test
    fun entries_sharing_a_moment_are_stepped_over_together() = runBlocking {
        write(LocalDateTime.of(2026, 4, 4, 9, 30))
        // The receipt and the tip paid with it: one stop, one step.
        write(LocalDateTime.of(2026, 4, 4, 13, 0))
        write(LocalDateTime.of(2026, 4, 4, 13, 0))
        write(LocalDateTime.of(2026, 4, 4, 18, 0))

        val back = db.entryDao().momentBefore(tripId, LocalDateTime.of(2026, 4, 4, 18, 0))
        val backAgain = db.entryDao().momentBefore(tripId, back!!)

        assertEquals(LocalDateTime.of(2026, 4, 4, 13, 0), back)
        assertEquals(LocalDateTime.of(2026, 4, 4, 9, 30), backAgain)
    }

    @Test
    fun there_is_nothing_beyond_either_end() = runBlocking {
        val only = LocalDateTime.of(2026, 4, 4, 9, 30)
        write(only)

        assertNull(db.entryDao().momentBefore(tripId, only))
        assertNull(db.entryDao().momentAfter(tripId, only))
    }

    @Test
    fun another_trip_is_no_part_of_this_one() = runBlocking {
        val other = db.tripDao().insert(
            Trip(
                name = "Elsewhere",
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 8),
            )
        )
        write(LocalDateTime.of(2026, 4, 4, 9, 30))
        write(LocalDateTime.of(2026, 1, 2, 9, 30), tripId = other)

        val before = db.entryDao().momentBefore(tripId, LocalDateTime.of(2026, 4, 4, 9, 30))

        assertNull(before)
    }

    private suspend fun write(moment: LocalDateTime, tripId: Long = this.tripId) {
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = categoryId,
                dateTime = moment,
                expenseAmountMilli = 1_000,
            )
        )
    }
}
