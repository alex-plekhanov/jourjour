package app.jourjour.core.stats

import app.jourjour.core.money.Money
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When a circle can be drawn at all. The slices have to be parts of one whole, so either
 * the totals have been brought to one currency or the trip only ever had one.
 */
class ExpenseChartTest {

    private val rub = Currency("RUB", "Russian Ruble", "₽", decimals = 2)
    private val thb = Currency("THB", "Thai Baht", "฿", decimals = 2)

    private val dining = Category(id = 1, name = "Restaurants", icon = "🍽", sortOrder = 1)
    private val refunds = Category(
        id = 2,
        name = "Refunds",
        icon = "↩",
        sortOrder = 2,
        isExpense = false,
        isIncome = true,
    )
    private val exchange = Category(
        id = 3,
        name = "Currency exchange",
        icon = "💱",
        sortOrder = 3,
        isExpense = true,
        isIncome = true,
    )

    private fun totals(category: Category, expense: Long = 0, income: Long = 0) = CategoryTotals(
        category = category,
        quantity = null,
        income = Money(income),
        expense = Money(expense),
    )

    private fun report(currency: Currency, vararg categories: CategoryTotals) = CurrencyReport(
        currency = currency,
        categories = categories.toList(),
        income = categories.fold(Money.ZERO) { sum, it -> sum + it.income },
        expense = categories.fold(Money.ZERO) { sum, it -> sum + it.expense },
    )

    private fun ready(vararg categories: CategoryTotals) =
        BaseTotals.Ready(report(rub, *categories))

    @Test
    fun `converted totals are what the circle is drawn from`() {
        val chart = expenseChartCategories(
            baseTotals = ready(totals(dining, expense = 1_000)),
            reports = listOf(report(thb, totals(dining, expense = 300))),
        )

        assertEquals(listOf(dining), chart.map { it.category })
        assertEquals(1_000L, chart.single().expense.milli)
    }

    @Test
    fun `one currency is its own whole, with no conversion at all`() {
        val chart = expenseChartCategories(
            baseTotals = null,
            reports = listOf(report(thb, totals(dining, expense = 300))),
        )

        assertEquals(listOf(dining), chart.map { it.category })
    }

    @Test
    fun `one currency can be drawn even while its rate is missing`() {
        val chart = expenseChartCategories(
            baseTotals = BaseTotals.RatesMissing(listOf("THB")),
            reports = listOf(report(thb, totals(dining, expense = 300))),
        )

        assertEquals(listOf(dining), chart.map { it.category })
    }

    @Test
    fun `several currencies with nothing to convert them by cannot be drawn`() {
        val chart = expenseChartCategories(
            baseTotals = BaseTotals.RatesMissing(listOf("THB")),
            reports = listOf(
                report(thb, totals(dining, expense = 300)),
                report(rub, totals(dining, expense = 1_000)),
            ),
        )

        assertEquals(emptyList<CategoryTotals>(), chart)
    }

    @Test
    fun `a currency the exchange alone put on the screen does not count as a second one`() {
        // Dollars changed into baht and the baht spent: two blocks, one of them money the
        // trip cost.
        val chart = expenseChartCategories(
            baseTotals = null,
            reports = listOf(
                report(
                    thb,
                    totals(dining, expense = 300),
                    totals(exchange, income = 5_000),
                ),
                report(rub, totals(exchange, expense = 1_000)),
            ),
        )

        assertEquals(listOf(dining), chart.map { it.category })
        assertEquals(300L, chart.single().expense.milli)
    }

    @Test
    fun `an exchange is left out of a single currency, as it is out of the converted total`() {
        val chart = expenseChartCategories(
            baseTotals = null,
            reports = listOf(
                report(
                    thb,
                    totals(dining, expense = 300),
                    totals(exchange, expense = 5_000),
                ),
            ),
        )

        assertEquals(listOf(dining), chart.map { it.category })
    }

    @Test
    fun `a category with nothing spent in it gets no slice`() {
        val chart = expenseChartCategories(
            baseTotals = ready(
                totals(dining, expense = 1_000),
                totals(refunds, income = 400),
            ),
            reports = emptyList(),
        )

        assertEquals(listOf(dining), chart.map { it.category })
    }

    @Test
    fun `a trip with nothing in it draws nothing`() {
        assertEquals(
            emptyList<CategoryTotals>(),
            expenseChartCategories(baseTotals = null, reports = emptyList()),
        )
    }
}
