package app.jourjour.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import java.time.LocalDateTime

/**
 * The report answers "where did the money go", so what is checked is the adding up: each
 * currency on its own, a category once however many entries fed it, and the awkward cases —
 * an exchange, which is an expense in one currency and an income in another, and a quantity,
 * which belongs to the entry rather than to either half.
 */
class TripStatisticsTest {

    private val usd = Currency("USD", "US Dollar", "$", decimals = 2)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", decimals = 0)
    private val cash = PaymentMethod(id = 1, name = "Cash", icon = "💵", fixedCurrency = true)
    private val wallet = TripWalletDetails(
        wallet = TripWallet(id = 1, tripId = 1, paymentMethodId = cash.id, currencyCode = usd.code),
        paymentMethod = cash,
        currency = usd,
    )
    private val dining = Category(id = 1, name = "Restaurants", icon = "🍽", sortOrder = 1)
    private val fuel = Category(id = 2, name = "Fuel", icon = "⛽", sortOrder = 2, trackQuantity = true)
    private val exchange =
        Category(id = 3, name = "Currency exchange", icon = "💱", sortOrder = 3, trackQuantity = true)

    @Test
    fun currencies_are_counted_apart() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 1_000_000L to usd),
                entry(id = 2, category = dining, expense = 500_000L to jpy),
            )
        )

        assertEquals(listOf("JPY", "USD"), reports.map { it.currency.code })
        assertEquals(500_000L, reports.first { it.currency.code == "JPY" }.expense.milli)
        assertEquals(1_000_000L, reports.first { it.currency.code == "USD" }.expense.milli)
    }

    @Test
    fun a_currency_nets_out_to_one_side_or_the_other() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 1_000_000L to usd),
                entry(id = 2, category = exchange, income = 100_000L to usd),
            )
        )
        val usdReport = reports.single()

        // Both halves are still there for the categories to be read against each other.
        assertEquals(1_000_000L, usdReport.expense.milli)
        assertEquals(100_000L, usdReport.income.milli)
        // The foot of the block reports being 900 down, and nothing on the other side.
        assertEquals(900_000L, usdReport.netExpense?.milli)
        assertEquals(null, usdReport.netIncome)
    }

    @Test
    fun a_currency_more_received_than_spent_nets_to_income() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 100_000L to usd),
                entry(id = 2, category = exchange, income = 1_000_000L to usd),
            )
        )
        val usdReport = reports.single()

        assertEquals(900_000L, usdReport.netIncome?.milli)
        assertEquals(null, usdReport.netExpense)
    }

    @Test
    fun a_currency_that_came_out_even_reports_neither_side() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 500_000L to usd),
                entry(id = 2, category = exchange, income = 500_000L to usd),
            )
        )
        val usdReport = reports.single()

        // Zero is shown as a blank, and the other column has nothing to show either.
        assertEquals(0L, usdReport.netExpense?.milli)
        assertEquals(null, usdReport.netIncome)
    }

    @Test
    fun a_category_adds_up_all_of_its_entries() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 1_000_000L to usd),
                entry(id = 2, category = dining, expense = 250_000L to usd),
                entry(id = 3, category = fuel, expense = 400_000L to usd),
            )
        )

        val categories = reports.single().categories
        assertEquals(2, categories.size)
        assertEquals(1_250_000L, categories.first { it.category.id == dining.id }.expense.milli)
        assertEquals(400_000L, categories.first { it.category.id == fuel.id }.expense.milli)
    }

    @Test
    fun a_currency_exchange_lands_in_both_groups() {
        val reports = TripStatistics.of(
            listOf(
                entry(
                    category = exchange,
                    expense = 100_000L to usd,
                    income = 9_000_000L to jpy,
                )
            )
        )

        val dollars = reports.first { it.currency.code == "USD" }
        val yen = reports.first { it.currency.code == "JPY" }
        assertEquals(100_000L, dollars.expense.milli)
        assertEquals(0L, dollars.income.milli)
        assertEquals(9_000_000L, yen.income.milli)
        assertEquals(0L, yen.expense.milli)
    }

    @Test
    fun a_quantity_is_not_doubled_by_an_exchange() {
        val reports = TripStatistics.of(
            listOf(
                entry(
                    category = exchange,
                    expense = 100_000L to usd,
                    income = 9_000_000L to jpy,
                    quantityMilli = 2_000,
                )
            )
        )

        // It goes under the currency the money left in, and nowhere else.
        assertEquals(2_000L, reports.first { it.currency.code == "USD" }.categories.single().quantity?.milli)
        assertNull(reports.first { it.currency.code == "JPY" }.categories.single().quantity)
    }

    @Test
    fun a_quantity_is_there_only_where_it_is_counted() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = fuel, expense = 400_000L to usd, quantityMilli = 41_500),
                entry(id = 2, category = dining, expense = 100_000L to usd),
            )
        )

        val categories = reports.single().categories
        assertEquals(41_500L, categories.first { it.category.id == fuel.id }.quantity?.milli)
        assertNull(categories.first { it.category.id == dining.id }.quantity)
    }

    @Test
    fun a_quantity_stored_under_a_category_that_counts_nothing_is_left_out() {
        // Two entries carrying one each, under a category whose form has no such field —
        // a file imported with a 1 in every row leaves exactly this. Added up they would
        // show as two, which is the number of entries and nothing a person ever entered.
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 100_000L to usd, quantityMilli = 1_000),
                entry(id = 2, category = dining, expense = 200_000L to usd, quantityMilli = 1_000),
            )
        )

        assertNull(reports.single().categories.single().quantity)
    }

    @Test
    fun the_total_of_a_currency_is_the_sum_of_its_categories() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 1_000_000L to usd),
                entry(id = 2, category = fuel, expense = 400_000L to usd),
                entry(id = 3, category = dining, income = 50_000L to usd),
            )
        )

        val report = reports.single()
        assertEquals(1_400_000L, report.expense.milli)
        assertEquals(50_000L, report.income.milli)
    }

    @Test
    fun categories_follow_the_order_of_the_dictionary() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = dining, expense = 100_000L to usd),
                entry(id = 2, category = fuel, expense = 900_000L to usd),
                entry(id = 3, category = exchange, expense = 500_000L to usd),
            )
        )

        // Not by amount: the same category has to sit in the same place in every block.
        assertEquals(
            listOf(dining.id, fuel.id, exchange.id),
            reports.single().categories.map { it.category.id },
        )
    }

    @Test
    fun the_order_is_the_same_in_every_currency_block() {
        val reports = TripStatistics.of(
            listOf(
                entry(category = fuel, expense = 900_000L to usd),
                entry(id = 2, category = dining, expense = 100_000L to usd),
                entry(id = 3, category = fuel, expense = 100_000L to jpy),
                entry(id = 4, category = dining, expense = 900_000L to jpy),
            )
        )

        val orders = reports.map { report -> report.categories.map { it.category.id } }
        assertEquals(listOf(listOf(dining.id, fuel.id), listOf(dining.id, fuel.id)), orders)
    }

    @Test
    fun with_no_entries_the_report_is_empty() {
        assertEquals(emptyList<CurrencyReport>(), TripStatistics.of(emptyList()))
    }

    private fun entry(
        id: Long = 1,
        category: Category,
        expense: Pair<Long, Currency>? = null,
        income: Pair<Long, Currency>? = null,
        quantityMilli: Long? = null,
    ) = EntryDetails(
        entry = Entry(
            id = id,
            tripId = 1,
            categoryId = category.id,
            dateTime = LocalDateTime.of(2026, 4, 3, 12, 0),
            quantityMilli = quantityMilli,
            expenseWalletId = wallet.wallet.id.takeIf { expense != null },
            expenseAmountMilli = expense?.first,
            expenseCurrencyCode = expense?.second?.code,
            incomeWalletId = wallet.wallet.id.takeIf { income != null },
            incomeAmountMilli = income?.first,
            incomeCurrencyCode = income?.second?.code,
        ),
        category = category,
        expenseWallet = wallet.takeIf { expense != null },
        incomeWallet = wallet.takeIf { income != null },
        expenseCurrency = expense?.second,
        incomeCurrency = income?.second,
    )
}
