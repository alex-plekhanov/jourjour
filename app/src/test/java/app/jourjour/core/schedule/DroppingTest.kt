package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Picking an event up and putting it somewhere else.
 *
 * A gap has two ends and they mean opposite things — against the top the event begins
 * where the thing above it ended, against the bottom it ends where the thing below it
 * begins — and the length it had is the length it keeps either way. Nothing is squeezed to
 * fit: where the chain is too long for the gap, that is said rather than worked around.
 */
class DroppingTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 10, 3)

    /** Breakfast, an afternoon out, and dinner, with four free hours in the middle. */
    private val breakfast = event(1, "2026-10-03T09:00", 60)
    private val museum = event(2, "2026-10-03T14:00", 120)
    private val dinner = event(3, "2026-10-03T19:00", 60)
    private val events = listOf(breakfast, museum, dinner)

    @Test
    fun `against the top of a gap it begins where the thing above it ended`() {
        val landing = drop(dinner, gapBetween(), DropEdge.TOP)

        // Breakfast is over at ten, so that is where dinner now starts.
        assertEquals(at("2026-10-03T10:00"), landing.moves.single().startsAt)
        assertTrue(landing.fits)
    }

    @Test
    fun `against the bottom it ends where the thing below it begins`() {
        val landing = drop(breakfast, gapBetween(), DropEdge.BOTTOM)

        // The museum opens at two and breakfast lasts an hour, so it begins at one.
        assertEquals(at("2026-10-03T13:00"), landing.moves.single().startsAt)
        assertTrue(landing.fits)
    }

    @Test
    fun `a chain goes whole, and keeps its shape`() {
        val flight = event(4, "2026-10-04T09:00", 60)
        val bags = event(5, "2026-10-04T10:00", 30, follows = 4)
        val all = events + listOf(flight, bags)
        val landing = drop(flight, gapBetween(all), DropEdge.TOP, all)

        // Both move by the same four and twenty hours, so the bags still come off it.
        assertEquals(
            listOf(at("2026-10-03T10:00"), at("2026-10-03T11:00")),
            landing.moves.map { it.startsAt },
        )
        assertTrue(landing.fits)
    }

    @Test
    fun `a chain too long for the gap says so rather than being squeezed`() {
        val flight = event(4, "2026-10-04T09:00", 180)
        val bags = event(5, "2026-10-04T12:00", 120, follows = 4)
        val all = events + listOf(flight, bags)
        val landing = drop(flight, gapBetween(all), DropEdge.TOP, all)

        // Five hours of chain into four hours of afternoon: it lands, and it clashes.
        assertEquals(at("2026-10-03T10:00"), landing.moves.first().startsAt)
        assertFalse(landing.fits)
    }

    @Test
    fun `an empty day takes the event at the hour it already had`() {
        val fourth = LocalDate.of(2026, 10, 4)
        val zoning = Zoning.of(events, moscow)
        val day = days(date, fourth, zoning).last()
        val landing = requireNotNull(
            landingFor(events, bands(day, place(events, zoning)).single(), day, museum.id, DropEdge.WHOLE, zoning)
        )

        // Two in the afternoon, on the other day. What is being said is which day, not
        // which hour.
        assertEquals(at("2026-10-04T14:00"), landing.moves.single().startsAt)
        assertTrue(landing.fits)
    }

    @Test
    fun `the upper of two that clash drops in front of the lower`() {
        val lunch = event(2, "2026-10-03T13:00", 120)
        val all = listOf(breakfast, lunch, event(3, "2026-10-03T14:00", 60))
        val landing = drop(lunch, overlapOf(all), DropEdge.WHOLE, all)

        // The other one starts at two; two hours of lunch therefore start at noon.
        assertEquals(at("2026-10-03T12:00"), landing.moves.single().startsAt)
        assertTrue(landing.fits)
    }

    @Test
    fun `and the lower drops in behind the upper`() {
        val lunch = event(2, "2026-10-03T13:00", 120)
        val call = event(3, "2026-10-03T14:00", 60)
        val all = listOf(breakfast, lunch, call)
        val landing = drop(call, overlapOf(all), DropEdge.WHOLE, all)

        // Lunch is over at three, so the call goes there.
        assertEquals(at("2026-10-03T15:00"), landing.moves.single().startsAt)
        assertTrue(landing.fits)
    }

    @Test
    fun `an edge with nothing to line up against is no target`() {
        val zoning = Zoning.of(events, moscow)
        val day = days(date, date, zoning).single()
        val opening = bands(day, place(events, zoning))
            .filterIsInstance<GapBand>()
            .first { it.edge == GapEdge.DAY_OPENS }

        // The morning runs from midnight, and midnight is nothing to begin at the end of.
        assertNull(landingFor(events, opening, day, dinner.id, DropEdge.TOP, zoning))
        // Its other end is a target all the same: breakfast is at nine, so an hour of
        // dinner dropped there runs up to it.
        val below = requireNotNull(
            landingFor(events, opening, day, dinner.id, DropEdge.BOTTOM, zoning)
        )
        assertEquals(at("2026-10-03T08:00"), below.moves.single().startsAt)
    }

    @Test
    fun `dropping a thing where it already is asks nothing of anybody`() {
        val zoning = Zoning.of(events, moscow)
        val day = days(date, date, zoning).single()
        val after = bands(day, place(events, zoning))
            .filterIsInstance<GapBand>()
            .first { it.edge == GapEdge.DAY_CLOSES }

        // Dinner is over at eight and the evening begins at eight: no move, no target.
        assertNull(landingFor(events, after, day, dinner.id, DropEdge.TOP, zoning))
    }

    private fun drop(
        dragged: ScheduleEvent,
        band: Band,
        edge: DropEdge,
        all: List<ScheduleEvent> = events,
    ): Landing {
        val zoning = Zoning.of(all, moscow)
        val day = days(date, date, zoning).single()
        return requireNotNull(landingFor(all, band, day, dragged.id, edge, zoning))
    }

    private fun gapBetween(all: List<ScheduleEvent> = events): GapBand {
        val zoning = Zoning.of(all, moscow)
        val day = days(date, date, zoning).single()
        return bands(day, place(all, zoning))
            .filterIsInstance<GapBand>()
            .first { it.edge == GapEdge.BETWEEN }
    }

    private fun overlapOf(all: List<ScheduleEvent>): OverlapBand {
        val zoning = Zoning.of(all, moscow)
        val day = days(date, date, zoning).single()
        return bands(day, place(all, zoning)).filterIsInstance<OverlapBand>().first()
    }

    private fun at(text: String) = LocalDateTime.parse(text).atZone(moscow).toEpochSecond()

    private fun event(id: Long, at: String, minutes: Int, follows: Long? = null) = ScheduleEvent(
        id = id,
        tripId = 1,
        title = "Event $id",
        startsAt = at(at),
        durationMinutes = minutes,
        followsId = follows,
    )
}
