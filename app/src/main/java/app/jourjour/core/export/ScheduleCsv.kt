package app.jourjour.core.export

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.schedule.PlacedEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

/**
 * What an event is made of. Written in this order, and read back by title in whatever
 * order the file happens to have them.
 */
enum class ScheduleColumn(@get:StringRes val titleRes: Int) {
    DATE(R.string.csv_date),
    TIME(R.string.csv_time),
    LENGTH(R.string.csv_length),
    TITLE(R.string.field_name),
    NOTES(R.string.field_notes),
    FOLLOWS(R.string.csv_follows),
    ZONE_START(R.string.field_zone_start),
    ZONE_END(R.string.field_zone_end),
    CATEGORY(R.string.field_category),
    AMOUNT(R.string.field_amount),
    CURRENCY(R.string.field_currency),
}

/**
 * A trip's schedule as a CSV table.
 *
 * The times are **wall clocks**, which is what a person reads and what a spreadsheet can
 * hold: the moment behind one is worked out on the way back in, from the zones the file
 * declares and the home zone of whatever install is reading it. That is the same rule the
 * app itself lays a schedule out by, so a file written here and read back anywhere says
 * the same hours.
 *
 * Only a zone an event actually declares is written. An inherited one is not a fact about
 * that event — it belongs to whatever moved the clocks — and writing it on every line
 * would make a file that, read back, nailed every event to a place.
 */
object ScheduleCsv {

    const val MIME_TYPE = TripCsv.MIME_TYPE

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * @param headers the column titles, one per [ScheduleColumn], in that order.
     * @param events the trip's schedule in time order, each read on the clock it happens on.
     * @param follows a mark for an event that begins where the one before it ends.
     * @param nameOf resolves a category's stored name and resource key, so that the file
     *   carries the name a person reads on the screen.
     */
    fun build(
        headers: List<String>,
        events: List<PlacedEvent>,
        separator: String,
        follows: String,
        nameOf: (name: String, nameKey: String?) -> String,
        categoryOf: (Long) -> Pair<String, String?>?,
        decimalsOf: (String) -> Int,
    ): String = buildString {
        val decimal = TripCsv.decimalOf(separator)
        append(CsvWriter.BOM)
        append(CsvWriter.row(headers, separator))
        events.forEach { placed ->
            val event = placed.event
            val category = event.categoryId?.let(categoryOf)?.let { (name, key) -> nameOf(name, key) }
            append(
                CsvWriter.row(
                    listOf(
                        placed.start.format(dateFormat),
                        placed.start.format(timeFormat),
                        length(event.durationMinutes),
                        event.title,
                        event.notes,
                        if (event.followsId != null) follows else "",
                        event.startsInZone.orEmpty(),
                        event.endsInZone.orEmpty(),
                        category.orEmpty(),
                        event.amountMilli
                            ?.let { amount ->
                                number(
                                    Money(amount).toBigDecimal(),
                                    decimalsOf(event.currencyCode.orEmpty()),
                                    decimal,
                                )
                            }
                            .orEmpty(),
                        event.currencyCode.orEmpty(),
                    ),
                    separator,
                )
            )
        }
    }

    /** How long it lasts, written as a clock is: 01:30, 168:00 for a week. */
    fun length(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun number(value: BigDecimal, decimals: Int, decimal: Char): String =
        value.setScale(decimals, RoundingMode.HALF_UP).toPlainString().replace('.', decimal)
}
