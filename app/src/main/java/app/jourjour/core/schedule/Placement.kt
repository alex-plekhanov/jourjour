package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * An event with the zones it is read in — the form everything on the schedule tab works
 * from, since the stored row knows only moments and the person reads clocks.
 *
 * [startZone] and [endZone] differ exactly when the event moves between them, which is
 * why a flight can end at an earlier hour than it began without ending earlier.
 */
data class PlacedEvent(
    val event: ScheduleEvent,
    val startZone: ZoneId,
    val endZone: ZoneId,
    /**
     * The zone this event would be read in had it declared nothing — which is what its
     * own start is worth saying anything about: a clock only needs announcing where this
     * event is what moved it.
     *
     * Not the zone of the minute before. A flight that lands in Moscow announces the move
     * at its end, and the event chained onto it inherits Moscow without moving anything;
     * read against the minute before, which was still the flight's own zone, that event
     * would announce the same hour a second time and the two would read as two moves.
     */
    val precedingZone: ZoneId = startZone,
) {
    val id: Long get() = event.id

    val duration: Duration = Duration.ofMinutes(event.durationMinutes.toLong())
    val startsAt: Instant = Instant.ofEpochSecond(event.startsAt)
    val endsAt: Instant = startsAt.plus(duration)

    /** The clock a person would have looked at when it began. */
    val start: ZonedDateTime get() = startsAt.atZone(startZone)

    /** And the one where they are when it ends, which may read earlier. */
    val end: ZonedDateTime get() = endsAt.atZone(endZone)
}

/**
 * How far the clocks moved at this event's start, in seconds, or nothing where they did
 * not — which is almost every event.
 *
 * Read against the minute before: a clock is only worth announcing where it has changed.
 */
val PlacedEvent.startShift: Long?
    get() = shiftBetween(precedingZone, startZone, startsAt)

/** And at its end, against what they read when it began. */
val PlacedEvent.endShift: Long?
    get() = shiftBetween(startZone, endZone, endsAt)

private fun PlacedEvent.shiftBetween(from: ZoneId, to: ZoneId, at: Instant): Long? {
    val moved = to.rules.getOffset(at).totalSeconds - from.rules.getOffset(startsAt).totalSeconds
    return moved.toLong().takeIf { it != 0L }
}

/**
 * How far the clocks moved, in hours — or in hours and minutes where a zone is odd.
 *
 * Written as a ticket writes the day it lands on: a sign and a figure, raised off the hour
 * it belongs to rather than standing beside it as a second thing to read.
 */
fun shiftLabel(seconds: Long): String {
    val sign = if (seconds < 0) "−" else "+"
    val minutes = kotlin.math.abs(seconds) / 60
    return if (minutes % 60 == 0L) {
        "$sign${minutes / 60}"
    } else {
        "$sign${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }
}

/**
 * Which zone the trip is in at any moment of it.
 *
 * Zones are stated at two kinds of point and inherited everywhere between: where the
 * schedule begins, and wherever an event lands somewhere other than it took off. Nothing
 * else carries one, so moving a flight changes how every later day is read without a
 * single other row being written.
 */
class Zoning private constructor(
    private val initial: ZoneId,
    /** Each moment the zone becomes something else, earliest first. */
    private val changes: List<Change>,
) {
    /** @param startOf the event that declared it at its own start, or null for a landing. */
    private data class Change(val at: Instant, val zone: ZoneId, val startOf: Long? = null)

    /** What the clocks read before anything has moved them. */
    val initialZone: ZoneId get() = initial

    /** What the clocks say at [instant]. A zone declared for that very moment counts. */
    fun at(instant: Instant): ZoneId =
        changes.lastOrNull { !it.at.isAfter(instant) }?.zone ?: initial

    /**
     * What the clocks say at [instant] to an event that has not spoken — everything the
     * rest of the schedule has settled by then, with [eventId]'s own declaration left out.
     *
     * The difference between this and [at] is the whole of what an event's start has to
     * announce: where the two agree the event inherited its zone, and an inherited zone
     * has moved nothing.
     */
    fun inheritedBy(eventId: Long, instant: Instant): ZoneId =
        changes.lastOrNull { !it.at.isAfter(instant) && it.startOf != eventId }?.zone ?: initial

    /** The next moment the clocks change after [instant], where there is one. */
    fun firstChangeAfter(instant: Instant): Instant? =
        changes.firstOrNull { it.at.isAfter(instant) }?.at

    companion object {
        /**
         * [home] is where the clocks stand before the trip has moved them — a setting,
         * not a column, and not the phone's zone either: the phone says where a person is
         * now, which halfway through a trip is the wrong end of it.
         *
         * An event may declare a zone at either end. At its start it means "from here on
         * we are in X", which is what somebody writes when nothing flew them there; at its
         * end it means "this is what took us", which is a flight. Either way it holds
         * until the next event that says otherwise.
         */
        fun of(events: List<ScheduleEvent>, home: ZoneId): Zoning {
            val changes = events.flatMap { event ->
                val startsAt = Instant.ofEpochSecond(event.startsAt)
                buildList {
                    event.startsInZone?.let {
                        add(Change(startsAt, ZoneId.of(it), startOf = event.id))
                    }
                    event.endsInZone?.let {
                        add(Change(startsAt.plusSeconds(event.durationMinutes * 60L), ZoneId.of(it)))
                    }
                }
            }.sortedBy { it.at }
            return Zoning(home, changes)
        }
    }
}

/** Events in the order they happen, each knowing the clocks at either end of it. */
fun place(events: List<ScheduleEvent>, zoning: Zoning): List<PlacedEvent> =
    events.sortedWith(byTime).map { event ->
        val startsAt = Instant.ofEpochSecond(event.startsAt)
        val startZone = zoning.at(startsAt)
        PlacedEvent(
            event = event,
            startZone = startZone,
            endZone = event.endsInZone?.let(ZoneId::of) ?: startZone,
            precedingZone = zoning.inheritedBy(event.id, startsAt),
        )
    }

/**
 * The order the schedule is read in. Two events beginning at the same moment are put in
 * the order they were written, so that a list does not shuffle itself between readings.
 */
internal val byTime: Comparator<ScheduleEvent> = compareBy({ it.startsAt }, { it.id })
