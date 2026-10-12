package app.jourjour.core.export

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.checklist.inReadingOrder
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.ChecklistItem

/**
 * The five things a checklist item is. Written in this order, and read back by title in
 * whatever order the file happens to have them.
 */
enum class ChecklistColumn(@get:StringRes val titleRes: Int) {
    SECTION(R.string.field_section),
    TITLE(R.string.field_name),
    QUANTITY(R.string.field_quantity),
    DONE(R.string.field_checklist_done),
    SKIPPED(R.string.field_checklist_skipped),
}

/**
 * A trip's checklist as a CSV table.
 *
 * Every column, always: a CSV is what an import reads back, and a list stripped of its
 * flags would come back as a list nobody had thought about. There is no column for the
 * order, because the lines are in it — a table is read down the page, and a number saying
 * so beside every line would be a column nobody could do anything with.
 *
 * The flags are written as words rather than as TRUE and FALSE, because the file is meant
 * to be opened in a spreadsheet and edited by hand. [readFlag] takes back rather more than
 * it writes, for the same reason.
 */
object ChecklistCsv {

    const val MIME_TYPE = TripCsv.MIME_TYPE

    /**
     * @param headers the column titles, one per [ChecklistColumn], in that order.
     * @param items the trip's checklist, written down the page in the order the screen
     *   reads it — see [inReadingOrder]. The file is the list, so it has to be the list in
     *   the order somebody put it in.
     * @param separator what goes between columns, from `R.string.csv_separator`.
     * @param yes, @param no what a flag is written as.
     */
    fun build(
        headers: List<String>,
        items: List<ChecklistItem>,
        separator: String,
        yes: String,
        no: String,
    ): String = buildString {
        append(CsvWriter.BOM)
        append(CsvWriter.row(headers, separator))
        inReadingOrder(items).forEach { item ->
            append(
                CsvWriter.row(
                    listOf(
                        item.section,
                        item.title,
                        // Ungrouped, as a budget line's count is and for the same reason:
                        // this figure is written to be read back. Grouped, three shirts a
                        // thousand times over goes out as "3 000" and comes back as three
                        // on a phone whose language has since changed — the space means
                        // nothing there, and the one that writes a comma means a half.
                        item.quantityMilli
                            ?.let { MoneyFormat.formatQuantityForInput(Quantity(it)) }
                            .orEmpty(),
                        if (item.isDone) yes else no,
                        if (item.isSkipped) yes else no,
                    ),
                    separator,
                )
            )
        }
    }
}
