package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent

/** One event told to begin somewhere else than it does. */
data class Move(val id: Long, val startsAt: Long)

/** Two events laying claim to the same minutes, and how many of them. */
data class Clash(val before: ScheduleEvent, val after: ScheduleEvent, val minutes: Long)

/** Where the plan books the same time twice, earliest first. */
fun clashes(events: List<ScheduleEvent>): List<Clash> {
    val clashes = mutableListOf<Clash>()
    var reach: ScheduleEvent? = null
    events.sortedWith(byTime).forEach { event ->
        val held = reach
        if (held != null && event.startsAt < held.endsAt) {
            clashes += Clash(held, event, (held.endsAt - event.startsAt) / 60)
        }
        if (held == null || event.endsAt > held.endsAt) reach = event
    }
    return clashes
}

/**
 * What the schedule would have to do to make room for the events in [pinned].
 *
 * Two rules, applied until nothing more moves. An event linked to another begins where
 * that one ends, wherever it has gone. An event sat upon by what now reaches further than
 * it does is pushed forward exactly far enough to be clear — and no further, so the empty
 * hours after it take up as much of the push as they are wide and the day's last event
 * often does not move at all.
 *
 * Nothing is ever pushed backwards. Moving something earlier can only run it into what
 * came before, and clearing the way there would mean shoving the morning into yesterday.
 *
 * The result is what a transaction would write; whether to write it is somebody else's
 * question, asked after they have seen how much moves.
 *
 * @param push whether to make room at all. Off, only the links are kept — which is not
 * optional: an event tied to another has no time of its own, so that much is written
 * whatever a person decides about the clash.
 */
fun settle(events: List<ScheduleEvent>, pinned: Set<Long>, push: Boolean = true): List<Move> {
    if (events.isEmpty()) return emptyList()
    val byId = events.associateBy { it.id }
    val starts = events.associate { it.id to it.startsAt }.toMutableMap()
    fun startOf(event: ScheduleEvent) = starts.getValue(event.id)
    fun endOf(event: ScheduleEvent) = startOf(event) + event.durationMinutes * 60L

    // One pass can move an event that a later pass moves again — a chain of three
    // settles from the front — so passes run until a whole one changes nothing. There
    // can be no more of them than there are events, since each pass fixes at least the
    // earliest event still out of place.
    for (pass in 0..events.size) {
        var moved = false

        events.sortedBy(::startOf).forEach { event ->
            val leader = event.followsId?.takeIf { event.id !in pinned }?.let(byId::get) ?: return@forEach
            val flush = endOf(leader)
            if (startOf(event) != flush) {
                starts[event.id] = flush
                moved = true
            }
        }

        if (push) {
            var reach = Long.MIN_VALUE
            // Two things beginning at the same moment have to be put in some order, and
            // the order decides which of them is pushed. What is pinned holds its place —
            // that is what pinning it means — so it goes first; then whatever is tied to
            // what came before it, so that a chain stays a chain; then the rest, which is
            // what gets out of the way. Left to the order of the list, a copy laid exactly
            // over its original was never pushed at all: the original happened to come
            // first, sat there, and the copy, being pinned, could not move either.
            events.sortedWith(compareBy(::startOf, { it.pushOrder(pinned) })).forEach { event ->
                if (startOf(event) < reach && event.id !in pinned) {
                    starts[event.id] = reach
                    moved = true
                }
                reach = maxOf(reach, endOf(event))
            }
        }

        if (!moved) break
    }

    return events.mapNotNull { event ->
        starts.getValue(event.id).takeIf { it != event.startsAt }?.let { Move(event.id, it) }
    }
}

/** Which of several events beginning together gives way last: the lower, the later. */
private fun ScheduleEvent.pushOrder(pinned: Set<Long>): Int = when {
    id in pinned -> 0
    followsId != null -> 1
    else -> 2
}

/**
 * The event a new one would most naturally follow: the one whose end is nearest the time
 * typed in, out of those that have already finished by then.
 *
 * There is no list to pick from because there is nothing to pick — a person writing down
 * "collect the bags" at twenty past five means the flight that landed at ten past, and
 * being shown every event of the trip to say so is work for nothing.
 */
fun nearestBefore(events: List<ScheduleEvent>, startsAt: Long, excluding: Long = 0): ScheduleEvent? =
    events.asSequence()
        // Only what began earlier, which is what keeps a chain from closing on itself.
        .filter { it.id != excluding && it.startsAt < startsAt }
        .minWithOrNull(compareBy({ kotlin.math.abs(it.endsAt - startsAt) }, { -it.id }))

/** Where the event ends, in seconds since the epoch. */
val ScheduleEvent.endsAt: Long get() = startsAt + durationMinutes * 60L
