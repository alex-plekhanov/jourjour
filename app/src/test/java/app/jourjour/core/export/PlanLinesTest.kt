package app.jourjour.core.export

import app.jourjour.core.schedule.Zoning
import app.jourjour.core.schedule.bands
import app.jourjour.core.schedule.days
import app.jourjour.core.schedule.place
import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The plan as the lines of a printed page.
 *
 * What is tested is where the page parts from the screen: an event carried over midnight
 * is one line, not one under each heading.
 */
class PlanLinesTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val dubai = ZoneId.of("Asia/Dubai")
    private val hours = DateTimeFormatter.ofPattern("HH:mm")

    /** Out of Moscow at ten past eleven at night, into Dubai at five past six. */
    private val flight = event(
        id = 1,
        at = "2026-10-02T23:10",
        zone = moscow,
        minutes = 355,
        title = "DME - DXB",
        notes = "EK132",
        endsIn = dubai,
    )

    /** And on, four and a half hours after landing. */
    private val onward = event(id = 2, at = "2026-10-03T10:35", zone = dubai, minutes = 395, title = "DXB - MRU")

    @Test
    fun `a flight over midnight is printed once, whole, under the day it took off`() {
        val lines = lines(flight, onward)

        val events = lines.filterIsInstance<PlanLine.Event>()
        assertEquals(listOf("DME - DXB", "DXB - MRU"), events.map { it.title })

        val printed = events.first()
        assertEquals("23:10", printed.start)
        assertEquals("06:05", printed.end)
        // Landing on another date than the heading's, which the page has to say.
        assertEquals("3.10", printed.endDate)
        assertEquals("", printed.startDate)
        // The notes and the money are on the one line there is.
        assertEquals("EK132", printed.notes)
        assertEquals("cost of DME - DXB", printed.cost)
        assertEquals(PlanCorners(top = true, bottom = true), printed.corners)
    }

    @Test
    fun `the morning after starts with what is booked in it`() {
        val lines = lines(flight, onward)

        // The free hours from the landing to the next flight are the morning of the third
        // as far as the page is concerned: nothing above them under that heading to be
        // measured from.
        assertEquals(
            listOf("day 2026-10-02", "DME - DXB", "day 2026-10-03", "DXB - MRU"),
            lines.map {
                when (it) {
                    is PlanLine.Day -> it.date
                    is PlanLine.Event -> it.title
                    is PlanLine.Gap -> "gap ${it.length}"
                    is PlanLine.Overlap -> "overlap"
                }
            },
        )
    }

    private fun lines(vararg events: ScheduleEvent): List<PlanLine> {
        val zoning = Zoning.of(events.toList(), moscow)
        val placed = place(events.toList(), zoning)
        return planLines(
            days = days(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), zoning),
            bandsOf = { day -> bands(day, placed) },
            summaryOf = { "" },
            dateOf = { day -> "day ${day.date}" },
            clock = { at -> at.format(hours) },
            shortDate = { on -> "${on.dayOfMonth}.${on.monthValue}" },
            length = { minutes -> "$minutes" },
            costOf = { event -> "cost of ${event.event.title}" },
            overlapText = "overlap",
        )
    }

    @Suppress("LongParameterList")
    private fun event(
        id: Long,
        at: String,
        zone: ZoneId,
        minutes: Int,
        title: String,
        notes: String = "",
        endsIn: ZoneId? = null,
    ) = ScheduleEvent(
        id = id,
        tripId = 1,
        title = title,
        notes = notes,
        startsAt = LocalDateTime.parse(at).atZone(zone).toEpochSecond(),
        durationMinutes = minutes,
        endsInZone = endsIn?.id,
    )
}
