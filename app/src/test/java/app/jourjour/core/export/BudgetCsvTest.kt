package app.jourjour.core.export

import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget out to a table and back. What is checked is the round trip and the two ways a
 * row can say what a line costs — the total, or a price and a quantity for the total to be
 * made of, which is how a spreadsheet filled in by hand says it.
 */
class BudgetCsvTest {

    private val tripId = 7L

    private val hotels = Category(id = 1, name = "Accommodation", icon = "🏨", sortOrder = 1)
    private val flights = Category(id = 2, name = "Getting there", icon = "✈️", sortOrder = 2)
    private val refunds = Category(
        id = 3,
        name = "Refunds",
        icon = "💰",
        sortOrder = 3,
        isExpense = false,
        isIncome = true,
    )

    private val usd = Currency("USD", "US Dollar", "$", decimals = 2)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", decimals = 0)

    private val headers = BudgetColumn.entries.map { it.name }

    @Test
    fun what_was_exported_reads_back_unchanged() {
        val original = item(
            category = hotels,
            title = "Ten nights",
            notes = "two\nlines",
            quantityMilli = 10_000,
            amountMilli = 500_000,
            currency = usd,
        )

        val line = readOne(export(original))

        assertEquals(tripId, line.tripId)
        assertEquals(hotels.id, line.categoryId)
        assertEquals("Ten nights", line.title)
        assertEquals("two\nlines", line.notes)
        assertEquals(10_000L, line.quantityMilli)
        assertEquals(500_000L, line.amountMilli)
        assertEquals("USD", line.currencyCode)
    }

    @Test
    fun the_price_is_written_out_although_it_is_not_stored() {
        val csv = export(
            item(category = hotels, quantityMilli = 10_000, amountMilli = 500_000, currency = usd)
        )

        // Fifty dollars a night, which is how the five hundred was arrived at.
        val fields = csv.records()[1].split(",")
        assertEquals("50.00", fields[BudgetColumn.entries.indexOf(BudgetColumn.PRICE)])
        assertEquals("10", fields[BudgetColumn.entries.indexOf(BudgetColumn.QUANTITY)])
    }

    @Test
    fun a_row_with_a_price_and_a_quantity_but_no_total_is_what_they_come_to() {
        val text = "CATEGORY,TITLE,PRICE,QUANTITY,AMOUNT,CURRENCY\r\n" +
            "Getting there,,60000,3,,USD\r\n"

        val line = readOne(text)

        assertEquals(180_000_000L, line.amountMilli)
        assertEquals(3_000L, line.quantityMilli)
    }

    @Test
    fun the_total_as_written_wins_over_the_price() {
        // A price rounded for the file need not multiply back out to the figure the app
        // kept, and the figure it kept is the one it should go on keeping.
        val text = "CATEGORY,TITLE,PRICE,QUANTITY,AMOUNT,CURRENCY\r\n" +
            "Accommodation,Hotel,33.33,3,100,USD\r\n"

        assertEquals(100_000L, readOne(text).amountMilli)
    }

    @Test
    fun a_row_naming_no_figure_at_all_is_refused() {
        val text = "CATEGORY,TITLE,AMOUNT,CURRENCY\r\nAccommodation,Hotel,,USD\r\n"

        val result = read(text)

        assertEquals(
            BudgetCsvImport.Result.Empty(2, BudgetColumn.AMOUNT),
            result,
        )
    }

    @Test
    fun a_file_without_a_column_it_has_to_carry_is_refused_whole() {
        // The name among them, although a line may have none: it is half of what tells
        // two lines apart, and without it every nameless line would look like the same one.
        val text = "CATEGORY,AMOUNT,CURRENCY\r\nAccommodation,100,USD\r\n"

        assertEquals(
            BudgetCsvImport.Result.MissingColumns(listOf(BudgetColumn.TITLE)),
            read(text),
        )
    }

    @Test
    fun a_category_that_brings_money_in_has_nothing_to_budget() {
        val text = "CATEGORY,TITLE,AMOUNT,CURRENCY\r\nRefunds,,100,USD\r\n"

        assertEquals(BudgetCsvImport.Result.NotAnExpense(2, "Refunds"), read(text))
    }

    @Test
    fun a_currency_the_dictionary_lacks_is_named() {
        val text = "CATEGORY,TITLE,AMOUNT,CURRENCY\r\nAccommodation,,100,XXX\r\n"

        assertEquals(
            BudgetCsvImport.Result.Unknown(2, BudgetColumn.CURRENCY, "XXX"),
            read(text),
        )
    }

    @Test
    fun a_file_naming_none_of_the_columns_is_refused_before_anything_is_read() {
        assertEquals(
            BudgetCsvImport.Result.HeaderMismatch,
            read("What,Where\r\nsomething,somewhere\r\n"),
        )
    }

    @Test
    fun a_semicolon_brings_a_comma_into_the_numbers() {
        val csv = export(
            item(category = hotels, quantityMilli = 10_000, amountMilli = 500_000, currency = usd),
            separator = ";",
        )

        assertTrue(csv, csv.records()[1].contains(";50,00;10;500,00;USD"))
    }

    @Test
    fun a_currency_with_no_minor_unit_is_written_without_one() {
        val csv = export(item(category = flights, amountMilli = 90_000_000, currency = jpy))

        assertTrue(csv, csv.records()[1].contains(",90000,JPY"))
    }

    @Test
    fun a_line_with_no_quantity_leaves_both_that_and_the_price_blank() {
        val csv = export(item(category = flights, amountMilli = 180_000_000, currency = usd))
        val fields = csv.records()[1].split(",")

        assertEquals("", fields[BudgetColumn.entries.indexOf(BudgetColumn.PRICE)])
        assertEquals("", fields[BudgetColumn.entries.indexOf(BudgetColumn.QUANTITY)])
        assertNull(readOne(csv).quantityMilli)
    }

    private fun export(vararg items: BudgetItemDetails, separator: String = ","): String =
        BudgetCsv.build(
            headers = headers,
            items = items.toList(),
            separator = separator,
            nameOf = { name, _ -> name },
        )

    private fun read(text: String): BudgetCsvImport.Result = BudgetCsvImport.read(
        text = text,
        headers = headers,
        tripId = tripId,
        categories = listOf(hotels, flights),
        currencies = listOf(usd, jpy),
        nameOf = { name, _ -> name },
    )

    private fun readOne(text: String): BudgetItem =
        (read(text) as BudgetCsvImport.Result.Ready).items.single()

    /** The records of a file, the byte-order mark and the trailing newline taken off. */
    private fun String.records(): List<String> =
        removePrefix(CsvWriter.BOM).trimEnd('\r', '\n').split("\r\n")

    private fun item(
        id: Long = 1,
        category: Category,
        title: String = "",
        notes: String = "",
        quantityMilli: Long? = null,
        amountMilli: Long,
        currency: Currency,
    ) = BudgetItemDetails(
        item = BudgetItem(
            id = id,
            tripId = tripId,
            categoryId = category.id,
            title = title,
            notes = notes,
            quantityMilli = quantityMilli,
            amountMilli = amountMilli,
            currencyCode = currency.code,
        ),
        category = category,
        currency = currency,
    )
}
