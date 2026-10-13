package app.jourjour.core.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day of a trip as a stretch of the timeline.
 *
 * A day is not twenty-four hours. It runs from one local midnight to the next, and the
 * clocks may have moved in between: a trip going west gets a day of thirty-one hours,
 * one going east a day of seventeen, and the weekend the clocks change gets twenty-three.
 * [from] and [until] are where those midnights actually fall.
 *
 * [zone] is what the clocks read at the start of it. Later in the day they may read
 * something else, which is the point of [Zoning].
 */
data class ScheduleDay(
    val date: LocalDate,
    val from: Instant,
    val until: Instant,
    val zone: ZoneId,
)

/**
 * The days of a trip, one per date between its first and its last.
 *
 * The dates are counted off rather than read back from the clocks, because the trip's own
 * dates are what the schedule is allowed to hold and a person expects as many headings as
 * the trip has days. The two agree everywhere except across the date line, where a
 * calendar date is skipped or lived twice; there the heading keeps counting and the day it
 * names may have no room in it at all — which is why [ScheduleDay] is allowed to be empty.
 */
fun days(startDate: LocalDate, endDate: LocalDate, zoning: Zoning): List<ScheduleDay> {
    var from = startDate.atStartOfDay(zoning.initialZone).toInstant()
    return generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .map { date ->
            val zone = zoning.at(from)
            val until = nextMidnight(from, zoning)
            ScheduleDay(date, from, until, zone).also { from = until }
        }
        .toList()
}

/**
 * Where the day that began at [from] ends: the midnight after which the date stays turned
 * over, read on whatever clock is in force when it arrives.
 *
 * Two kinds of change make the first midnight the wrong one, and the walk has to carry on
 * past both.
 *
 * A change **before** that midnight means the midnight being waited for was never the one:
 * the clocks moved first, and the midnight to wait for is the next one they read.
 *
 * A change **after** it matters too, where the new clock puts the date back to the day we
 * were in. That is what flying west over midnight does: the date turns over in the air on
 * the clock nobody has reset yet, and turns back on landing. A day ended at the first of
 * those two midnights cuts the flight in half and hands the second half to a heading the
 * flight never reached — and every heading after it, and the trip's own last minute, is
 * then a zone out. A day going west is simply longer, which is the rule everywhere else
 * here, and this is the same rule where the landing is what moves the clocks.
 */
private fun nextMidnight(from: Instant, zoning: Zoning): Instant {
    val date = from.atZone(zoning.at(from)).toLocalDate()
    var cursor = from
    var midnight = from.midnightAfter(zoning.at(from))
    while (true) {
        val change = zoning.firstChangeAfter(cursor) ?: return midnight
        val zone = zoning.at(change)
        val resumes = !change.atZone(zone).toLocalDate().isAfter(date)
        if (!change.isBefore(midnight) && !resumes) return midnight
        cursor = change
        midnight = change.midnightAfter(zone)
    }
}

/** The first midnight after this moment on that clock, which is where a day of it ends. */
private fun Instant.midnightAfter(zone: ZoneId): Instant =
    atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
