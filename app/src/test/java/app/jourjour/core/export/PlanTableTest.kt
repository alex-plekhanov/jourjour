package app.jourjour.core.export

import app.jourjour.core.schedule.ScheduleDay
import app.jourjour.core.schedule.Zoning
import app.jourjour.core.schedule.bands
import app.jourjour.core.schedule.days
import app.jourjour.core.schedule.place
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The schedule as a sheet of a spreadsheet.
 *
 * What is tested here is mostly what the CSV deliberately does not do: free time and
 * minutes booked twice are rows in their own right, with both ends and a length and
 * nothing else to say.
 */
class PlanTableTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 10, 3)
    private val rouble = Currency("RUB", "Rouble", "R", decimals = 2)

    /** Nine to ten, half past nine to half past ten, then noon: a clash and a gap. */
    private val breakfast = event(
        id = 1,
        at = "2026-10-03T09:00",
        minutes = 60,
        title = "Breakfast",
        notes = "On the terrace",
        amountMilli = 2_000_000,
    )
    private val call = event(id = 2, at = "2026-10-03T09:30", minutes = 60, title = "Call")
    private val museum = event(id = 3, at = "2026-10-03T12:00", minutes = 90, title = "Museum")

    @Test
    fun `free time and a clash are rows of their own, the night is not`() {
        val rows = rows(breakfast, call, museum)

        assertEquals(
            listOf("Breakfast", "Overlap", "Call", "", "Museum"),
            rows.map { it.title },
        )
        assertEquals(listOf(60L, 30L, 60L, 90L, 90L), rows.map { it.length })
    }

    @Test
    fun `a clash carries both its ends and how long it is, and nothing else`() {
        val clash = rows(breakfast, call, museum)[1]

        assertEquals(Cell.Date(date), clash[PlanColumn.START_DATE.ordinal])
        assertEquals(Cell.Time(LocalTime.of(9, 30)), clash[PlanColumn.START_TIME.ordinal])
        assertEquals(Cell.Date(date), clash[PlanColumn.END_DATE.ordinal])
        assertEquals(Cell.Time(LocalTime.of(10, 0)), clash[PlanColumn.END_TIME.ordinal])
        assertEquals(Cell.Length(30), clash[PlanColumn.LENGTH.ordinal])
        assertEquals(Cell.Text("Overlap"), clash[PlanColumn.TITLE.ordinal])
        listOf(
            PlanColumn.START_ZONE, PlanColumn.END_ZONE, PlanColumn.NOTES,
            PlanColumn.CATEGORY, PlanColumn.AMOUNT, PlanColumn.CURRENCY,
        ).forEach { assertEquals(Cell.Blank, clash[it.ordinal]) }
    }

    @Test
    fun `a gap says when it is and says nothing about money`() {
        val gap = rows(breakfast, call, museum)[3]

        assertEquals(Cell.Time(LocalTime.of(10, 30)), gap[PlanColumn.START_TIME.ordinal])
        assertEquals(Cell.Time(LocalTime.of(12, 0)), gap[PlanColumn.END_TIME.ordinal])
        assertEquals(Cell.Length(90), gap[PlanColumn.LENGTH.ordinal])
        assertEquals(Cell.Blank, gap[PlanColumn.TITLE.ordinal])
        assertEquals(Cell.Blank, gap[PlanColumn.AMOUNT.ordinal])
    }

    @Test
    fun `an event carries what it costs, in a currency named by its code`() {
        val row = rows(breakfast, call, museum).first()

        assertEquals(Cell.Text("On the terrace"), row[PlanColumn.NOTES.ordinal])
        assertEquals(Cell.Text("Dining"), row[PlanColumn.CATEGORY.ordinal])
        assertEquals(
            Cell.Number(BigDecimal("2000.000"), decimals = 2),
            row[PlanColumn.AMOUNT.ordinal],
        )
        assertEquals(Cell.Text("RUB"), row[PlanColumn.CURRENCY.ordinal])
    }

    @Test
    fun `a day with nothing in it writes nothing at all`() {
        assertEquals(emptyList<List<Cell>>(), rows())
    }

    private fun rows(vararg events: ScheduleEvent): List<List<Cell>> {
        val zoning = Zoning.of(events.toList(), moscow)
        val placed = place(events.toList(), zoning)
        val days: List<ScheduleDay> = days(date, date, zoning)
        return PlanTable.rows(
            days = days,
            bandsOf = { day -> bands(day, placed) },
            columns = PlanColumn.entries,
            categoryOf = { if (it.event.amountMilli == null) "" else "Dining" },
            currencyOf = { if (it.event.currencyCode == null) null else rouble },
            overlapText = "Overlap",
        )
    }

    private val List<Cell>.title: String
        get() = (this[PlanColumn.TITLE.ordinal] as? Cell.Text)?.value.orEmpty()

    private val List<Cell>.length: Long
        get() = (this[PlanColumn.LENGTH.ordinal] as Cell.Length).minutes

    private fun event(
        id: Long,
        at: String,
        minutes: Int,
        title: String,
        notes: String = "",
        amountMilli: Long? = null,
    ) = ScheduleEvent(
        id = id,
        tripId = 1,
        title = title,
        notes = notes,
        startsAt = LocalDateTime.parse(at).atZone(moscow).toEpochSecond(),
        durationMinutes = minutes,
        amountMilli = amountMilli,
        currencyCode = amountMilli?.let { rouble.code },
    )
}
