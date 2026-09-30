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
 * Where the day that began at [from] ends: the first midnight after it, read on whatever
 * clock is in force when it arrives.
 *
 * The clock may change before that midnight is reached, and then the midnight to wait for
 * is a different one — so the search walks from one change to the next. A midnight that
 * falls *before* the next change has already ended the day and is taken as it stands: the
 * date turns over mid-flight, and the flight is drawn across two headings.
 */
private fun nextMidnight(from: Instant, zoning: Zoning): Instant {
    var cursor = from
    while (true) {
        val zone = zoning.at(cursor)
        val midnight = cursor.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val change = zoning.firstChangeAfter(cursor)
        if (change == null || !change.isBefore(midnight)) return midnight
        cursor = change
    }
}
