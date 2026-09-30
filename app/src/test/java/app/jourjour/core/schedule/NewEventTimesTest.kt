package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * What tapping empty time puts in the form before a person has typed anything.
 *
 * An hour against whichever end of the gap the tap means, trimmed where the gap is
 * shorter. Which end that is follows from what the gap is next to: a gap after something
 * runs forwards from it, and the one before the first event of a day runs backwards — what
 * goes there is what comes immediately before, not the small hours.
 */
class NewEventTimesTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val nine = LocalTime.of(9, 0)
    private val anHour: Duration = Duration.ofHours(1)

    @Test
    fun `a gap between two things is taken from the front of it`() {
        val offered = offer("2026-10-03T09:00" to 60, "2026-10-03T14:00" to 60, edge = GapEdge.BETWEEN)
        assertEquals("2026-10-03T10:00" to 60, offered)
    }

    @Test
    fun `and trimmed where it is shorter than an hour`() {
        val offered = offer("2026-10-03T09:00" to 60, "2026-10-03T10:20" to 60, edge = GapEdge.BETWEEN)
        assertEquals("2026-10-03T10:00" to 20, offered)
    }

    @Test
    fun `the evening runs on from the last thing of the day`() {
        val offered = offer("2026-10-03T15:00" to 60, edge = GapEdge.DAY_CLOSES)
        assertEquals("2026-10-03T16:00" to 60, offered)
    }

    @Test
    fun `and stops at midnight where less than an hour is left`() {
        val offered = offer("2026-10-03T22:40" to 60, edge = GapEdge.DAY_CLOSES)
        assertEquals("2026-10-03T23:40" to 20, offered)
    }

    @Test
    fun `the morning runs backwards from the first thing of the day`() {
        val offered = offer("2026-10-03T11:00" to 60, edge = GapEdge.DAY_OPENS)
        assertEquals("2026-10-03T10:00" to 60, offered)
    }

    @Test
    fun `and no earlier than midnight`() {
        val offered = offer("2026-10-03T00:30" to 60, edge = GapEdge.DAY_OPENS)
        assertEquals("2026-10-03T00:00" to 30, offered)
    }

    @Test
    fun `an empty day is offered from the hour a day is taken to begin at`() {
        val zoning = Zoning.of(emptyList(), moscow)
        val day = days(DATE, DATE, zoning).single()
        val gap = bands(day, emptyList()).single() as GapBand
        assertEquals("2026-10-03T09:00" to 60, newEventIn(gap, zoning, nine, anHour).readable())
    }

    /** Taps the day's one gap of the given kind. */
    private fun offer(vararg events: Pair<String, Int>, edge: GapEdge): Pair<String, Int> {
        val rows = events.mapIndexed { index, (at, minutes) ->
            ScheduleEvent(
                id = index + 1L,
                tripId = 1,
                startsAt = LocalDateTime.parse(at).atZone(moscow).toEpochSecond(),
                durationMinutes = minutes,
            )
        }
        val zoning = Zoning.of(rows, moscow)
        val day = days(DATE, DATE, zoning).single()
        val gap = bands(day, place(rows, zoning)).filterIsInstance<GapBand>().single { it.edge == edge }
        return newEventIn(gap, zoning, nine, anHour).readable()
    }

    private fun NewEventTimes.readable() =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(startsAt), moscow).toString() to
            durationMinutes

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 3)
    }
}
