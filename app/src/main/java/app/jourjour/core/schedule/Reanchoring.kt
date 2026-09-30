package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import java.time.Instant
import java.time.ZoneId

/**
 * What the rest of the schedule has to do when the clocks under it move.
 *
 * A moment is what an event is stored as, because two events can only be put in order,
 * measured against each other or found to clash as moments — a wall clock cannot do it.
 * Fly west overnight and the landing reads at an earlier hour than the take-off, and
 * dinner after it reads earlier still; sorted by what the clocks say, the evening comes
 * out backwards. So the moment is the key the schedule is read by.
 *
 * But the moment is not what a person entered. They entered a **wall clock** — "dinner at
 * eight" — in whichever place they will be, and that is the thing that must not move. Put
 * a flight in front of dinner and the clocks after it now read four hours later; dinner is
 * still at eight, and its moment is four hours earlier than it was.
 *
 * So the moment is derived and the wall clock is the truth, and this is where the two are
 * put back in step. Every write that can move the zones — an event that changes one being
 * added, retimed, re-zoned or deleted, or the home zone itself changing — reads the wall
 * clocks as they stand, applies the change, lays the zones out again, and gives every
 * event back the clock it had. One transaction, or the schedule is left half in one zone.
 *
 * [pinned] is whatever the person has just typed, which is already saying what it means
 * and is left exactly where it was put.
 */
fun reanchor(
    before: List<ScheduleEvent>,
    after: List<ScheduleEvent>,
    home: ZoneId,
    /** Where the clocks stand afterwards, which differs only when the setting itself changed. */
    homeAfter: ZoneId = home,
    pinned: Set<Long> = emptySet(),
): List<Move> {
    if (after.isEmpty()) return emptyList()
    val wasRead = place(before, Zoning.of(before, home)).associateBy { it.id }

    val moves = mutableListOf<Move>()
    var zone = homeAfter
    // In the order they stand now. Re-anchoring shifts an event by the difference between
    // two offsets, which keeps a day in its day and a morning in its morning, so the order
    // this walk assumes is the order it leaves behind in every case short of a zone
    // change big enough to overtake the next event — and that is a clash, which the
    // schedule shows rather than hides.
    after.sortedWith(byTime).forEach { event ->
        val startZone = event.startsInZone?.let(ZoneId::of) ?: zone
        val wall = wasRead[event.id]?.start?.toLocalDateTime()
        if (wall != null && event.id !in pinned) {
            val anchored = wall.atZone(startZone).toInstant()
            if (anchored.epochSecond != event.startsAt) {
                moves += Move(event.id, anchored.epochSecond)
            }
        }
        zone = event.endsInZone?.let(ZoneId::of) ?: startZone
    }
    return moves
}

/**
 * Whether anything about [event] could move the clocks under what follows it.
 *
 * Saying so is what keeps the re-anchoring rare: most events are a name and two times in
 * whatever zone they find themselves, and writing one has no business rewriting the rest
 * of the trip.
 */
fun movesTheClocks(event: ScheduleEvent?, was: ScheduleEvent?): Boolean {
    val declares = event?.startsInZone != null || event?.endsInZone != null
    val declared = was?.startsInZone != null || was?.endsInZone != null
    if (!declares && !declared) return false
    // A zone-setting event that has been retimed moves the boundary as surely as one whose
    // zone was changed outright, so the times count too.
    return declares != declared ||
        event?.startsInZone != was?.startsInZone ||
        event?.endsInZone != was?.endsInZone ||
        event?.startsAt != was?.startsAt ||
        event?.durationMinutes != was?.durationMinutes
}

/** Where the clocks stand at [instant] on this schedule. */
fun zoneAt(events: List<ScheduleEvent>, home: ZoneId, instant: Instant): ZoneId =
    Zoning.of(events, home).at(instant)
