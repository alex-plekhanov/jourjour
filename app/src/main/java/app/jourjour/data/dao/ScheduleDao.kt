package app.jourjour.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.jourjour.data.entity.ScheduleDayNote
import app.jourjour.data.entity.ScheduleEvent
import app.jourjour.data.entity.ScheduleEventDetails
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduleDao {

    /**
     * A trip's schedule in the order it happens. Two events beginning at the same moment
     * keep the order they were written in, so that a list does not shuffle itself.
     */
    @Transaction
    @Query("SELECT * FROM schedule_events WHERE trip_id = :tripId ORDER BY starts_at, id")
    fun observeByTrip(tripId: Long): Flow<List<ScheduleEventDetails>>

    /** The same, read once, for a dump or a table. */
    @Transaction
    @Query("SELECT * FROM schedule_events WHERE trip_id = :tripId ORDER BY starts_at, id")
    suspend fun getByTrip(tripId: Long): List<ScheduleEventDetails>

    /** The rows themselves, which is what the timeline arithmetic works on. */
    @Query("SELECT * FROM schedule_events WHERE trip_id = :tripId ORDER BY starts_at, id")
    suspend fun eventsOf(tripId: Long): List<ScheduleEvent>

    @Transaction
    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEventDetails?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ScheduleEvent): Long

    @Update
    suspend fun update(event: ScheduleEvent)

    @Query("UPDATE schedule_events SET starts_at = :startsAt WHERE id = :id")
    suspend fun moveTo(id: Long, startsAt: Long)

    /** Ties an event to the one before it, once both have the ids they will keep. */
    @Query("UPDATE schedule_events SET follows_id = :followsId WHERE id = :id")
    suspend fun setFollows(id: Long, followsId: Long?)

    /** Every trip that has a schedule at all, which is what a home-zone change touches. */
    @Query("SELECT DISTINCT trip_id FROM schedule_events")
    suspend fun tripsWithEvents(): List<Long>

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** What each day of a trip is about, for the headings to carry. */
    @Query("SELECT * FROM schedule_days WHERE trip_id = :tripId ORDER BY date")
    fun observeDays(tripId: Long): Flow<List<ScheduleDayNote>>

    /** The same, read once, for a dump. */
    @Query("SELECT * FROM schedule_days WHERE trip_id = :tripId ORDER BY date")
    suspend fun daysOf(tripId: Long): List<ScheduleDayNote>

    /**
     * Writes what a day is about. Replacing on conflict is what makes it one line per
     * day: the unique index is the row's identity, and the id it happens to carry is not.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun describeDay(note: ScheduleDayNote)

    /** A day with nothing left to say keeps no row; see [ScheduleDayNote]. */
    @Query("DELETE FROM schedule_days WHERE trip_id = :tripId AND date = :date")
    suspend fun undescribeDay(tripId: Long, date: LocalDate)
}
