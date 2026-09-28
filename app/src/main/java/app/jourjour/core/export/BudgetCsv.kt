package app.jourjour.core.export

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.data.entity.BudgetItemDetails
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What a budget line is made of. Written in this order, and read back by title in whatever
 * order the file happens to have them.
 */
enum class BudgetColumn(@get:StringRes val titleRes: Int) {
    CATEGORY(R.string.field_category),
    TITLE(R.string.field_name),
    NOTES(R.string.field_notes),
    PRICE(R.string.field_unit_price),
    QUANTITY(R.string.field_quantity),
    AMOUNT(R.string.field_amount),
    CURRENCY(R.string.field_currency),
}

/**
 * A trip's budget as a CSV table.
 *
 * The price is written although it is not stored: the file is meant to be read and edited
 * in a spreadsheet, and a line arrived at as so many at so much is unreadable without it.
 * [BudgetCsvImport] takes it back in the same spirit — where a row names a price and a
 * quantity but no total, the total is what they come to.
 */
object BudgetCsv {

    const val MIME_TYPE = TripCsv.MIME_TYPE

    /**
     * @param headers the column titles, one per [BudgetColumn], in that order.
     * @param separator what goes between columns, from `R.string.csv_separator`.
     * @param nameOf resolves a category's stored name and resource key, so that the file
     *   carries the name a person reads on the screen.
     */
    fun build(
        headers: List<String>,
        items: List<BudgetItemDetails>,
        separator: String,
        nameOf: (name: String, nameKey: String?) -> String,
    ): String = buildString {
        val decimal = TripCsv.decimalOf(separator)
        append(CsvWriter.BOM)
        append(CsvWriter.row(headers, separator))
        items.forEach { details ->
            val item = details.item
            val amount = Money(item.amountMilli)
            val quantity = item.quantityMilli?.let(::Quantity)
            append(
                CsvWriter.row(
                    listOf(
                        nameOf(details.category.name, details.category.nameKey),
                        item.title,
                        item.notes,
                        unitPrice(amount, quantity)
                            ?.let { number(it.toBigDecimal(), details.currency.decimals, decimal) }
                            .orEmpty(),
                        // No fixed number of decimals: ten nights should not read "10.000".
                        quantity?.let { number(it.toBigDecimal().stripTrailingZeros(), null, decimal) }
                            .orEmpty(),
                        number(amount.toBigDecimal(), details.currency.decimals, decimal),
                        details.currency.code,
                    ),
                    separator,
                )
            )
        }
    }

    /**
     * A figure with the decimal separator the file uses and the decimals it is written to
     * — the same rules the entries table is written by.
     *
     * @param decimals null to leave the value as it is, for a figure with no fixed shape.
     */
    private fun number(value: BigDecimal, decimals: Int?, decimal: Char): String {
        val scaled = decimals?.let { value.setScale(it, RoundingMode.HALF_UP) } ?: value
        // There is at most one dot to swap: nothing here is written with grouping.
        return scaled.toPlainString().replace('.', decimal)
    }
}
