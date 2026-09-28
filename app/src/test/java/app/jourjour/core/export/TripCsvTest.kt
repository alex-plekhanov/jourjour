package app.jourjour.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The export exists so that something else can read the file, which is what these check:
 * a fixed field order, quoting that survives a comma inside a name, and numbers and dates
 * written the same way whatever locale the phone is set to.
 */
class TripCsvTest {

    private val headers = TripColumn.csv.map { it.name }

    @Test
    fun fields_follow_the_order_of_the_columns() {
        val csv = build(
            entry(
                title = "Lunch",
                place = "Pier",
                notes = "with a view",
                expenseAmountMilli = 1_250_500,
                incomeAmountMilli = 300_000,
            )
        )

        assertEquals(
            "2026-04-03,12:30,Restaurants,Lunch,,1250.50,USD,Cash,300.00,USD,Card,,,,,Pier,,with a view",
            csv.records()[1],
        )
    }

    @Test
    fun there_are_as_many_columns_as_titles() {
        val csv = build(entry(expenseAmountMilli = 1_000))

        assertEquals(TripColumn.csv.size, csv.records()[0].split(",").size)
        assertEquals(TripColumn.csv.size, csv.records()[1].split(",").size)
    }

    @Test
    fun an_amount_is_written_to_the_decimals_of_its_currency() {
        val csv = build(entry(expenseAmountMilli = 500_000, expenseCurrency = jpy))

        // Yen has no minor unit: "500.00" would be a different number to a reader.
        assertTrue(csv, csv.records()[1].contains(",500,JPY,"))
    }

    @Test
    fun a_quantity_carries_no_trailing_zeros() {
        val csv = build(entry(category = fuel, quantityMilli = 41_500))

        assertEquals("41.5", csv.records()[1].split(",")[TripColumn.csv.indexOf(TripColumn.QUANTITY)])
    }

    @Test
    fun the_settled_amount_reaches_the_export() {
        val csv = build(
            entry(
                expenseAmountMilli = 500_000,
                expenseCurrency = jpy,
                settlementMilli = 20_150,
            )
        )
        val fields = csv.records()[1].split(",")

        assertEquals("20.15", fields[TripColumn.csv.indexOf(TripColumn.EXPENSE_SETTLEMENT_AMOUNT)])
        // The currency is the wallet's, written out for a reader rather than stored.
        assertEquals("USD", fields[TripColumn.csv.indexOf(TripColumn.EXPENSE_SETTLEMENT_CURRENCY)])
    }

    @Test
    fun coordinates_go_as_one_field_with_a_dot() {
        val csv = build(entry(latitude = 55.755826, longitude = 37.6173))
        val fields = csv.records()[1].split(",")

        // Quoted, since the pair carries a comma of its own — and always with a dot, so it
        // can be pasted into a map whatever the locale.
        assertTrue(csv, csv.records()[1].contains("\"55.755826, 37.617300\""))
        assertEquals(TripColumn.csv.size + 1, fields.size)
    }

    @Test
    fun commas_and_quotes_in_text_are_escaped() {
        val csv = build(entry(title = "Lunch, tea and \"pie\"", notes = "line one\nline two"))

        val record = csv.substringAfter("\r\n")
        assertTrue(record, record.contains("\"Lunch, tea and \"\"pie\"\"\""))
        assertTrue(record, record.contains("\"line one\nline two\""))
    }

    @Test
    fun a_semicolon_brings_a_comma_into_the_numbers() {
        val csv = build(
            ";",
            entry(category = fuel, quantityMilli = 41_500, expenseAmountMilli = 1_250_500),
        )

        // A spreadsheet that splits on a semicolon reads "1250.50" as text, not a number.
        assertEquals(
            "2026-04-03;12:30;Fuel;;41,5;1250,50;USD;Cash;;;;;;;;;;",
            csv.records()[1],
        )
    }

    @Test
    fun only_the_separator_in_use_is_escaped() {
        val csv = build(";", entry(title = "Lunch, tea", place = "Pier; the far one"))
        val record = csv.records()[1]

        // A comma is an ordinary character here — quoting it would put the amounts, which
        // now carry a comma of their own, in quotation marks as well.
        assertTrue(record, record.contains(";Lunch, tea;"))
        assertTrue(record, record.contains("\"Pier; the far one\""))
    }

    @Test
    fun records_come_oldest_first() {
        val csv = build(
            entry(id = 1, dateTime = LocalDateTime.of(2026, 4, 10, 12, 0), title = "later"),
            entry(id = 2, dateTime = LocalDateTime.of(2026, 4, 3, 12, 0), title = "earlier"),
        )

        assertTrue(csv, csv.records()[1].contains("earlier"))
        assertTrue(csv, csv.records()[2].contains("later"))
    }

    @Test
    fun the_file_starts_with_the_encoding_mark() {
        // Without it a spreadsheet reads the file as the system codepage.
        assertTrue(build(entry()).startsWith(TripCsv.BOM))
    }

    @Test
    fun empty_halves_give_empty_fields() {
        val csv = build(entry(expenseAmountMilli = null, incomeAmountMilli = null))

        assertEquals("2026-04-03,12:30,Restaurants" + ",".repeat(15), csv.records()[1])
    }

    private fun build(vararg entries: TestEntry): String = build(",", *entries)

    private fun build(separator: String, vararg entries: TestEntry): String =
        TripCsv.build(
            headers = headers,
            entries = entries.toList(),
            separator = separator,
            nameOf = { name, _ -> name },
            walletNameOf = ::walletLabel,
        )

    /** The header row comes first, then one line per entry; the trailing CRLF is dropped. */
    private fun String.records(): List<String> =
        removePrefix(TripCsv.BOM).removeSuffix("\r\n").split("\r\n")
}
