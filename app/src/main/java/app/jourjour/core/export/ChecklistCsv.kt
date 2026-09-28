package app.jourjour.core.export

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.data.entity.ChecklistItem

/**
 * The four things a checklist item is. Written in this order, and read back by title in
 * whatever order the file happens to have them.
 */
enum class ChecklistColumn(@get:StringRes val titleRes: Int) {
    SECTION(R.string.field_section),
    TITLE(R.string.field_name),
    DONE(R.string.field_checklist_done),
    SKIPPED(R.string.field_checklist_skipped),
}

/**
 * A trip's checklist as a CSV table.
 *
 * All four columns, always: a CSV is what an import reads back, and a list stripped of its
 * flags would come back as a list nobody had thought about.
 *
 * The flags are written as words rather than as TRUE and FALSE, because the file is meant
 * to be opened in a spreadsheet and edited by hand. [readFlag] takes back rather more than
 * it writes, for the same reason.
 */
object ChecklistCsv {

    const val MIME_TYPE = TripCsv.MIME_TYPE

    /**
     * @param headers the column titles, one per [ChecklistColumn], in that order.
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
        items.forEach { item ->
            append(
                CsvWriter.row(
                    listOf(
                        item.section,
                        item.title,
                        if (item.isDone) yes else no,
                        if (item.isSkipped) yes else no,
                    ),
                    separator,
                )
            )
        }
    }
}
