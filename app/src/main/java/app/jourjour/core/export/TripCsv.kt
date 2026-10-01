package app.jourjour.core.export

import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.TripWalletDetails
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

/**
 * A trip's entries as a CSV table.
 *
 * RFC 4180 in shape: CRLF between records, a field quoted only when it carries the
 * separator, a quotation mark or a line break, and a quotation mark inside one doubled.
 * Dates are ISO, which every spreadsheet recognises whatever its locale.
 *
 * What the separator is comes from outside, from a string resource, because a spreadsheet
 * splits a line on whatever its own locale calls the list separator: a comma in English, a
 * semicolon where the comma is already busy being a decimal point. See [decimalOf].
 *
 * The columns and their order come from [TripTable], shared with the XLSX export.
 */
object TripCsv {

    /** See [CsvWriter.BOM]; named here too, since this is where readers look for it. */
    const val BOM = CsvWriter.BOM

    const val MIME_TYPE = "text/csv"

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Hours and minutes, which is all an entry keeps and all a person types. */
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * The two go together and cannot be chosen apart: a locale that separates columns with
     * a semicolon does so precisely because the comma is its decimal point. Written the
     * other way round the numbers arrive as text, and nothing can be summed.
     */
    fun decimalOf(separator: String): Char = if (separator == ",") '.' else ','

    /**
     * @param headers the column titles, one per [TripColumn], in that order.
     * @param separator what goes between columns, from `R.string.csv_separator`.
     * @param nameOf resolves the stored name and resource key of a dictionary entry.
     */
    fun build(
        headers: List<String>,
        entries: List<EntryDetails>,
        separator: String,
        nameOf: (name: String, nameKey: String?) -> String,
        walletNameOf: (TripWalletDetails) -> String,
    ): String {
        val decimal = decimalOf(separator)
        return buildString {
            append(BOM)
            append(row(headers, separator))
            // Always the whole of TripColumn.csv: a CSV is what an import reads back, and
            // what it reads back has to be the whole entry.
            TripTable.rows(
                entries = entries,
                columns = TripColumn.csv,
                nameOf = nameOf,
                walletNameOf = walletNameOf,
            ).forEach { cells ->
                append(row(cells.map { render(it, decimal) }, separator))
            }
        }
    }

    private fun render(cell: Cell, decimal: Char): String = when (cell) {
        Cell.Blank -> ""
        is Cell.Text -> cell.value
        is Cell.Date -> cell.value.format(dateFormat)
        is Cell.Time -> cell.value.format(timeFormat)
        // Written as a padded clock, which is how the app says a length everywhere else.
        is Cell.Length -> "%02d:%02d".format(cell.minutes / 60, cell.minutes % 60)
        is Cell.Number -> {
            val scaled = cell.decimals
                ?.let { cell.value.setScale(it, RoundingMode.HALF_UP) }
                ?: cell.value
            // There is at most one dot to swap: nothing here is written with grouping.
            scaled.toPlainString().replace('.', decimal)
        }
    }

    private fun row(values: List<String>, separator: String): String =
        CsvWriter.row(values, separator)
}
