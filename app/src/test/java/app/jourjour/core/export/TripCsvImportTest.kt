package app.jourjour.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import app.jourjour.data.entity.Entry
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The import reads back what the export wrote, so most of what matters is checked by
 * sending a trip out and bringing it in again. The rest is about a file that is not quite
 * the export — columns left out or rearranged — and about refusing a file rather than
 * half-understanding it: a wrong header, a name no dictionary knows, a date that is not
 * one, a half the category wants and the row has not got.
 */
class TripCsvImportTest {

    private val headers = TripColumn.csv.map { it.name }
    private val tripId = 7L

    @Test
    fun what_was_exported_reads_back_unchanged() {
        // Yen paid from the dollar card, so the settled amount — what the card was charged
        // in dollars — has a place to be.
        val original = entry(
            category = exchange,
            title = "Lunch, tea and \"pie\"",
            place = "Pier",
            notes = "two\nlines",
            expenseAmountMilli = 1_250_000,
            expenseCurrency = jpy,
            expenseWallet = cardWallet,
            incomeAmountMilli = 300_000,
            settlementMilli = 20_150,
            latitude = 55.755826,
            longitude = 37.6173,
        )

        val entry = readOne(export(original))

        assertEquals(tripId, entry.tripId)
        assertEquals(exchange.id, entry.categoryId)
        assertEquals(LocalDate.of(2026, 4, 3), entry.dateTime.toLocalDate())
        assertEquals("Lunch, tea and \"pie\"", entry.title)
        assertEquals("Pier", entry.place)
        assertEquals("two\nlines", entry.notes)
        assertEquals(1_250_000L, entry.expenseAmountMilli)
        assertEquals("JPY", entry.expenseCurrencyCode)
        assertEquals(cardWallet.wallet.id, entry.expenseWalletId)
        assertEquals(300_000L, entry.incomeAmountMilli)
        assertEquals(cardWallet.wallet.id, entry.incomeWalletId)
        assertEquals(20_150L, entry.expenseSettlementAmountMilli)
        assertEquals(55.755826, entry.latitude!!, 1e-6)
        assertEquals(37.6173, entry.longitude!!, 1e-6)
    }

    @Test
    fun the_separator_is_found_from_the_header() {
        // A file written on a phone whose locale splits columns with a semicolon.
        val entry = readOne(export(entry(expenseAmountMilli = 500_000), separator = ";"))

        assertEquals(500_000L, entry.expenseAmountMilli)
    }

    @Test
    fun a_half_the_category_has_not_got_stays_empty() {
        val entry = readOne(export(entry(expenseAmountMilli = 1_000)))

        assertEquals(null, entry.incomeAmountMilli)
        assertEquals(null, entry.incomeCurrencyCode)
        assertEquals(null, entry.incomeWalletId)
        assertEquals(null, entry.latitude)
    }

    @Test
    fun the_time_of_day_survives_the_round_trip() {
        val entry = readOne(
            export(entry(dateTime = LocalDateTime.of(2026, 4, 3, 17, 45), expenseAmountMilli = 1_000))
        )

        assertEquals(17, entry.dateTime.hour)
        assertEquals(45, entry.dateTime.minute)
    }

    @Test
    fun columns_may_come_in_any_order_and_the_optional_ones_may_be_left_out() {
        // Rearranged in a spreadsheet and cut down to what has to be there.
        val text = "CATEGORY,DATE,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET,TIME\r\n" +
            "Restaurants,2026-04-03,12.50,USD,Cash,9:15\r\n"

        val entry = readOne(text)

        assertEquals(12_500L, entry.expenseAmountMilli)
        assertEquals(cashWallet.wallet.id, entry.expenseWalletId)
        assertEquals(9, entry.dateTime.hour)
        assertEquals("", entry.title)
        assertEquals(null, entry.quantityMilli)
    }

    @Test
    fun a_quantity_is_read_only_where_the_category_counts_one() {
        val counted = readOne(
            export(entry(category = fuel, quantityMilli = 41_500, expenseAmountMilli = 400_000))
        )
        assertEquals(41_500L, counted.quantityMilli)

        // A file filled in by hand carries a 1 in every row, under categories that count
        // nothing. Stored, it would be a figure the form never shows and nobody can clear,
        // and the totals would add it up into a count of the entries.
        val byHand = "DATE,CATEGORY,QUANTITY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,Restaurants,1,12.50,USD,Cash\r\n"

        assertEquals(null, readOne(byHand).quantityMilli)
    }

    @Test
    fun a_column_the_app_does_not_know_is_passed_over() {
        val text = export(entry(expenseAmountMilli = 1_000)).replaceFirst("NOTES", "NOTES,EXTRA")

        assertEquals(1_000L, readOne(text).expenseAmountMilli)
    }

    @Test
    fun a_missing_required_column_is_named() {
        val text = "DATE,TIME,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,9:15,12.50,USD,Cash\r\n"

        assertEquals(TripCsvImport.Result.MissingColumns(listOf(TripColumn.CATEGORY)), read(text))
    }

    @Test
    fun a_file_without_a_time_column_starts_every_day() {
        val text = "DATE,CATEGORY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,Restaurants,12.50,USD,Cash\r\n"

        assertEquals(LocalDateTime.of(2026, 4, 3, 0, 0), readOne(text).dateTime)
    }

    @Test
    fun a_date_the_way_a_spreadsheet_writes_it_is_read() {
        // A file saved from a Russian spreadsheet carries its dates day first.
        val text = "DATE,CATEGORY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "01.05.2025,Restaurants,12.50,USD,Cash\r\n" +
            "2.5.25,Restaurants,3,USD,Cash\r\n"

        val result = read(text) as TripCsvImport.Result.Ready

        assertEquals(LocalDate.of(2025, 5, 1), result.entries[0].dateTime.toLocalDate())
        assertEquals(LocalDate.of(2025, 5, 2), result.entries[1].dateTime.toLocalDate())
    }

    @Test
    fun a_wallet_that_fixes_its_currency_takes_no_other() {
        // Cash in dollars cannot be paid in yen, on the form or from a file.
        val text = "DATE,CATEGORY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,Restaurants,12.50,JPY,Cash\r\n"

        assertEquals(
            TripCsvImport.Result.WrongCurrency(2, TripColumn.EXPENSE_CURRENCY, "JPY", cashWallet),
            read(text),
        )
    }

    @Test
    fun a_settled_amount_is_refused_where_the_currency_is_the_wallets_own() {
        // Dollars from the dollar cash: the bank charged the amount entered, there is nothing to settle.
        val text = export(entry(expenseAmountMilli = 1_000, settlementMilli = 500))

        assertEquals(
            TripCsvImport.Result.Unexpected(2, TripColumn.EXPENSE_SETTLEMENT_AMOUNT, "0.50"),
            read(text),
        )
    }

    @Test
    fun an_amount_is_rounded_to_the_decimals_of_its_currency() {
        // Half a yen does not exist; the form would not take it, and neither does a file.
        val text = "DATE,CATEGORY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,Restaurants,500.5,JPY,Card\r\n"

        assertEquals(501_000L, readOne(text).expenseAmountMilli)
    }

    @Test
    fun a_foreign_header_is_refused() {
        val text = "Date,Category\r\n2026-04-03,Restaurants\r\n"

        assertEquals(TripCsvImport.Result.HeaderMismatch, read(text))
    }

    @Test
    fun a_half_the_category_calls_for_has_to_be_filled_in() {
        // An exchange with the income left blank: money that left and arrived nowhere.
        val text = export(entry(category = exchange, expenseAmountMilli = 1_000))

        assertEquals(TripCsvImport.Result.Empty(2, TripColumn.INCOME_AMOUNT), read(text))
    }

    @Test
    fun a_half_the_category_calls_for_may_not_be_missing_from_the_file() {
        val text = "DATE,TIME,CATEGORY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,9:15,Exchange,12.50,USD,Cash\r\n"

        assertEquals(TripCsvImport.Result.Empty(2, TripColumn.INCOME_AMOUNT), read(text))
    }

    @Test
    fun an_amount_on_a_half_the_category_has_not_got_is_refused() {
        // A refund is an income; an expense amount on it is money going nowhere the entry shows.
        val text = export(entry(category = refunds, expenseAmountMilli = 1_000, incomeAmountMilli = 2_000))

        assertEquals(
            TripCsvImport.Result.Unexpected(2, TripColumn.EXPENSE_AMOUNT, "1.00"),
            read(text),
        )
    }

    @Test
    fun an_amount_without_its_currency_or_wallet_is_refused() {
        val text = "DATE,TIME,CATEGORY,EXPENSE_AMOUNT,EXPENSE_CURRENCY,EXPENSE_WALLET\r\n" +
            "2026-04-03,9:15,Restaurants,12.50,,Cash\r\n"

        assertEquals(TripCsvImport.Result.Empty(2, TripColumn.EXPENSE_CURRENCY), read(text))
    }

    @Test
    fun an_unknown_category_stops_the_import() {
        val text = export(entry(expenseAmountMilli = 1_000)).replace("Restaurants", "Hovercraft")

        val result = read(text)
        assertTrue(result.toString(), result is TripCsvImport.Result.Unknown)
        result as TripCsvImport.Result.Unknown
        assertEquals(2, result.line)
        assertEquals(TripColumn.CATEGORY, result.column)
        assertEquals("Hovercraft", result.value)
    }

    @Test
    fun an_unknown_currency_stops_the_import() {
        val text = export(entry(expenseAmountMilli = 1_000)).replace(",USD,", ",XYZ,")

        val result = read(text)
        assertTrue(result.toString(), result is TripCsvImport.Result.Unknown)
        assertEquals(TripColumn.EXPENSE_CURRENCY, (result as TripCsvImport.Result.Unknown).column)
    }

    @Test
    fun a_date_that_will_not_parse_stops_the_import() {
        val text = export(entry(expenseAmountMilli = 1_000)).replace("2026-04-03", "3 April")

        val result = read(text)
        assertTrue(result.toString(), result is TripCsvImport.Result.Malformed)
        assertEquals(TripColumn.DATE, (result as TripCsvImport.Result.Malformed).column)
    }

    @Test
    fun no_entry_gets_in_when_the_last_row_is_bad() {
        val text = export(
            entry(id = 1, title = "good", expenseAmountMilli = 1_000),
            entry(id = 2, title = "bad", expenseAmountMilli = 1_000),
        ).replace("2026-04-03,12:30,Restaurants,bad", "nonsense,12:30,Restaurants,bad")

        // The second row is the one at fault; nothing at all comes back.
        assertTrue(read(text) is TripCsvImport.Result.Malformed)
    }

    @Test
    fun the_line_number_counts_as_a_spreadsheet_does() {
        val text = export(
            entry(id = 1, dateTime = LocalDateTime.of(2026, 4, 3, 9, 0), expenseAmountMilli = 1_000),
            entry(id = 2, dateTime = LocalDateTime.of(2026, 4, 4, 9, 0), expenseAmountMilli = 1_000),
            entry(id = 3, dateTime = LocalDateTime.of(2026, 4, 5, 9, 0), expenseAmountMilli = 1_000),
        ).replace("2026-04-05", "later on")

        // Header is line 1, so the third entry sits on line 4.
        assertEquals(4, (read(text) as TripCsvImport.Result.Malformed).line)
    }

    private fun export(vararg entries: TestEntry, separator: String = ","): String =
        TripCsv.build(
            headers = headers,
            entries = entries.toList(),
            separator = separator,
            nameOf = { name, _ -> name },
            walletNameOf = ::walletLabel,
        )

    private fun read(text: String): TripCsvImport.Result = TripCsvImport.read(
        text = text,
        headers = headers,
        tripId = tripId,
        categories = listOf(dining, exchange, refunds, fuel),
        wallets = listOf(cashWallet, cardWallet),
        currencies = listOf(usd, jpy, kwd),
        nameOf = { name, _ -> name },
        walletNameOf = ::walletLabel,
    )

    private fun readOne(text: String): Entry {
        val result = read(text)
        assertTrue(result.toString(), result is TripCsvImport.Result.Ready)
        return (result as TripCsvImport.Result.Ready).entries.single()
    }
}
