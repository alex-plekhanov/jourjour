package app.jourjour.repository

import androidx.room.withTransaction
import app.jourjour.core.schedule.Move
import app.jourjour.core.schedule.Zoning
import app.jourjour.core.schedule.days
import app.jourjour.core.schedule.endsAt
import app.jourjour.core.schedule.movesTheClocks
import app.jourjour.core.schedule.reanchor
import app.jourjour.data.dao.ScheduleDao
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.ScheduleDayNote
import app.jourjour.data.entity.ScheduleEvent
import app.jourjour.data.entity.ScheduleEventDetails
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: ScheduleDao,
    private val budget: BudgetRepository,
) {

    fun observeByTrip(tripId: Long): Flow<List<ScheduleEventDetails>> = dao.observeByTrip(tripId)

    suspend fun eventsOf(tripId: Long): List<ScheduleEvent> = dao.eventsOf(tripId)

    /** What each day of the trip is about, by date. */
    fun observeDays(tripId: Long): Flow<List<ScheduleDayNote>> = dao.observeDays(tripId)

    /**
     * Says what a day is about, or takes back what was said.
     *
     * Blank is not a description but the absence of one, so it removes the row rather than
     * storing an empty string: a day with nothing to say should read the same whether
     * nobody has written anything yet or somebody has rubbed it out.
     */
    suspend fun describe(tripId: Long, date: LocalDate, summary: String) {
        val said = summary.trim()
        if (said.isEmpty()) {
            dao.undescribeDay(tripId, date)
        } else {
            dao.describeDay(ScheduleDayNote(tripId = tripId, date = date, summary = said))
        }
    }

    suspend fun get(id: Long): ScheduleEventDetails? = dao.getById(id)

    /**
     * Writes an event together with everything its arrival displaced — what it pushed out
     * of the way, and what it moved the clocks under.
     *
     * One transaction, because half of either is worse than none: an event landing in the
     * middle of an afternoon that has not moved out of its way, or a trip half of which is
     * still reading Moscow time. It is also what makes cancelling free — nothing is
     * written until Save.
     */
    suspend fun save(
        event: ScheduleEvent,
        home: ZoneId,
        moves: List<Move> = emptyList(),
    ): Long = db.withTransaction {
        val before = dao.eventsOf(event.tripId)
        val id = if (event.id == 0L) dao.insert(event) else dao.update(event).let { event.id }
        moves.filter { it.id != id }.forEach { dao.moveTo(it.id, it.startsAt) }

        val was = before.firstOrNull { it.id == event.id }
        if (movesTheClocks(event, was)) {
            reanchor(before, dao.eventsOf(event.tripId), home, pinned = setOf(id))
                .forEach { dao.moveTo(it.id, it.startsAt) }
        }
        forget(event.tripId, was?.categoryId, event.categoryId)
        id
    }

    /**
     * Ties an event to the one it touches, or lets it go.
     *
     * Nothing moves either way: the two already begin and end at the same moment, which is
     * the only reason there was anything to tie. What changes is what happens next time
     * the first one does.
     */
    suspend fun setFollows(id: Long, followsId: Long?) = dao.setFollows(id, followsId)

    /**
     * A table read in lands as one write: half a schedule is worse than none.
     *
     * The clocks are laid out again at the end of it. A file may carry a flight, and
     * everything already in the schedule after that flight was written on the old clock.
     */
    suspend fun addAll(events: List<ScheduleEvent>, home: ZoneId): List<Long> {
        if (events.isEmpty()) return emptyList()
        return db.withTransaction {
            val tripId = events.first().tripId
            val before = dao.eventsOf(tripId)
            val added = events.map { dao.insert(it) }
            if (events.any { movesTheClocks(it, null) }) {
                reanchor(before, dao.eventsOf(tripId), home, pinned = added.toSet())
                    .forEach { dao.moveTo(it.id, it.startsAt) }
            }
            added
        }
    }

    /**
     * Moves events already written — a chain dropped somewhere else — in one transaction.
     *
     * The clocks are laid out again where one of the moved events is what moves them: a
     * flight dragged to another morning takes the zone with it, and everything after
     * either of its old and new places has to be read again.
     */
    suspend fun moveAll(tripId: Long, moves: List<Move>, home: ZoneId) {
        if (moves.isEmpty()) return
        db.withTransaction {
            val before = dao.eventsOf(tripId)
            moves.forEach { dao.moveTo(it.id, it.startsAt) }
            val moved = moves.mapTo(mutableSetOf()) { it.id }
            if (before.any { it.id in moved && movesTheClocks(it, null) }) {
                reanchor(before, dao.eventsOf(tripId), home, pinned = moved)
                    .forEach { dao.moveTo(it.id, it.startsAt) }
            }
        }
    }

    /**
     * Takes an event. Whatever followed it keeps the time it had, having lost its link —
     * and, where the event was the one that moved the clocks, keeps the hour it was
     * written at rather than the moment that hour used to mean.
     */
    suspend fun delete(event: ScheduleEvent, home: ZoneId) = db.withTransaction {
        val before = dao.eventsOf(event.tripId)
        dao.deleteById(event.id)
        if (movesTheClocks(null, event)) {
            reanchor(before, dao.eventsOf(event.tripId), home)
                .forEach { dao.moveTo(it.id, it.startsAt) }
        }
        forget(event.tripId, event.categoryId)
    }

    /**
     * Forgets what the budget had settled about any category this write may have emptied.
     *
     * The money on an event can move from one category to another or go altogether, and a
     * category with nothing left in the budget has nothing left to declare closed — a flag
     * kept there would be invisible until the day it surprised somebody.
     */
    private suspend fun forget(tripId: Long, vararg categoryIds: Long?) {
        categoryIds.filterNotNull().distinct().forEach { budget.forgetIfEmpty(tripId, it) }
    }

    /**
     * How many of a trip's events would fall outside these dates, before them and after.
     *
     * Asked by the trip form before it shortens a trip: a schedule is what happens *on*
     * the trip, and dates that no longer cover it would leave events the schedule cannot
     * show and the form cannot reach.
     */
    suspend fun countOutside(
        tripId: Long,
        from: LocalDate,
        until: LocalDate,
        home: ZoneId,
    ): Pair<Int, Int> {
        val events = dao.eventsOf(tripId)
        if (events.isEmpty()) return 0 to 0
        val days = days(from, until, Zoning.of(events, home))
        val opens = days.first().from
        val closes = days.last().until
        return events.count { Instant.ofEpochSecond(it.startsAt) < opens } to
            events.count { Instant.ofEpochSecond(it.endsAt) > closes }
    }

    /** Whether any trip has a schedule at all, which is what a home-zone change touches. */
    suspend fun anyEvents(): Boolean = dao.tripsWithEvents().isNotEmpty()

    /**
     * Gives every trip its clocks back after the home zone has been changed.
     *
     * Nothing on any screen moves: every event keeps the hour it was written at, which is
     * the whole point. What changes is what those hours mean, which is what the setting is
     * for — and leaving it out would silently walk a whole schedule sideways instead.
     */
    suspend fun rehome(was: ZoneId, now: ZoneId) {
        if (was == now) return
        db.withTransaction {
            dao.tripsWithEvents().forEach { tripId ->
                val events = dao.eventsOf(tripId)
                reanchor(events, events, was, now).forEach { dao.moveTo(it.id, it.startsAt) }
            }
        }
    }
}
