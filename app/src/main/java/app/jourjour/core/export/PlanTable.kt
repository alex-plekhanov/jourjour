package app.jourjour.core.export

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.schedule.Band
import app.jourjour.core.schedule.EventBand
import app.jourjour.core.schedule.GapBand
import app.jourjour.core.schedule.GapEdge
import app.jourjour.core.schedule.OverlapBand
import app.jourjour.core.schedule.PlacedEvent
import app.jourjour.core.schedule.ScheduleDay
import app.jourjour.core.schedule.endShift
import app.jourjour.core.schedule.shiftLabel
import app.jourjour.core.schedule.startShift
import app.jourjour.data.entity.Currency
import java.math.BigDecimal
import java.time.ZoneId

/**
 * The columns a schedule is written out in as a spreadsheet, in the order they stand.
 *
 * Not the same list the CSV carries, and deliberately. A CSV is read back by this app and
 * must say what an event *is* — hence a link to the one before it, and a length instead of
 * an end, the end being worked out from the two. A spreadsheet is read by a person, who
 * wants both ends spelled out and has no use for a link they cannot act on.
 *
 * @param headerRes what stands over the column in the sheet. Short for the six that say
 *   when: a heading read sideways is nobody's spreadsheet, and which end a clock belongs
 *   to is already said by the date beside it.
 * @param pickRes what the column is called where somebody is choosing it. Long, because
 *   there is nothing beside it there to say which end it means.
 */
enum class PlanColumn(@get:StringRes val headerRes: Int, @get:StringRes val pickRes: Int) {
    START_DATE(R.string.field_starts, R.string.xlsx_start_date),
    START_TIME(R.string.field_time_short, R.string.xlsx_start_time),
    START_ZONE(R.string.xlsx_zone, R.string.field_zone_start),
    END_DATE(R.string.field_ends, R.string.xlsx_end_date),
    END_TIME(R.string.field_time_short, R.string.xlsx_end_time),
    END_ZONE(R.string.xlsx_zone, R.string.field_zone_end),
    TITLE(R.string.field_name, R.string.field_name),
    NOTES(R.string.field_notes, R.string.field_notes),
    LENGTH(R.string.csv_length, R.string.csv_length),
    CATEGORY(R.string.xlsx_category, R.string.xlsx_category),
    AMOUNT(R.string.field_amount, R.string.field_amount),
    CURRENCY(R.string.field_currency, R.string.field_currency),
}

/** Turns a schedule into rows of [Cell]s, one per [PlanColumn]. */
object PlanTable {

    /** How many decimals an amount gets when its currency somehow did not come along. */
    private const val DEFAULT_DECIMALS = 2

    /**
     * The whole schedule, day by day, as a table.
     *
     * Free time and minutes claimed twice are rows of their own here, where the CSV leaves
     * them out: the CSV is read back and would turn every gap into an event, while a
     * spreadsheet is looked at, and what is *not* booked is half of what anybody looks at
     * a schedule for. They carry their two ends and how long they are and nothing else —
     * a gap has no name, and nothing states a zone but an event.
     *
     * The dates are neither grouped nor headed. A spreadsheet groups and sorts for itself,
     * and a heading row in the middle of a table is a row that breaks both.
     */
    fun rows(
        days: List<ScheduleDay>,
        bandsOf: (ScheduleDay) -> List<Band>,
        columns: List<PlanColumn>,
        categoryOf: (PlacedEvent) -> String,
        currencyOf: (PlacedEvent) -> Currency?,
        overlapText: String,
    ): List<List<Cell>> = buildList {
        days.forEach { day ->
            bandsOf(day).filter { it.isWorthWriting }.forEach { band ->
                add(
                    columns.map { column ->
                        when (band) {
                            is EventBand -> band.placed.cell(column, categoryOf, currencyOf)
                            is OverlapBand -> band.spanCell(column, day.zone, overlapText)
                            else -> band.spanCell(column, day.zone, name = "")
                        }
                    }
                )
            }
        }
    }

    /**
     * A stretch of time that is nobody's event: where it begins, where it ends, how long
     * it is, read on the clock the day it falls in is read on.
     */
    private fun Band.spanCell(column: PlanColumn, zone: ZoneId, name: String): Cell = when (column) {
        PlanColumn.START_DATE -> Cell.Date(from.atZone(zone).toLocalDate())
        PlanColumn.START_TIME -> Cell.Time(from.atZone(zone).toLocalTime())
        PlanColumn.END_DATE -> Cell.Date(until.atZone(zone).toLocalDate())
        PlanColumn.END_TIME -> Cell.Time(until.atZone(zone).toLocalTime())
        PlanColumn.LENGTH -> Cell.Length(length.toMinutes())
        PlanColumn.TITLE -> text(name)
        else -> Cell.Blank
    }

    private fun PlacedEvent.cell(
        column: PlanColumn,
        categoryOf: (PlacedEvent) -> String,
        currencyOf: (PlacedEvent) -> Currency?,
    ): Cell = when (column) {
        PlanColumn.START_DATE -> Cell.Date(start.toLocalDate())
        PlanColumn.START_TIME -> Cell.Time(start.toLocalTime())
        // The offset and nothing else, and only where the clocks actually moved: a column
        // reading "+3" on every line of a trip that never left home would be noise.
        PlanColumn.START_ZONE -> startShift?.let { Cell.Text(shiftLabel(it)) } ?: Cell.Blank
        PlanColumn.END_DATE -> Cell.Date(end.toLocalDate())
        PlanColumn.END_TIME -> Cell.Time(end.toLocalTime())
        PlanColumn.END_ZONE -> endShift?.let { Cell.Text(shiftLabel(it)) } ?: Cell.Blank
        PlanColumn.TITLE -> text(event.title)
        PlanColumn.NOTES -> text(event.notes)
        PlanColumn.LENGTH -> Cell.Length(duration.toMinutes())
        PlanColumn.CATEGORY -> text(categoryOf(this))
        PlanColumn.AMOUNT -> event.amountMilli?.let { amount ->
            Cell.Number(
                value = BigDecimal(amount).movePointLeft(Money.SCALE),
                decimals = currencyOf(this)?.decimals ?: DEFAULT_DECIMALS,
            )
        } ?: Cell.Blank
        // The code and not the symbol: three letters say which money it is wherever the
        // sheet is opened, and a symbol says it only to whoever knows the symbol.
        PlanColumn.CURRENCY -> text(currencyOf(this)?.code.orEmpty())
    }

    private fun text(value: String): Cell =
        if (value.isBlank()) Cell.Blank else Cell.Text(value)

    /**
     * What belongs in the table. The chains are a control on a screen and write as
     * nothing; the stretches at either end of a day are the night, which is not the free
     * time anybody is asking about.
     */
    private val Band.isWorthWriting: Boolean
        get() = when (this) {
            is GapBand -> edge == GapEdge.BETWEEN
            is EventBand, is OverlapBand -> true
            else -> false
        }
}
