package app.jourjour.core.export

import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.TripWalletDetails
import java.io.OutputStream

/**
 * A trip's entries as a spreadsheet.
 *
 * All this does is say what the table is — which columns, what they are called and how
 * wide they want to be — and hand it to [Xlsx], which knows how to make a workbook of
 * anything. The columns and their order come from [TripTable], shared with the CSV, so
 * neither export can quietly drift from the other.
 */
object TripXlsx {

    const val MIME_TYPE = Xlsx.MIME_TYPE

    /**
     * @param columns which columns the sheet carries, in order. A spreadsheet is read by a
     * person, who is asked what they want in it; the CSV always carries everything.
     * @param titleOf what a column is called. Taken as a function rather than a list, so
     * that a title cannot end up over the wrong column.
     * @param nameOf resolves the stored name and resource key of a dictionary entry.
     * @return how many entries went into the sheet.
     */
    fun write(
        sheetName: String,
        columns: List<TripColumn>,
        titleOf: (TripColumn) -> String,
        entries: List<EntryDetails>,
        nameOf: (name: String, nameKey: String?) -> String,
        walletNameOf: (TripWalletDetails) -> String,
        target: OutputStream,
    ): Int = Xlsx.write(
        sheetName = sheetName,
        headers = columns.map(titleOf),
        widths = columns.map(::widthOf),
        rows = TripTable.rows(entries, columns, nameOf, walletNameOf),
        target = target,
    )

    /** In characters of the default font, the unit a spreadsheet sizes a column in. */
    private fun widthOf(column: TripColumn): Int = when (column) {
        TripColumn.DATE -> Xlsx.WIDTH_DATE
        TripColumn.TIME,
        TripColumn.QUANTITY,
        TripColumn.EXPENSE_CURRENCY,
        TripColumn.INCOME_CURRENCY,
        TripColumn.EXPENSE_SETTLEMENT_CURRENCY,
        TripColumn.INCOME_SETTLEMENT_CURRENCY -> Xlsx.WIDTH_SHORT
        TripColumn.EXPENSE_AMOUNT,
        TripColumn.INCOME_AMOUNT,
        TripColumn.EXPENSE_SETTLEMENT_AMOUNT,
        TripColumn.INCOME_SETTLEMENT_AMOUNT -> Xlsx.WIDTH_AMOUNT
        else -> Xlsx.WIDTH_TEXT
    }
}
