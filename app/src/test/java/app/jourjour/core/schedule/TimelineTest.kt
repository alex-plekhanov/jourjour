package app.jourjour.core.schedule

import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A schedule read against the clocks a person actually looks at.
 *
 * Everything here turns on one decision: an event is a moment, not a wall clock. These
 * tests are the case for it — a flight that lands at an earlier hour than it left, a day
 * of thirty-one hours, a day of twenty-three when the clocks go forward — none of which
 * can be written down as a pair of local date-times.
 */
class TimelineTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `a flight lands at an earlier hour than it left, and still later`() {
        // Out of Moscow at 13:00 on the third, ten hours in the air, into New York at
        // 16:00 the same afternoon.
        val flight = event(
            id = 1,
            at = "2026-10-03T13:00",
            zone = moscow,
            minutes = 10 * 60,
            startsIn = moscow,
            endsIn = newYork,
        )
        val placed = place(listOf(flight), Zoning.of(listOf(flight), moscow)).single()

        assertEquals(13, placed.start.hour)
        assertEquals(16, placed.end.hour)
        assertEquals(LocalDate.of(2026, 10, 3), placed.end.toLocalDate())
        assertTrue(placed.endsAt.isAfter(placed.startsAt))
        assertEquals(Duration.ofHours(10), placed.duration)
    }

    @Test
    fun `a day going west is longer than twenty-four hours`() {
        val flight = event(
            id = 1,
            at = "2026-10-03T12:00",
            zone = moscow,
            minutes = 60,
            startsIn = moscow,
            endsIn = newYork,
        )
        val days = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), zoning(flight))

        // Midnight in Moscow, then midnight in New York, which is seven hours further on.
        assertEquals(Duration.ofHours(31), days.first().let { Duration.between(it.from, it.until) })
        assertEquals(Duration.ofHours(24), days.last().let { Duration.between(it.from, it.until) })
    }

    @Test
    fun `a day going east is shorter, and the flight stays inside one heading`() {
        val flight = event(
            id = 1,
            at = "2026-10-03T10:00",
            zone = moscow,
            minutes = 8 * 60,
            startsIn = moscow,
            endsIn = bangkok,
        )
        val days = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3), zoning(flight))
        val day = days.single()

        assertEquals(Duration.ofHours(20), Duration.between(day.from, day.until))
        val placed = place(listOf(flight), zoning(flight))
        assertEquals(listOf(EventPart.WHOLE), bands(day, placed).filterIsInstance<EventBand>().map { it.part })
    }

    @Test
    fun `the day the clocks go forward is an hour short`() {
        // The last Sunday of March in Europe, where nobody has flown anywhere.
        val breakfast = event(id = 1, at = "2026-03-29T09:00", zone = berlin, minutes = 60, startsIn = berlin)
        val days = days(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29), zoning(breakfast, fallback = berlin))

        assertEquals(Duration.ofHours(23), days.single().let { Duration.between(it.from, it.until) })
    }

    @Test
    fun `a zone is inherited until something changes it`() {
        val flight = event(id = 1, at = "2026-10-03T10:00", zone = moscow, minutes = 5 * 60, startsIn = moscow, endsIn = bangkok)
        val dinner = event(id = 2, at = "2026-10-03T16:00", zone = moscow, minutes = 90)
        val events = listOf(flight, dinner)
        val placed = place(events, Zoning.of(events, moscow))

        // Dinner says nothing about where it is, and is read in Bangkok because the
        // flight before it landed there: 16:00 Moscow is 20:00 local.
        assertEquals(bangkok, placed.last().startZone)
        assertEquals(20, placed.last().start.hour)
    }

    @Test
    fun `a midnight passed in mid-air ends the day where the clocks still were`() {
        // Leaves Moscow at 22:00 and lands in Bangkok five hours later, which is three in
        // the morning at home and seven where it comes down.
        val flight = event(id = 1, at = "2026-10-03T22:00", zone = moscow, minutes = 5 * 60, startsIn = moscow, endsIn = bangkok)
        val days = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), zoning(flight))
        val placed = place(listOf(flight), zoning(flight))

        // The date turned over in the air, so the flight is drawn under both headings.
        assertEquals(EventPart.STARTS, bands(days[0], placed).filterIsInstance<EventBand>().single().part)
        assertEquals(EventPart.ENDS, bands(days[1], placed).filterIsInstance<EventBand>().single().part)
    }

    @Test
    fun `a landing that puts the date back keeps the day it landed on`() {
        // Moscow to Bangkok, a week there, then the night flight home as far as Muscat:
        // off the ground at five past eight in Bangkok on the eighth, down at quarter past
        // eleven in Muscat, still on the eighth. In between, midnight passes on the clock
        // nobody has reset yet.
        val out = event(id = 1, at = "2026-10-30T19:15", zone = moscow, minutes = 545, endsIn = bangkok)
        val back = event(id = 2, at = "2026-11-08T20:05", zone = bangkok, minutes = 370, endsIn = muscat)
        val events = listOf(out, back)
        val zoning = zoning(out, back)
        val all = days(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 9), zoning)
        val placed = place(events, zoning)

        val eighth = all.single { it.date == LocalDate.of(2026, 11, 8) }
        assertEquals(
            listOf(EventPart.WHOLE),
            bands(eighth, placed).filterIsInstance<EventBand>().map { it.part },
        )

        val ninth = all.single { it.date == LocalDate.of(2026, 11, 9) }
        assertTrue(bands(ninth, placed).filterIsInstance<EventBand>().isEmpty())
        assertEquals(Duration.ofHours(24), Duration.between(ninth.from, ninth.until))
        // And the trip's last minute is the ninth's last minute where anybody is standing,
        // rather than three hours of it on a clock they left behind.
        assertEquals(
            LocalDateTime.parse("2026-11-10T00:00"),
            ninth.until.atZone(muscat).toLocalDateTime(),
        )
    }

    @Test
    fun `a day is read as an unbroken column`() {
        val breakfast = event(id = 1, at = "2026-10-03T09:00", zone = moscow, minutes = 60, startsIn = moscow)
        val museum = event(id = 2, at = "2026-10-03T12:00", zone = moscow, minutes = 120)
        val events = listOf(breakfast, museum)
        val day = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3), Zoning.of(events, moscow)).single()

        val bands = bands(day, place(events, Zoning.of(events, moscow)))
        assertEquals(
            listOf(GapEdge.DAY_OPENS, null, GapEdge.BETWEEN, null, GapEdge.DAY_CLOSES),
            bands.map { (it as? GapBand)?.edge },
        )
        assertEquals(Duration.ofHours(2), (bands[2] as GapBand).length)
        // Nothing is left out between one band and the next.
        bands.zipWithNext().forEach { (a, b) -> assertEquals(a.until, b.from) }
        assertEquals(day.from, bands.first().from)
        assertEquals(day.until, bands.last().until)
    }

    @Test
    fun `two events that touch are joined by a chain`() {
        val breakfast = event(id = 1, at = "2026-10-03T09:00", zone = moscow, minutes = 60, startsIn = moscow)
        val taxi = event(id = 2, at = "2026-10-03T10:00", zone = moscow, minutes = 30, follows = 1)
        val events = listOf(breakfast, taxi)
        val zoning = Zoning.of(events, moscow)
        val day = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3), zoning).single()

        val chain = bands(day, place(events, zoning)).filterIsInstance<ChainBand>().single()
        assertEquals(1L to 2L, chain.before.id to chain.after.id)
        assertTrue(chain.linked)

        // Untied, the chain is still drawn — they touch whether or not one follows the
        // other, and the tap is where that is said.
        val loose = listOf(breakfast, taxi.copy(followsId = null))
        val again = bands(day, place(loose, Zoning.of(loose, moscow))).filterIsInstance<ChainBand>()
        assertFalse(again.single().linked)
    }

    @Test
    fun `a join on midnight is drawn under both days`() {
        val night = event(id = 1, at = "2026-10-03T22:00", zone = moscow, minutes = 120, startsIn = moscow)
        val morning = event(id = 2, at = "2026-10-04T00:00", zone = moscow, minutes = 60)
        val events = listOf(night, morning)
        val zoning = Zoning.of(events, moscow)
        val both = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), zoning)

        // Neither day can show a join that happened in the other, so each shows its own
        // copy of the same one.
        assertEquals(1, bands(both[0], place(events, zoning)).filterIsInstance<ChainBand>().size)
        assertEquals(1, bands(both[1], place(events, zoning)).filterIsInstance<ChainBand>().size)
    }

    @Test
    fun `an empty day is one band`() {
        val day = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3), Zoning.of(emptyList(), moscow)).single()
        assertEquals(GapEdge.WHOLE_DAY, (bands(day, emptyList()).single() as GapBand).edge)
    }

    @Test
    fun `two things booked over each other are drawn between the two of them`() {
        val lunch = event(id = 1, at = "2026-10-03T13:00", zone = moscow, minutes = 90, startsIn = moscow)
        val train = event(id = 2, at = "2026-10-03T14:00", zone = moscow, minutes = 60)
        val events = listOf(lunch, train)
        val zoning = Zoning.of(events, moscow)
        val day = days(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3), zoning).single()

        val overlap = bands(day, place(events, zoning)).filterIsInstance<OverlapBand>().single()
        assertEquals(1L, overlap.before.id)
        assertEquals(2L, overlap.after.id)
        assertEquals(Duration.ofMinutes(30), overlap.length)
        assertEquals(30L, clashes(events).single().minutes)
    }

    @Test
    fun `a clash with something not drawn immediately above is still found`() {
        val afternoon = event(id = 1, at = "2026-10-03T12:00", zone = moscow, minutes = 6 * 60, startsIn = moscow)
        val call = event(id = 2, at = "2026-10-03T13:00", zone = moscow, minutes = 30)
        val taxi = event(id = 3, at = "2026-10-03T17:00", zone = moscow, minutes = 30)

        assertEquals(listOf(1L to 2L, 1L to 3L), clashes(listOf(afternoon, call, taxi)).map { it.before.id to it.after.id })
    }

    @Test
    fun `a landing announces the move once, and what inherits it announces nothing`() {
        // Out to Dubai, back from it, and the queue at the border the minute we land.
        val out = event(id = 1, at = "2026-10-02T12:00", zone = moscow, minutes = 300, endsIn = dubai)
        val back = event(id = 2, at = "2026-10-03T10:00", zone = dubai, minutes = 420, endsIn = moscow)
        val border = event(id = 3, at = "2026-10-03T16:00", zone = moscow, minutes = 60, follows = 2)
        val events = listOf(out, back, border)

        val placed = place(events, Zoning.of(events, moscow)).associateBy { it.id }

        // The return flight takes off on the clocks the first one landed on. That move
        // has been announced already, at the end of the first flight.
        assertEquals(dubai, placed.getValue(2L).startZone)
        assertNull(placed.getValue(2L).startShift)
        assertEquals(-3600L, placed.getValue(2L).endShift)

        // And the border queue is read in Moscow because that is where we are, which is
        // not the same as the border queue moving anybody's clocks.
        assertEquals(moscow, placed.getValue(3L).startZone)
        assertNull(placed.getValue(3L).startShift)
    }

    @Test
    fun `an event that moves the clocks itself says so`() {
        val morning = event(id = 1, at = "2026-10-03T09:00", zone = moscow, minutes = 60)
        // Nothing flew anybody anywhere: somebody simply says the clocks read Dubai now.
        val onwards = event(id = 2, at = "2026-10-03T11:00", zone = dubai, minutes = 60, startsIn = dubai)
        val events = listOf(morning, onwards)

        val placed = place(events, Zoning.of(events, moscow)).associateBy { it.id }

        assertEquals(3600L, placed.getValue(2L).startShift)
        assertNull(placed.getValue(1L).startShift)
    }

    @Test
    fun `an inherited zone stays quiet however long after the flight it falls`() {
        val flight = event(id = 1, at = "2026-10-11T07:50", zone = dubai, minutes = 7 * 60, endsIn = moscow)
        val supper = event(id = 2, at = "2026-10-11T20:00", zone = moscow, minutes = 60)
        val events = listOf(flight, supper)

        val placed = place(events, Zoning.of(events, moscow)).associateBy { it.id }

        assertEquals(moscow, placed.getValue(2L).startZone)
        assertNull(placed.getValue(2L).startShift)
    }

    private val berlin = ZoneId.of("Europe/Berlin")
    private val dubai = ZoneId.of("Asia/Dubai")
    private val muscat = ZoneId.of("Asia/Muscat")

    private fun zoning(vararg events: ScheduleEvent, fallback: ZoneId = moscow) =
        Zoning.of(events.toList(), fallback)

    private fun event(
        id: Long,
        at: String,
        zone: ZoneId,
        minutes: Int,
        startsIn: ZoneId? = null,
        endsIn: ZoneId? = null,
        follows: Long? = null,
    ) = ScheduleEvent(
        id = id,
        tripId = 1,
        title = "Event $id",
        startsAt = LocalDateTime.parse(at).atZone(zone).toEpochSecond(),
        durationMinutes = minutes,
        startsInZone = startsIn?.id,
        endsInZone = endsIn?.id,
        followsId = follows,
    )
}
