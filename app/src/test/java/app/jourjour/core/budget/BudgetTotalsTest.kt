package app.jourjour.core.budget

import app.jourjour.data.entity.BudgetItem
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.TripRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Adding up a plan written in several currencies. What is checked is the same bargain the
 * statistics make: currencies apart until a rate says otherwise, and a clear reason when
 * there is no one figure to give.
 */
class BudgetTotalsTest {

    private val rub = Currency("RUB", "Russian Ruble", "₽", decimals = 2, sortOrder = 1)
    private val usd = Currency("USD", "US Dollar", "$", decimals = 2, sortOrder = 2)
    private val thb = Currency("THB", "Thai Baht", "฿", decimals = 2, sortOrder = 3)

    private val flights = Category(id = 1, name = "Getting there", icon = "✈️", sortOrder = 1)
    private val hotels = Category(id = 2, name = "Accommodation", icon = "🏨", sortOrder = 2)

    /** 1 USD = 84.78 RUB. */
    private val usdRate = TripRate(
        id = 1,
        tripId = 1,
        fromCurrencyCode = "USD",
        toCurrencyCode = "RUB",
        rateMilli = 84_780,
        multiplier = 1,
    )

    @Test
    fun `each currency is added up on its own`() {
        val totals = BudgetTotals.of(
            items = listOf(
                item(category = flights, amountMilli = 180_000_000, currency = rub),
                item(id = 2, category = hotels, amountMilli = 500_000, currency = usd),
                item(id = 3, category = hotels, amountMilli = 100_000, currency = usd),
            ),
            base = rub,
            rates = listOf(usdRate),
        )

        val byCurrency = requireNotNull(totals).byCurrency
        assertEquals(listOf("RUB", "USD"), byCurrency.map { it.currency.code })
        assertEquals(180_000_000L, byCurrency.first().amount.milli)
        assertEquals(600_000L, byCurrency.last().amount.milli)
    }

    @Test
    fun `the one figure goes through the trip's own rates`() {
        val totals = BudgetTotals.of(
            items = listOf(
                item(category = flights, amountMilli = 180_000_000, currency = rub),
                item(id = 2, category = hotels, amountMilli = 500_000, currency = usd),
            ),
            base = rub,
            rates = listOf(usdRate),
        )

        // 180 000 roubles and 500 dollars at 84.78.
        assertEquals(
            180_000_000L + 42_390_000L,
            (totals as BudgetTotals.Ready).total.amount.milli,
        )
        assertEquals(rub, totals.total.currency)
    }

    @Test
    fun `a currency with no rate is named instead of being left out of the total`() {
        val totals = BudgetTotals.of(
            items = listOf(
                item(category = flights, amountMilli = 180_000_000, currency = rub),
                item(id = 2, category = hotels, amountMilli = 30_000_000, currency = thb),
            ),
            base = rub,
            rates = listOf(usdRate),
        )

        assertEquals(listOf("THB"), (totals as BudgetTotals.RatesMissing).codes)
        // The lines still add up in their own currencies: that much is known either way.
        assertEquals(listOf("RUB", "THB"), totals.byCurrency.map { it.currency.code })
    }

    @Test
    fun `no base currency is said rather than passed over`() {
        val totals = BudgetTotals.of(
            items = listOf(item(category = flights, amountMilli = 180_000_000, currency = rub)),
            base = null,
            rates = emptyList(),
        )

        assertEquals(1, (totals as BudgetTotals.NoBaseCurrency).byCurrency.size)
    }

    @Test
    fun `a budget written in the base currency alone is already its own total`() {
        val totals = BudgetTotals.of(
            items = listOf(
                item(category = flights, amountMilli = 180_000_000, currency = rub),
                item(id = 2, category = hotels, amountMilli = 40_000_000, currency = rub),
            ),
            base = rub,
            rates = emptyList(),
        )

        assertEquals(220_000_000L, (totals as BudgetTotals.Ready).total.amount.milli)
    }

    @Test
    fun `an empty budget comes to nothing worth saying`() {
        assertNull(BudgetTotals.of(items = emptyList(), base = rub, rates = emptyList()))
    }

    private fun item(
        id: Long = 1,
        category: Category,
        amountMilli: Long,
        currency: Currency,
        quantityMilli: Long? = null,
    ) = BudgetItemDetails(
        item = BudgetItem(
            id = id,
            tripId = 1,
            categoryId = category.id,
            quantityMilli = quantityMilli,
            amountMilli = amountMilli,
            currencyCode = currency.code,
        ),
        category = category,
        currency = currency,
    )
}
