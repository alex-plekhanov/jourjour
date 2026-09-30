package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import java.time.Instant

/**
 * Which part of a band a dragged event is aimed at.
 *
 * Empty time has two ends and they mean opposite things: let go against the top of a gap
 * and the event begins where the thing above it ended; against the bottom, and it ends
 * where the thing below it begins, keeping the length it had. A whole band is aimed at as
 * one — an empty day, or the minutes two events are fighting over.
 */
enum class DropEdge { TOP, BOTTOM, WHOLE }

/** Where a dragged chain would go if it were let go here, and whether it would fit. */
data class Landing(val moves: List<Move>, val fits: Boolean)

/**
 * What dropping [draggedId] on this band would do.
 *
 * The whole chain goes: an event tied to another has no time of its own, so dragging the
 * flight takes the bags with it. What is offered is a place, not a squeeze — nothing is
 * shortened and nothing is pushed out of the way here. Where the chain does not fit,
 * [Landing.fits] says so and the drop is left to the form, which can say what it runs into
 * and offer to move the rest along.
 *
 * @return null where this is no target at all: a band nothing can be dropped on, an edge
 *   with nothing to line up against, or the place the chain already is.
 */
fun landingFor(
    events: List<ScheduleEvent>,
    band: Band,
    day: ScheduleDay,
    draggedId: Long,
    edge: DropEdge,
    zoning: Zoning,
    within: ClosedRange<Instant>? = null,
): Landing? {
    val chain = chainFrom(events, draggedId)
    val leader = chain.firstOrNull() ?: return null
    val ids = chain.mapTo(mutableSetOf()) { it.id }
    // What the chain takes up, end to end. Its links hold it flush, so moving the leader
    // moves the lot by the same amount and the shape of it never changes.
    val span = chain.maxOf { it.endsAt } - leader.startsAt

    val startsAt = when (band) {
        is GapBand -> when (edge) {
            // Against the top: begin where whatever the gap follows ended.
            DropEdge.TOP -> {
                val before = events.lastOrNull { it.endsAt == band.from.epochSecond }
                if (before == null || before.id in ids) return null
                band.from.epochSecond
            }
            // Against the bottom: end where whatever the gap runs up to begins.
            DropEdge.BOTTOM -> {
                val after = events.firstOrNull { it.startsAt == band.until.epochSecond }
                if (after == null || after.id in ids) return null
                band.until.epochSecond - span
            }
            // A day with nothing in it takes the event at the hour it already has: what
            // is being said is "this happens on that day", not "at some other time".
            DropEdge.WHOLE -> {
                if (band.edge != GapEdge.WHOLE_DAY) return null
                leader.onTheDayOf(day, zoning)
            }
        }
        // Minutes claimed twice are aimed at to settle which of the two gives way: the
        // upper one drops in front of the lower, the lower in behind the upper.
        is OverlapBand -> when (draggedId) {
            band.before.id -> band.after.startsAt.epochSecond - span
            band.after.id -> band.before.endsAt.epochSecond
            else -> return null
        }
        else -> return null
    }

    val moved = startsAt - leader.startsAt
    if (moved == 0L) return null

    val moves = chain.map { Move(it.id, it.startsAt + moved) }
    val after = events.applying(moves)
    val clashing = clashes(after).any { it.before.id in ids || it.after.id in ids }
    val inside = within == null || after.none {
        it.id in ids &&
            (Instant.ofEpochSecond(it.startsAt) < within.start ||
                Instant.ofEpochSecond(it.endsAt) > within.endInclusive)
    }
    return Landing(moves, fits = !clashing && inside)
}

/**
 * An event and everything tied to it, in the order they happen.
 *
 * Dragging one of these drags all of them, which is what the link means: the bags come off
 * *this* flight, and a flight that moves without them would be saying the opposite.
 */
fun chainFrom(events: List<ScheduleEvent>, id: Long): List<ScheduleEvent> {
    val followers = events.filter { it.followsId != null }.groupBy { requireNotNull(it.followsId) }
    val chain = mutableListOf<ScheduleEvent>()
    val queue = ArrayDeque(listOfNotNull(events.firstOrNull { it.id == id }))
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        chain += next
        followers[next.id]?.forEach(queue::addLast)
    }
    return chain.sortedWith(byTime)
}

/** The same events with the moves applied, which is the state a question is asked of. */
fun List<ScheduleEvent>.applying(moves: List<Move>): List<ScheduleEvent> {
    if (moves.isEmpty()) return this
    val byId = moves.associate { it.id to it.startsAt }
    return map { event -> byId[event.id]?.let { event.copy(startsAt = it) } ?: event }
}

/** The hour this event already has, on that day's date and whatever clock is in force there. */
private fun ScheduleEvent.onTheDayOf(day: ScheduleDay, zoning: Zoning): Long {
    val at = Instant.ofEpochSecond(startsAt)
    val wall = at.atZone(zoning.at(at)).toLocalTime()
    val zone = zoning.at(day.from)
    return day.date.atTime(wall).atZone(zone).toEpochSecond()
}
