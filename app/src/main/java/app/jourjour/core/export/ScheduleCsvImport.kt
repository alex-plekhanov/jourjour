package app.jourjour.core.export

import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.ScheduleEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Reads a schedule back from a table in the shape [ScheduleCsv] writes — or part of it.
 *
 * The columns are found by their titles in the first line, in whatever order they come, so
 * a file cut down or rearranged in a spreadsheet still reads. A row needs a date, a time,
 * a length and a name; everything else it may leave out.
 *
 * **The times are wall clocks and the moments are worked out here**, by walking the rows
 * in order and carrying the zone forward exactly as the schedule does: a row that declares
 * where it begins or where it lands moves the clock for everything after it, and the home
 * zone answers until one does. So a file says the same hours wherever it is read, which is
 * the whole of what anybody wrote down.
 *
 * Nothing is written until the whole file has been understood. A row that cannot be made
 * sense of stops the import and is reported with its line number: half an import is worse
 * than none, because there is no telling by eye which half arrived.
 */
object ScheduleCsvImport {

    /** The separators an export can have used, tried against the header in this order. */
    private val SEPARATORS = listOf(',', ';')

    /** What a file has to carry, whatever else it leaves out. */
    val REQUIRED: List<ScheduleColumn> = listOf(
        ScheduleColumn.DATE,
        ScheduleColumn.TIME,
        ScheduleColumn.LENGTH,
        ScheduleColumn.TITLE,
    )

    /**
     * What counts as "this begins where the one before it ends". The same marks the
     * checklist takes for a tick: whatever a person reaches for with a pen.
     */
    private val AFFIRMATIVE_MARKS = setOf("true", "1", "+", "x", "v", "✓", "✔")

    private val dates = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    )

    private val times = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm:ss"),
    )

    sealed interface Result {

        /** The events the file describes, in the order it describes them. */
        data class Ready(val events: List<ScheduleEvent>) : Result

        /** No arrangement of the separators produced a first line naming any column. */
        data object HeaderMismatch : Result

        /** The first line names some columns, but not all of [REQUIRED]; these are missing. */
        data class MissingColumns(val columns: List<ScheduleColumn>) : Result

        /** A value the dictionaries know nothing about. [line] counts from 1, header included. */
        data class Unknown(val line: Int, val column: ScheduleColumn, val value: String) : Result

        /** A value of the wrong shape: a date that is not one, a length that is not one. */
        data class Malformed(val line: Int, val column: ScheduleColumn, val value: String) : Result

        /** A column that has to say something and says nothing. */
        data class Empty(val line: Int, val column: ScheduleColumn) : Result

        /** A row that says what it costs but not what the money is for, or the other way round. */
        data class HalfACost(val line: Int) : Result
    }

    /**
     * @param headers the titles this app would write, in [ScheduleColumn] order.
     * @param home where the clocks stand before any row has moved them.
     * @param categories what a planned expense may be filed under, already narrowed to the
     *   ones the form offers.
     */
    fun read(
        text: String,
        headers: List<String>,
        tripId: Long,
        home: ZoneId,
        categories: List<Category>,
        currencies: List<Currency>,
        nameOf: (name: String, nameKey: String?) -> String,
    ): Result {
        val columnOf = ScheduleColumn.entries.zip(headers)
            .associate { (column, title) -> title to column }

        val (separator, positions) = SEPARATORS
            .map { candidate ->
                candidate to positions(CsvReader.read(text, candidate).firstOrNull(), columnOf)
            }
            .maxBy { (_, found) -> found.size }
        if (positions.isEmpty()) return Result.HeaderMismatch
        val missing = REQUIRED.filter { it !in positions }
        if (missing.isNotEmpty()) return Result.MissingColumns(missing)

        val decimal = TripCsv.decimalOf(separator.toString())
        val known = categories.associateBy { nameOf(it.name, it.nameKey) }
        val money = currencies.associateBy { it.code }

        var zone = home
        val events = mutableListOf<ScheduleEvent>()
        CsvReader.read(text, separator).drop(1).forEachIndexed { index, fields ->
            // The header is line 1, so the first record is line 2 — what a spreadsheet shows.
            val line = index + 2

            fun field(column: ScheduleColumn): String =
                positions[column]?.let { fields.getOrNull(it) }.orEmpty().trim()

            val title = field(ScheduleColumn.TITLE)
            if (title.isEmpty()) return Result.Empty(line, ScheduleColumn.TITLE)

            val rawDate = field(ScheduleColumn.DATE)
            if (rawDate.isEmpty()) return Result.Empty(line, ScheduleColumn.DATE)
            val date = dates.firstNotNullOfOrNull { format ->
                runCatching { LocalDate.parse(rawDate, format) }.getOrNull()
            } ?: return Result.Malformed(line, ScheduleColumn.DATE, rawDate)

            val rawTime = field(ScheduleColumn.TIME)
            if (rawTime.isEmpty()) return Result.Empty(line, ScheduleColumn.TIME)
            val time = times.firstNotNullOfOrNull { format ->
                runCatching { LocalTime.parse(rawTime, format) }.getOrNull()
            } ?: return Result.Malformed(line, ScheduleColumn.TIME, rawTime)

            val rawLength = field(ScheduleColumn.LENGTH)
            if (rawLength.isEmpty()) return Result.Empty(line, ScheduleColumn.LENGTH)
            val minutes = minutes(rawLength)
                ?: return Result.Malformed(line, ScheduleColumn.LENGTH, rawLength)

            val startsIn = field(ScheduleColumn.ZONE_START).zone()
                ?: return Result.Unknown(
                    line,
                    ScheduleColumn.ZONE_START,
                    field(ScheduleColumn.ZONE_START),
                )
            val endsIn = field(ScheduleColumn.ZONE_END).zone()
                ?: return Result.Unknown(
                    line,
                    ScheduleColumn.ZONE_END,
                    field(ScheduleColumn.ZONE_END),
                )

            // The clock this row is read on, and the one everything after it is read on.
            val startZone = startsIn.value ?: zone
            zone = endsIn.value ?: startZone

            val rawCategory = field(ScheduleColumn.CATEGORY)
            val category = rawCategory.takeIf { it.isNotEmpty() }?.let {
                known[it] ?: return Result.Unknown(line, ScheduleColumn.CATEGORY, it)
            }
            val rawCurrency = field(ScheduleColumn.CURRENCY)
            val currency = rawCurrency.takeIf { it.isNotEmpty() }?.let {
                money[it] ?: return Result.Unknown(line, ScheduleColumn.CURRENCY, it)
            }
            val rawAmount = field(ScheduleColumn.AMOUNT)
            val amount = rawAmount.takeIf { it.isNotEmpty() }?.let {
                runCatching { BigDecimal(it.replace(decimal, '.')) }.getOrNull()
                    ?: return Result.Malformed(line, ScheduleColumn.AMOUNT, it)
            }
            // All three or none, the same rule the form keeps: a sum with nothing to file
            // it under says nothing the budget can read.
            val costAsked = category != null || amount != null || currency != null
            if (costAsked && (category == null || amount == null || currency == null)) {
                return Result.HalfACost(line)
            }

            events += ScheduleEvent(
                tripId = tripId,
                title = title,
                notes = field(ScheduleColumn.NOTES),
                startsAt = ZonedDateTime.of(date, time, startZone).toEpochSecond(),
                durationMinutes = minutes,
                startsInZone = startsIn.value?.id,
                endsInZone = endsIn.value?.id,
                // The link is which row it is under, so it is tied up once the rows have
                // the ids they will keep. See `ScheduleRepository`.
                followsId = null,
                categoryId = category?.id,
                amountMilli = amount?.let { milli(it, requireNotNull(currency).decimals) },
                currencyCode = currency?.code,
            )
        }
        return Result.Ready(events)
    }

    /** Which rows begin where the row above them ends, by position in the file. */
    fun followers(text: String, headers: List<String>, yes: String): Set<Int> {
        val columnOf = ScheduleColumn.entries.zip(headers)
            .associate { (column, title) -> title to column }
        val (separator, positions) = SEPARATORS
            .map { candidate ->
                candidate to positions(CsvReader.read(text, candidate).firstOrNull(), columnOf)
            }
            .maxBy { (_, found) -> found.size }
        val at = positions[ScheduleColumn.FOLLOWS] ?: return emptySet()
        return CsvReader.read(text, separator).drop(1)
            .mapIndexedNotNull { index, fields ->
                val mark = fields.getOrNull(at)?.trim()?.lowercase().orEmpty()
                index.takeIf { mark.isNotEmpty() && (mark == yes.lowercase() || mark in AFFIRMATIVE_MARKS) }
            }
            .toSet()
    }

    /** "01:30", or a plain count of minutes for a file written by hand. */
    private fun minutes(value: String): Int? {
        val parts = value.split(':')
        val minutes = when (parts.size) {
            1 -> parts[0].toIntOrNull()
            2 -> parts[0].toIntOrNull()?.let { hours ->
                parts[1].toIntOrNull()?.let { rest -> hours * 60 + rest }
            }
            else -> null
        }
        return minutes?.takeIf { it > 0 }
    }

    /** A zone as named, nothing for a blank field, or a refusal for a name nobody knows. */
    private fun String.zone(): Zoned? {
        if (isEmpty()) return Zoned(null)
        return runCatching { Zoned(ZoneId.of(this)) }.getOrNull()
    }

    private class Zoned(val value: ZoneId?)

    private fun milli(value: BigDecimal, decimals: Int): Long =
        value.setScale(decimals, RoundingMode.HALF_UP)
            .setScale(SCALE, RoundingMode.HALF_UP)
            .movePointRight(SCALE)
            .longValueExact()

    /** Where each column the first line names is found; a title named twice keeps its first. */
    private fun positions(
        header: List<String>?,
        columnOf: Map<String, ScheduleColumn>,
    ): Map<ScheduleColumn, Int> {
        val found = mutableMapOf<ScheduleColumn, Int>()
        header?.forEachIndexed { index, title ->
            val column = columnOf[title.trim()] ?: return@forEachIndexed
            found.putIfAbsent(column, index)
        }
        return found
    }

    /** Amounts are held as whole thousandths. */
    private const val SCALE = 3
}
