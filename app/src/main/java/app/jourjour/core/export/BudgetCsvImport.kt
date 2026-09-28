package app.jourjour.core.export

import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Reads a budget back from a table in the shape [BudgetCsv] writes — or part of it.
 *
 * The columns are found by their titles in the first line, in whatever order they come, so
 * a file cut down or rearranged in a spreadsheet still reads. A line needs a category, a
 * currency and a figure; the figure may be the total, or a price and a quantity for the
 * total to be made of, which is how a budget is written by hand.
 *
 * Nothing is written until the whole file has been understood. A row that cannot be made
 * sense of stops the import and is reported with its line number: half an import is worse
 * than none, because there is no telling by eye which half arrived.
 */
object BudgetCsvImport {

    /** The separators an export can have used, tried against the header in this order. */
    private val SEPARATORS = listOf(',', ';')

    /**
     * What a file has to carry, whatever else it leaves out. Not the total: a row may name
     * a price and a quantity instead, and which of the two the file uses is a question for
     * the row rather than for the header.
     */
    val REQUIRED: List<BudgetColumn> = listOf(BudgetColumn.CATEGORY, BudgetColumn.CURRENCY)

    sealed interface Result {

        /** The lines the file describes, in the order it describes them. */
        data class Ready(val items: List<BudgetItem>) : Result

        /** No arrangement of the separators produced a first line naming any column. */
        data object HeaderMismatch : Result

        /** The first line names some columns, but not all of [REQUIRED]; these are missing. */
        data class MissingColumns(val columns: List<BudgetColumn>) : Result

        /** A value the dictionaries know nothing about. [line] counts from 1, header included. */
        data class Unknown(val line: Int, val column: BudgetColumn, val value: String) : Result

        /** A value of the wrong shape: a number that is not one. */
        data class Malformed(val line: Int, val column: BudgetColumn, val value: String) : Result

        /** A column that has to say something and says nothing. */
        data class Empty(val line: Int, val column: BudgetColumn) : Result

        /** A category that exists but has nothing to budget: an income, or an exchange. */
        data class NotAnExpense(val line: Int, val value: String) : Result
    }

    /**
     * @param headers the titles this app would write, in [BudgetColumn] order — the fixed
     *   names the first line of the file is matched against.
     * @param categories what a line may be filed under, already narrowed to the ones the
     *   form offers; anything else in the file is reported rather than quietly filed.
     * @param nameOf resolves a category's stored name and resource key, so that a category
     *   is matched by the name the export actually put in the file.
     */
    fun read(
        text: String,
        headers: List<String>,
        tripId: Long,
        categories: List<Category>,
        currencies: List<Currency>,
        nameOf: (name: String, nameKey: String?) -> String,
    ): Result {
        val columnOf = BudgetColumn.entries.zip(headers)
            .associate { (column, title) -> title to column }

        // Whichever separator makes the first line name the most columns: the file may
        // have been written on a phone whose locale splits columns differently.
        val (separator, positions) = SEPARATORS
            .map { candidate ->
                candidate to positions(CsvReader.read(text, candidate).firstOrNull(), columnOf)
            }
            .maxBy { (_, found) -> found.size }
        if (positions.isEmpty()) return Result.HeaderMismatch
        val missing = REQUIRED.filter { it !in positions }
        if (missing.isNotEmpty()) return Result.MissingColumns(missing)

        val decimal = TripCsv.decimalOf(separator.toString())
        val budgetable = categories.associateBy { nameOf(it.name, it.nameKey) }
        val known = currencies.associateBy { it.code }

        val items = mutableListOf<BudgetItem>()
        CsvReader.read(text, separator).drop(1).forEachIndexed { index, fields ->
            // The header is line 1, so the first record is line 2 — what a spreadsheet shows.
            val line = index + 2

            fun field(column: BudgetColumn): String =
                positions[column]?.let { fields.getOrNull(it) }.orEmpty().trim()

            val rawCategory = field(BudgetColumn.CATEGORY)
            if (rawCategory.isEmpty()) return Result.Empty(line, BudgetColumn.CATEGORY)
            val category = budgetable[rawCategory] ?: return Result.NotAnExpense(line, rawCategory)

            val rawCurrency = field(BudgetColumn.CURRENCY)
            if (rawCurrency.isEmpty()) return Result.Empty(line, BudgetColumn.CURRENCY)
            val currency = known[rawCurrency]
                ?: return Result.Unknown(line, BudgetColumn.CURRENCY, rawCurrency)

            val quantity = when (val parsed = number(field(BudgetColumn.QUANTITY), decimal)) {
                is Parsed.Bad -> return Result.Malformed(line, BudgetColumn.QUANTITY, parsed.value)
                is Parsed.Good -> parsed.value
            }
            val price = when (val parsed = number(field(BudgetColumn.PRICE), decimal)) {
                is Parsed.Bad -> return Result.Malformed(line, BudgetColumn.PRICE, parsed.value)
                is Parsed.Good -> parsed.value
            }
            val total = when (val parsed = number(field(BudgetColumn.AMOUNT), decimal)) {
                is Parsed.Bad -> return Result.Malformed(line, BudgetColumn.AMOUNT, parsed.value)
                is Parsed.Good -> parsed.value
            }

            // The total as written, or what the price and the quantity make of it. Written
            // wins where both are there: it is the figure the app itself keeps, and a
            // rounded price multiplied back out need not come to it exactly.
            val amount = total ?: price?.let { unit -> quantity?.let(unit::multiply) }
                ?: return Result.Empty(line, BudgetColumn.AMOUNT)

            items += BudgetItem(
                tripId = tripId,
                categoryId = category.id,
                title = field(BudgetColumn.TITLE),
                notes = field(BudgetColumn.NOTES),
                quantityMilli = quantity?.let { milli(it, Quantity.SCALE) },
                amountMilli = milli(amount, currency.decimals),
                currencyCode = currency.code,
            )
        }
        return Result.Ready(items)
    }

    /** A number as written, or nothing for a blank field; the scale comes later. */
    private fun number(value: String, decimal: Char): Parsed {
        if (value.isEmpty()) return Parsed.Good(null)
        val plain = value.replace(decimal, '.')
        val parsed = runCatching { BigDecimal(plain) }.getOrNull() ?: return Parsed.Bad(value)
        return Parsed.Good(parsed)
    }

    /**
     * Thousandths, the scale everything is held in, of a value first rounded to the
     * [decimals] it is allowed — the form does the same, so that no fraction a person
     * never saw can settle into the database.
     */
    private fun milli(value: BigDecimal, decimals: Int): Long =
        value.setScale(decimals, RoundingMode.HALF_UP)
            .setScale(SCALE, RoundingMode.HALF_UP)
            .movePointRight(SCALE)
            .longValueExact()

    /** Where each column the first line names is found; a title named twice keeps its first. */
    private fun positions(
        header: List<String>?,
        columnOf: Map<String, BudgetColumn>,
    ): Map<BudgetColumn, Int> {
        val found = mutableMapOf<BudgetColumn, Int>()
        header?.forEachIndexed { index, title ->
            val column = columnOf[title.trim()] ?: return@forEachIndexed
            found.putIfAbsent(column, index)
        }
        return found
    }

    /** A number that read cleanly — or nothing, for a blank field — or the text that did not. */
    private sealed interface Parsed {
        data class Good(val value: BigDecimal?) : Parsed
        data class Bad(val value: String) : Parsed
    }

    /** Amounts and quantities are held as whole thousandths. */
    private const val SCALE = 3
}
