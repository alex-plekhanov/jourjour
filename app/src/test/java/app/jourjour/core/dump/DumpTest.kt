package app.jourjour.core.dump

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A dump is read by the same code that wrote it, so what matters is that nothing is lost
 * on the way through the text — nulls included, since a wallet without a name of its own
 * has to come back without one — and that a file with a field this build does not know
 * still reads.
 */
class DumpTest {

    private val dump = Dump(
        databaseVersion = 13,
        exportedAt = "2026-09-16T10:00:00",
        currencies = listOf(CurrencyDump("USD", "US Dollar", "$", 2, nameKey = "currency_usd", usedAt = null)),
        paymentMethods = listOf(PaymentMethodDump("Cash", "💵", fixedCurrency = true, nameKey = "payment_cash")),
        categories = listOf(
            CategoryDump(
                "Restaurants", "🍽", isExpense = true, isIncome = false, trackQuantity = false,
                nameKey = "category_restaurants",
                favorites = listOf(FavoriteDump("Lunch", nameKey = "favorite_lunch")),
            ),
        ),
        trips = listOf(
            TripDump(
                name = "Japan",
                startDate = "2026-04-03",
                endDate = "2026-04-17",
                createdAt = "2026-04-01T09:00:00",
                wallets = listOf(
                    WalletDump(
                        name = null,
                        paymentMethod = NameRef(nameKey = "payment_cash"),
                        currencyCode = "USD",
                        openingBalanceMilli = 100_000,
                        recounts = listOf(RecountDump("2026-04-05T21:00:00", 80_000, "evening")),
                    ),
                ),
                entries = listOf(
                    EntryDump(
                        dateTime = "2026-04-03T12:30:00",
                        category = NameRef(nameKey = "category_restaurants"),
                        title = "Lunch",
                        expenseWallet = WalletRef(paymentMethod = NameRef(nameKey = "payment_cash"), currencyCode = "USD"),
                        expenseAmountMilli = 12_500,
                        expenseCurrencyCode = "USD",
                        photoFile = "abc.jpg",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `a dump comes back from its text as it went in`() {
        assertEquals(dump, Dump.decode(Dump.encode(dump)))
    }

    @Test
    fun `a wallet without a name of its own is written as having none`() {
        val text = Dump.encode(dump)

        assertTrue(text, text.contains("\"name\": null"))
    }

    @Test
    fun `a field this build does not know is passed over`() {
        val text = Dump.encode(dump).replaceFirst("\"app\":", "\"colour\": \"blue\",\n    \"app\":")

        assertEquals(dump, Dump.decode(text))
    }

    /**
     * The thing older dumps being accepted rests on: a file written before a field existed
     * has to read back with that field at its default rather than refusing to read at all.
     * Every schema change so far has only added, so this holds for all of them.
     */
    @Test
    fun `a field a dump was written before still reads, at its default`() {
        val olderText = Dump.encode(dump)
            .replace(Regex("\\n\\s*\"rates\": \\[\\s*\\],"), "")

        assertTrue(olderText, !olderText.contains("\"rates\""))
        val older = Dump.decode(olderText)
        assertEquals(emptyList<RateDump>(), older.trips.single().rates)
        assertEquals(dump, older)
    }

    @Test
    fun `a reference names a row by its key when it has one, else by its text`() {
        assertEquals(NameRef(nameKey = "k"), NameRef.of("Text", "k"))
        assertEquals(NameRef(name = "Text"), NameRef.of("Text", null))
    }
}
