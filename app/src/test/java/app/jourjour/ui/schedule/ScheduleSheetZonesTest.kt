package app.jourjour.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The clocks on the form of one event.
 *
 * Two rules, and everything here is one of them. A zone says how a moment is read, so
 * naming one moves the figures on the face and never the hours between them. And an hour
 * typed is read on the clock the schedule hands down at *that* hour, not at the hour the
 * form happened to open on.
 */
class ScheduleSheetZonesTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val muscat = ZoneId.of("Asia/Muscat")

    @Test
    fun `naming where it lands moves the clock and leaves the length alone`() {
        // Out of Muscat at twenty to one in the morning, six and three quarter hours.
        val flight = ScheduleSheet
            .over(Instant.parse("2026-11-08T20:40:00Z"), durationMinutes = 405, zone = muscat)

        val landing = flight.withEndZone(moscow)

        assertEquals(LocalTime.of(6, 25), landing.endTime)
        assertEquals(LocalDate.of(2026, 11, 9), landing.endDate)
        assertEquals(Duration.ofMinutes(405), landing.duration)
        assertEquals("6", landing.hours)
        assertEquals("45", landing.minutes)
        assertTrue(landing.declaresEnd)
        // And the moment it lands is the one it was always going to land at.
        assertEquals(flight.endsAt, landing.endsAt)
    }

    @Test
    fun `naming where it begins moves both clocks, the event staying where it is`() {
        val morning = ScheduleSheet
            .over(Instant.parse("2026-10-03T06:00:00Z"), durationMinutes = 60, zone = moscow)
        assertEquals(LocalTime.of(9, 0), morning.startTime)

        val dubai = ZoneId.of("Asia/Dubai")
        val moved = morning.withStartZone(dubai)

        assertEquals(LocalTime.of(10, 0), moved.startTime)
        assertEquals(LocalTime.of(11, 0), moved.endTime)
        assertEquals(Duration.ofHours(1), moved.duration)
        assertEquals(morning.startsAt, moved.startsAt)
        assertTrue(moved.declaresStart)
        // The far end follows the near one while it has said nothing of its own.
        assertEquals(dubai, moved.endZone)
        assertFalse(moved.declaresEnd)
    }

    @Test
    fun `taking a declaration back is the same move in reverse`() {
        val flight = ScheduleSheet
            .over(Instant.parse("2026-11-08T20:40:00Z"), durationMinutes = 405, zone = muscat)
            .withEndZone(moscow)

        val back = flight.withInheritedEnd()

        assertEquals(LocalTime.of(7, 25), back.endTime)
        assertEquals(Duration.ofMinutes(405), back.duration)
        assertFalse(back.declaresEnd)
    }

    @Test
    fun `an hour typed back into the small hours is read on the clock of those hours`() {
        // The clocks go back three on landing in Muscat at a quarter past eleven at night.
        val landed = Instant.parse("2026-11-08T19:15:00Z")
        val schedule = { at: Instant -> if (at.isBefore(landed)) bangkok else muscat }

        // The form opens on the three quarters of an hour left of that evening...
        val sheet = ScheduleSheet.over(landed, durationMinutes = 45, zone = muscat)
        assertEquals(LocalDate.of(2026, 11, 8), sheet.startDate)
        assertEquals(LocalTime.of(23, 15), sheet.startTime)

        // ...and is then set back to a quarter past midnight of the same date, which on
        // that date is an hour nobody was in Muscat for.
        val typed = sheet.withStart(time = LocalTime.of(0, 15)).inheriting(schedule)

        assertEquals(bangkok, typed.startZone)
        assertEquals(LocalTime.of(0, 15), typed.startTime)
        assertEquals(LocalDate.of(2026, 11, 8), typed.startDate)
        // Which is the moment the list will draw it at: the hour typed, not three past it.
        assertEquals(Instant.parse("2026-11-07T17:15:00Z"), typed.startsAt)
        assertEquals(Duration.ofMinutes(45), typed.duration)
    }

    @Test
    fun `a declared zone is not taken away by the hours moving under it`() {
        val landed = Instant.parse("2026-11-08T19:15:00Z")
        val schedule = { at: Instant -> if (at.isBefore(landed)) bangkok else muscat }

        val declared = ScheduleSheet
            .over(landed, durationMinutes = 45, zone = muscat)
            .withStartZone(muscat)
            .withStart(time = LocalTime.of(0, 15))
            .inheriting(schedule)

        assertEquals(muscat, declared.startZone)
        assertEquals(Instant.parse("2026-11-07T20:15:00Z"), declared.startsAt)
    }
}
