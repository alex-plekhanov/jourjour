package app.jourjour.core.schedule

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** When a new event would begin and how long it would last. */
data class NewEventTimes(val startsAt: Long, val durationMinutes: Int)

/**
 * What tapping empty time offers to put in it.
 *
 * The tap says where, the settings say how long. An hour is what most things are, and a
 * tap on a four-hour afternoon means "something goes in here", not "four hours of it" —
 * so the offer is an hour against whichever end of the gap the tap is nearest in meaning,
 * trimmed to fit where the gap is shorter than that.
 *
 * Which end that is follows from what the gap is next to. A gap after something runs
 * forwards from it: the thing being written down is what happens next. The gap before the
 * first event of a day runs backwards from it instead, since what goes there is what comes
 * immediately before — getting to the airport, not the small hours.
 *
 * @param dayBegins the hour a day is taken to start at, which is all an empty day has.
 */
fun newEventIn(
    gap: GapBand,
    zoning: Zoning,
    dayBegins: LocalTime,
    length: Duration,
): NewEventTimes {
    val (from, until) = when (gap.edge) {
        // Forwards from whatever the gap follows.
        GapEdge.BETWEEN, GapEdge.DAY_CLOSES -> gap.from to minOf(gap.from.plus(length), gap.until)

        // Backwards from whatever the gap runs up to, and no earlier than midnight.
        GapEdge.DAY_OPENS -> maxOf(gap.until.minus(length), gap.from) to gap.until

        // A day with nothing in it has no event to run from, so it runs from the hour a
        // day is taken to begin at.
        GapEdge.WHOLE_DAY -> {
            val opening = gap.from.onSameDayAt(dayBegins, zoning).coerceIn(gap.from, gap.until)
            opening to minOf(opening.plus(length), gap.until)
        }
    }
    val minutes = Duration.between(from, until).toMinutes()
    return NewEventTimes(from.epochSecond, minutes.coerceAtLeast(1).toInt())
}

/**
 * The given hour of whatever date it is here — "here" being wherever the clocks have got
 * to by this moment, which on a day that crosses a zone is not where they started.
 */
private fun Instant.onSameDayAt(time: LocalTime, zoning: Zoning): Instant {
    val zone = zoning.at(this)
    return atZone(zone).toLocalDate().atTime(time).atZone(zone).toInstant()
}

private fun Instant.coerceIn(from: Instant, until: Instant): Instant =
    if (isBefore(from)) from else if (isAfter(until)) until else this
