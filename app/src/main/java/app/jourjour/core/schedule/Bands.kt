package app.jourjour.core.schedule

import java.time.Duration
import java.time.Instant

/**
 * A day of the schedule is read as an unbroken column: every minute of it belongs to
 * something, and the something is one of three bands.
 *
 * Nothing is laid out to scale — an hour and a fortnight take the same room — because a
 * schedule is read as a list of what happens next, not as a chart of how long it takes.
 * What the bands say instead is what touches what.
 */
sealed interface Band {
    val from: Instant
    val until: Instant

    val length: Duration get() = Duration.between(from, until)
}

/**
 * An event, or the part of one that falls inside this day. [part] says which corners are
 * rounded: an event carried over into tomorrow is cut square along the join, and the same
 * event appears again under tomorrow's heading square along its top.
 */
data class EventBand(
    val placed: PlacedEvent,
    val part: EventPart,
    override val from: Instant,
    override val until: Instant,
) : Band

/** Which day of an event this is, for an event that does not fit in one. */
enum class EventPart {
    /** Begins and ends inside the day: rounded all round. */
    WHOLE,

    /** Begins here and runs on: square along the bottom. */
    STARTS,

    /** Neither end is in this day, which takes a day and a half of flying. */
    CONTINUES,

    /** Ends here, having begun yesterday: square along the top. */
    ENDS,
}

/** Time with nothing in it. Somewhere to tap, and the answer to "when am I free". */
data class GapBand(
    override val from: Instant,
    override val until: Instant,
    val edge: GapEdge,
) : Band

/**
 * Where a gap sits in its day, which decides whether it is worth putting a length on.
 * Three hours between two things is the useful number; the hours before the first thing
 * of the day and after the last are the night, and saying how long the night is helps
 * nobody.
 */
enum class GapEdge {
    /** Between midnight and the first event. */
    DAY_OPENS,

    /** Between two events. */
    BETWEEN,

    /** Between the last event and midnight. */
    DAY_CLOSES,

    /** A day with nothing in it at all. */
    WHOLE_DAY,
}

/**
 * Minutes booked twice. Drawn between the two events that claim them, square against
 * both, so that the column stays unbroken and the clash is impossible to scroll past.
 */
data class OverlapBand(
    val before: PlacedEvent,
    val after: PlacedEvent,
    override val from: Instant,
    override val until: Instant,
) : Band

/**
 * Two events with nothing between them: one ends exactly where the next begins.
 *
 * That is worth drawing, because it is the difference between a coincidence and a
 * consequence — the bags come off *this* flight, and if the flight moves they move with
 * it. [linked] is which of the two it is, and the band is where a person says so.
 *
 * Where the join falls on midnight the chain is drawn twice, once under each day's
 * heading, since neither day can show a join that happened in the other. Both are the
 * same link and either tap settles it.
 */
data class ChainBand(
    val before: PlacedEvent,
    val after: PlacedEvent,
    val linked: Boolean,
    override val from: Instant,
    override val until: Instant,
) : Band

/**
 * The day cut into bands, in order, leaving no minute unaccounted for.
 *
 * An event is compared against the furthest any event has reached so far rather than
 * against the one drawn immediately above it: a long afternoon with two short things
 * booked inside it clashes with both, and only the running reach notices the second.
 */
fun bands(day: ScheduleDay, placed: List<PlacedEvent>): List<Band> {
    val inDay = placed.filter { it.endsAt.isAfter(day.from) && it.startsAt.isBefore(day.until) }
    if (inDay.isEmpty()) return listOf(GapBand(day.from, day.until, GapEdge.WHOLE_DAY))

    val bands = mutableListOf<Band>()
    var cursor = day.from
    var reached: PlacedEvent? = null
    inDay.forEach { event ->
        when {
            event.startsAt.isAfter(cursor) -> bands += GapBand(
                from = cursor,
                until = event.startsAt,
                edge = if (bands.isEmpty()) GapEdge.DAY_OPENS else GapEdge.BETWEEN,
            )
            // Only a clash that begins inside the day is drawn: an event running over
            // midnight into another already shows itself, and the band belongs to the
            // day the second one starts in.
            event.startsAt.isBefore(cursor) && reached != null && !event.startsAt.isBefore(day.from) ->
                bands += OverlapBand(reached!!, event, event.startsAt, cursor)
            // Flush against what came before, which may be yesterday's last event where
            // the join is midnight itself.
            else -> chain(reached ?: placed.endingAt(day.from), event)?.let { bands += it }
        }
        bands += EventBand(
            placed = event,
            part = partOf(event, day),
            from = maxOf(event.startsAt, day.from),
            until = minOf(event.endsAt, day.until),
        )
        if (event.endsAt.isAfter(cursor)) {
            cursor = event.endsAt
            reached = event
        }
    }
    when {
        cursor.isBefore(day.until) -> bands += GapBand(cursor, day.until, GapEdge.DAY_CLOSES)
        // The day runs out exactly where tomorrow's first event begins: the same join,
        // shown again under this heading because tomorrow's copy is out of sight.
        cursor == day.until -> chain(reached, placed.startingAt(day.until))?.let { bands += it }
    }
    return bands
}

/** The chain between two events that touch, or nothing where one of them is not there. */
private fun chain(before: PlacedEvent?, after: PlacedEvent?): ChainBand? {
    if (before == null || after == null || before.endsAt != after.startsAt) return null
    return ChainBand(
        before = before,
        after = after,
        linked = after.event.followsId == before.id,
        from = after.startsAt,
        until = after.startsAt,
    )
}

private fun List<PlacedEvent>.endingAt(instant: Instant) = lastOrNull { it.endsAt == instant }

private fun List<PlacedEvent>.startingAt(instant: Instant) = firstOrNull { it.startsAt == instant }

private fun partOf(event: PlacedEvent, day: ScheduleDay): EventPart {
    val begins = !event.startsAt.isBefore(day.from)
    val ends = !event.endsAt.isAfter(day.until)
    return when {
        begins && ends -> EventPart.WHOLE
        begins -> EventPart.STARTS
        ends -> EventPart.ENDS
        else -> EventPart.CONTINUES
    }
}
