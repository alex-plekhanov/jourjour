package app.jourjour.ui.schedule

import app.jourjour.core.calendar.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * An event copied off the device's calendar into the form.
 *
 * The whole of the care here is about zones. A calendar names one on every event it
 * holds, and an event of ours names one only where the clocks moved under it, so copying
 * the first straight into the second would have every dinner announcing a move.
 */
class CalendarCopyTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val dubai = ZoneId.of("Asia/Dubai")
    private val date = LocalDate.of(2026, 10, 3)

    private val dayBegins = LocalTime.of(9, 0)
    private val defaultMinutes = 90

    @Test
    fun `an hour on the same clocks declares nothing`() {
        val dinner = calendar(
            title = "Dinner",
            at = "2026-10-03T19:00",
            zone = moscow,
            minutes = 90,
        )

        val filled = sheet().copiedFrom(dinner, dayBegins, defaultMinutes) { moscow }

        assertEquals("Dinner", filled.title)
        assertEquals(LocalTime.of(19, 0), filled.startTime)
        assertEquals(LocalTime.of(20, 30), filled.endTime)
        assertEquals(moscow, filled.startZone)
        assertFalse(filled.declaresStart)
        assertFalse(filled.declaresEnd)
    }

    @Test
    fun `an hour on another clock declares where it is read`() {
        // Written into the calendar in Dubai while the schedule is still on Moscow.
        val meeting = calendar(title = "Meeting", at = "2026-10-03T12:00", zone = dubai, minutes = 120)

        val filled = sheet().copiedFrom(meeting, dayBegins, defaultMinutes) { moscow }

        assertEquals(dubai, filled.startZone)
        assertTrue(filled.declaresStart)
        // The moment is what was copied, so the hour is the hour it reads in Dubai.
        assertEquals(LocalTime.of(12, 0), filled.startTime)
        assertEquals(LocalTime.of(14, 0), filled.endTime)
        assertFalse(filled.declaresEnd)
    }

    @Test
    fun `a flight brings over where it lands and not where it took off`() {
        val flight = calendar(
            title = "EK133",
            at = "2026-10-11T08:50",
            zone = dubai,
            minutes = 7 * 60,
            endZone = moscow,
        )

        // The schedule is already on Dubai, this being the flight home.
        val filled = sheet().copiedFrom(flight, dayBegins, defaultMinutes) { dubai }

        assertEquals(dubai, filled.startZone)
        assertFalse(filled.declaresStart)
        assertEquals(moscow, filled.endZone)
        assertTrue(filled.declaresEnd)
        assertEquals(LocalTime.of(8, 50), filled.startTime)
        assertEquals(LocalTime.of(14, 50), filled.endTime)
    }

    @Test
    fun `a day marked off is offered at the hour a day starts`() {
        val holiday = CalendarEvent(
            title = "Holiday",
            notes = "",
            place = "",
            // A day marked off is kept on UTC midnight, whatever clock anybody is on.
            startsAt = Instant.parse("2026-10-03T00:00:00Z"),
            endsAt = Instant.parse("2026-10-04T00:00:00Z"),
            isAllDay = true,
            startZone = ZoneId.of("UTC"),
            endZone = ZoneId.of("UTC"),
            calendar = "Holidays",
        )

        val filled = sheet().copiedFrom(holiday, dayBegins, defaultMinutes) { moscow }

        assertEquals(date, filled.startDate)
        assertEquals(dayBegins, filled.startTime)
        assertEquals(LocalTime.of(10, 30), filled.endTime)
        assertEquals(moscow, filled.startZone)
        assertFalse(filled.declaresStart)
        assertFalse(filled.declaresEnd)
    }

    @Test
    fun `where it is comes over with what it says, and any link goes`() {
        val flight = calendar(
            title = "EK132",
            at = "2026-10-03T10:00",
            zone = moscow,
            minutes = 60,
            place = "DME",
            notes = "Booking XJ7K2",
        )

        val filled = sheet()
            .copy(followsId = 7, followsTitle = "Taxi")
            .copiedFrom(flight, dayBegins, defaultMinutes) { moscow }

        assertEquals("DME\nBooking XJ7K2", filled.notes)
        assertTrue(filled.isNotesVisible)
        assertNull(filled.followsId)
        assertEquals("", filled.followsTitle)
    }

    /** The form as a tap on an empty morning leaves it: nothing typed, an hour long. */
    private fun sheet() = ScheduleSheet.over(
        startsAt = LocalDateTime.of(date, LocalTime.of(9, 0)).atZone(moscow).toInstant(),
        durationMinutes = 60,
        zone = moscow,
    )

    @Suppress("LongParameterList")
    private fun calendar(
        title: String,
        at: String,
        zone: ZoneId,
        minutes: Int,
        endZone: ZoneId = zone,
        place: String = "",
        notes: String = "",
    ): CalendarEvent {
        val startsAt = LocalDateTime.parse(at).atZone(zone).toInstant()
        return CalendarEvent(
            title = title,
            notes = notes,
            place = place,
            startsAt = startsAt,
            endsAt = startsAt.plusSeconds(minutes * 60L),
            isAllDay = false,
            startZone = zone,
            endZone = endZone,
            calendar = "Personal",
        )
    }
}
