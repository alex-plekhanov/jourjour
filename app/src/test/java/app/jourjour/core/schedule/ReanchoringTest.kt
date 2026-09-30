package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The hour a person typed is the thing that must not move.
 *
 * A moment is what an event is stored as, because nothing else can put two events in
 * order. But nobody enters a moment: they enter "dinner at eight", in whichever place they
 * will be. Put a flight in front of dinner and the clocks after it read four hours later —
 * dinner is still at eight, and its moment has changed.
 *
 * These are the four ways that happens: a zone arriving, a zone being retimed, a zone
 * being changed outright, and a zone being deleted. In every one of them the assertion is
 * the same — the clocks still read what they read before.
 */
class ReanchoringTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val newYork = ZoneId.of("America/New_York")

    /** Dinner on the second at eight, written before anything was known about flying. */
    private val dinner = event(id = 2, at = "2026-10-02T20:00", zone = moscow, minutes = 90)

    @Test
    fun `a flight written in front of dinner leaves dinner at eight`() {
        // Written while the whole trip was still read in Moscow.
        val before = listOf(dinner)
        val flight = event(
            id = 1,
            at = "2026-10-01T10:00",
            zone = moscow,
            minutes = 8 * 60,
            endsIn = bangkok,
        )
        val after = listOf(flight, dinner)

        val moves = reanchor(before, after, moscow, pinned = setOf(1))
        assertEquals("2026-10-02T20:00", moved(after, moves, id = 2, readIn = bangkok))
        // And it really did move: four hours of Bangkok is four hours off the moment.
        assertEquals(listOf(2L), moves.map { it.id })
    }

    @Test
    fun `moving the flight to after dinner gives dinner its old clock back`() {
        val flight = event(id = 1, at = "2026-10-01T10:00", zone = moscow, minutes = 8 * 60, endsIn = bangkok)
        val before = listOf(flight, dinner.copy(startsAt = at("2026-10-02T20:00", bangkok)))
        // The flight turns out to be the day after the dinner.
        val later = flight.copy(startsAt = at("2026-10-03T10:00", moscow))
        val after = listOf(later, before[1])

        val moves = reanchor(before, after, moscow, pinned = setOf(1))
        assertEquals("2026-10-02T20:00", moved(after, moves, id = 2, readIn = moscow))
    }

    @Test
    fun `changing where the flight lands leaves dinner at eight in the new place`() {
        val flight = event(id = 1, at = "2026-10-01T10:00", zone = moscow, minutes = 8 * 60, endsIn = bangkok)
        val before = listOf(flight, dinner.copy(startsAt = at("2026-10-02T20:00", bangkok)))
        val elsewhere = flight.copy(endsInZone = newYork.id)
        val after = listOf(elsewhere, before[1])

        val moves = reanchor(before, after, moscow, pinned = setOf(1))
        assertEquals("2026-10-02T20:00", moved(after, moves, id = 2, readIn = newYork))
    }

    @Test
    fun `deleting the flight leaves dinner at eight back at home`() {
        val flight = event(id = 1, at = "2026-10-01T10:00", zone = moscow, minutes = 8 * 60, endsIn = bangkok)
        val before = listOf(flight, dinner.copy(startsAt = at("2026-10-02T20:00", bangkok)))
        val after = listOf(before[1])

        val moves = reanchor(before, after, moscow)
        assertEquals("2026-10-02T20:00", moved(after, moves, id = 2, readIn = moscow))
    }

    @Test
    fun `a zone declared at an event's own start holds from there`() {
        // No flight to blame: a train, a ferry, or simply nobody bothering to write one.
        val before = listOf(dinner)
        val arrival = event(id = 1, at = "2026-10-01T10:00", zone = moscow, minutes = 60, startsIn = bangkok)
        val after = listOf(arrival, dinner)

        val moves = reanchor(before, after, moscow, pinned = setOf(1))
        assertEquals("2026-10-02T20:00", moved(after, moves, id = 2, readIn = bangkok))
    }

    @Test
    fun `changing the home zone moves no clock on any screen`() {
        val events = listOf(dinner, event(id = 3, at = "2026-10-03T09:00", zone = moscow, minutes = 60))

        val moves = reanchor(events, events, moscow, homeAfter = newYork)
        assertEquals("2026-10-02T20:00", moved(events, moves, id = 2, readIn = newYork))
        assertEquals("2026-10-03T09:00", moved(events, moves, id = 3, readIn = newYork))
    }

    @Test
    fun `an event that says nothing about zones rewrites nothing`() {
        val plain = event(id = 5, at = "2026-10-02T10:00", zone = moscow, minutes = 60)
        assertFalse(movesTheClocks(plain, null))
        assertFalse(movesTheClocks(plain.copy(title = "Renamed"), plain))

        val flight = plain.copy(endsInZone = bangkok.id)
        assertTrue(movesTheClocks(flight, plain))
        assertTrue(movesTheClocks(null, flight))
        assertTrue(movesTheClocks(flight.copy(startsAt = flight.startsAt + 3_600), flight))
        assertFalse(movesTheClocks(flight.copy(title = "Renamed"), flight))
    }

    /** What the clock of [id] reads once the moves are applied. */
    private fun moved(
        events: List<ScheduleEvent>,
        moves: List<Move>,
        id: Long,
        readIn: ZoneId,
    ): String {
        val startsAt = moves.firstOrNull { it.id == id }?.startsAt
            ?: events.first { it.id == id }.startsAt
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(startsAt), readIn).toString()
    }

    private fun at(text: String, zone: ZoneId) =
        LocalDateTime.parse(text).atZone(zone).toEpochSecond()

    private fun event(
        id: Long,
        at: String,
        zone: ZoneId,
        minutes: Int,
        startsIn: ZoneId? = null,
        endsIn: ZoneId? = null,
    ) = ScheduleEvent(
        id = id,
        tripId = 1,
        title = "Event $id",
        startsAt = at(at, zone),
        durationMinutes = minutes,
        startsInZone = startsIn?.id,
        endsInZone = endsIn?.id,
    )
}
