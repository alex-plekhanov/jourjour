package app.jourjour.core.stats

import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.TripRate
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Bringing a trip to one currency. What is checked is which figure gets converted — the
 * price on the tag or the charge on the account — when a rate is genuinely missing as
 * opposed to merely unused, and the cases where the whole section has nothing to say.
 */
class BaseTotalsTest {

    private val rub = Currency("RUB", "Russian Ruble", "₽", decimals = 2)
    private val usd = Currency("USD", "US Dollar", "$", decimals = 2)
    private val thb = Currency("THB", "Thai Baht", "฿", decimals = 2)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", decimals = 0)

    private val cash = PaymentMethod(id = 1, name = "Cash", icon = "💵", fixedCurrency = true)
    private val card = PaymentMethod(id = 2, name = "Card", icon = "💳", fixedCurrency = false)

    private val dining = Category(id = 1, name = "Restaurants", icon = "🍽", sortOrder = 1)
    private val fuel = Category(id = 2, name = "Fuel", icon = "⛽", sortOrder = 2, trackQuantity = true)
    private val exchange = Category(
        id = 3,
        name = "Currency exchange",
        icon = "💱",
        sortOrder = 3,
        isExpense = true,
        isIncome = true,
    )

    /** 1 USD = 84.78 RUB. */
    private val usdRate = TripRate(
        id = 1,
        tripId = 1,
        fromCurrencyCode = "USD",
        toCurrencyCode = "RUB",
        rateMilli = 84_780,
        multiplier = 1,
    )

    /** 10 JPY = 5.354 RUB — the shape a rate takes when one unit buys almost nothing. */
    private val jpyRate = TripRate(
        id = 2,
        tripId = 1,
        fromCurrencyCode = "JPY",
        toCurrencyCode = "RUB",
        rateMilli = 5_354,
        multiplier = 10,
    )

    private val thbRate = TripRate(
        id = 3,
        tripId = 1,
        fromCurrencyCode = "THB",
        toCurrencyCode = "RUB",
        rateMilli = 2_630,
        multiplier = 1,
    )

    @Test
    fun `amounts are converted through the rate and its multiplier`() {
        val totals = BaseTotals.of(
            entries = listOf(
                entry(category = dining, expense = 100_000L to usd),
                entry(id = 2, category = dining, expense = 1_000_000L to jpy),
            ),
            base = rub,
            rates = listOf(usdRate, jpyRate),
        )

        val report = (totals as BaseTotals.Ready).report
        assertEquals(rub, report.currency)
        // 100 USD at 84.78, plus 1000 JPY at 5.354 per ten.
        assertEquals(8_478_000L + 535_400L, report.expense.milli)
        assertEquals(9_013_400L, report.netExpense?.milli)
    }

    @Test
    fun `the base currency needs no rate of its own`() {
        val totals = BaseTotals.of(
            entries = listOf(
                entry(category = dining, expense = 500_000L to rub),
                entry(id = 2, category = dining, expense = 100_000L to usd),
            ),
            base = rub,
            rates = listOf(usdRate),
        )

        assertEquals(500_000L + 8_478_000L, (totals as BaseTotals.Ready).report.expense.milli)
    }

    @Test
    fun `what the account was charged wins over what the price tag said`() {
        // 1000 baht on a rouble card, which the bank actually charged 2 700 roubles for.
        val totals = BaseTotals.of(
            entries = listOf(
                entry(
                    category = dining,
                    expense = 1_000_000L to thb,
                    wallet = wallet(rub, card),
                    expenseSettlementMilli = 2_700_000L,
                ),
            ),
            base = rub,
            rates = listOf(thbRate),
        )

        // Not the 2 630 the trip's own rate would have made of it.
        assertEquals(2_700_000L, (totals as BaseTotals.Ready).report.expense.milli)
    }

    @Test
    fun `a settled amount in a currency with no rate falls back to the entered one`() {
        // Charged in dollars, spent in baht, and only the baht has a rate. The entered
        // figure answers the question; the dollar is never needed and never complained of.
        val totals = BaseTotals.of(
            entries = listOf(
                entry(
                    category = dining,
                    expense = 1_000_000L to thb,
                    wallet = wallet(usd, card),
                    expenseSettlementMilli = 30_000L,
                ),
            ),
            base = rub,
            rates = listOf(thbRate),
        )

        assertEquals(2_630_000L, (totals as BaseTotals.Ready).report.expense.milli)
    }

    @Test
    fun `a currency with no rate anywhere stops the whole section`() {
        val totals = BaseTotals.of(
            entries = listOf(
                entry(category = dining, expense = 100_000L to usd),
                entry(id = 2, category = dining, expense = 1_000_000L to thb),
            ),
            base = rub,
            rates = listOf(usdRate),
        )

        assertEquals(listOf("THB"), (totals as BaseTotals.RatesMissing).codes)
    }

    @Test
    fun `every currency that blocks the total is named, in the order of the blocks below`() {
        val totals = BaseTotals.of(
            entries = listOf(
                entry(category = dining, expense = 100_000L to thb),
                entry(id = 2, category = dining, expense = 100_000L to usd),
                entry(id = 3, category = dining, expense = 100_000L to jpy),
            ),
            base = rub,
            rates = emptyList(),
        )

        assertEquals(listOf("JPY", "THB", "USD"), (totals as BaseTotals.RatesMissing).codes)
    }

    @Test
    fun `a rate pointing at some other base is not read as though it pointed at this one`() {
        val againstDollars = usdRate.copy(fromCurrencyCode = "THB", toCurrencyCode = "USD")

        val totals = BaseTotals.of(
            entries = listOf(entry(category = dining, expense = 100_000L to thb)),
            base = rub,
            rates = listOf(againstDollars),
        )

        assertEquals(listOf("THB"), (totals as BaseTotals.RatesMissing).codes)
    }

    @Test
    fun `an exchange is left out altogether, category and all`() {
        val totals = BaseTotals.of(
            entries = listOf(
                entry(category = dining, expense = 100_000L to usd),
                entry(
                    id = 2,
                    category = exchange,
                    expense = 1_000_000L to usd,
                    income = 84_780_000L to rub,
                ),
            ),
            base = rub,
            rates = listOf(usdRate),
        )

        val report = (totals as BaseTotals.Ready).report
        assertEquals(listOf(dining), report.categories.map { it.category })
        assertEquals(8_478_000L, report.expense.milli)
        assertEquals(0L, report.income.milli)
    }

    @Test
    fun `a quantity is counted once for the entry, not once per currency`() {
        val totals = BaseTotals.of(
            entries = listOf(
                entry(category = fuel, expense = 100_000L to usd, quantityMilli = 30_000L),
                entry(id = 2, category = fuel, expense = 100_000L to jpy, quantityMilli = 20_000L),
            ),
            base = rub,
            rates = listOf(usdRate, jpyRate),
        )

        val report = (totals as BaseTotals.Ready).report
        assertEquals(50_000L, report.categories.single().quantity?.milli)
    }

    @Test
    fun `no base currency says so, rather than saying nothing`() {
        // Even a trip of one currency has something to gain from a base currency — what it
        // cost in the money the person reckons in — so both shapes get the line.
        val several = BaseTotals.of(
            entries = listOf(
                entry(category = dining, expense = 100_000L to usd),
                entry(id = 2, category = dining, expense = 300_000L to thb),
            ),
            base = null,
            rates = emptyList(),
        )
        assertEquals(BaseTotals.NoBaseCurrency, several)

        val one = BaseTotals.of(
            entries = listOf(entry(category = dining, expense = 100_000L to usd)),
            base = null,
            rates = emptyList(),
        )
        assertEquals(BaseTotals.NoBaseCurrency, one)
    }

    @Test
    fun `one currency that is not the base is still worth converting`() {
        val totals = BaseTotals.of(
            entries = listOf(entry(category = dining, expense = 100_000L to usd)),
            base = rub,
            rates = listOf(usdRate),
        )

        assertEquals(8_478_000L, (totals as BaseTotals.Ready).report.expense.milli)
    }

    @Test
    fun `a trip spent in the base currency alone says nothing the block below does not`() {
        assertNull(
            BaseTotals.of(
                entries = listOf(
                    entry(category = dining, expense = 100_000L to rub),
                    entry(id = 2, category = fuel, expense = 300_000L to rub),
                ),
                base = rub,
                rates = emptyList(),
            )
        )
    }

    @Test
    fun `a trip of nothing but exchanges has nothing to total`() {
        assertNull(
            BaseTotals.of(
                entries = listOf(
                    entry(
                        category = exchange,
                        expense = 1_000_000L to usd,
                        income = 84_780_000L to rub,
                    ),
                ),
                base = rub,
                rates = listOf(usdRate),
            )
        )
    }

    @Test
    fun `an amount too large for a Long to multiply is still converted`() {
        // A billion dong, which is a real sum of money and a real overflow.
        val dong = Currency("VND", "Vietnamese Dong", "₫", decimals = 0)
        val dongRate = TripRate(
            id = 4,
            tripId = 1,
            fromCurrencyCode = "VND",
            toCurrencyCode = "RUB",
            rateMilli = 3_220,
            multiplier = 1_000,
        )

        val totals = BaseTotals.of(
            entries = listOf(entry(category = dining, expense = 1_000_000_000_000L to dong)),
            base = rub,
            rates = listOf(dongRate),
        )

        // A thousand million dong at 3.22 roubles the thousand.
        assertTrue(totals is BaseTotals.Ready)
        assertEquals(3_220_000_000L, (totals as BaseTotals.Ready).report.expense.milli)
    }

    private fun wallet(currency: Currency, method: PaymentMethod = cash) = TripWalletDetails(
        wallet = TripWallet(
            id = 1,
            tripId = 1,
            paymentMethodId = method.id,
            currencyCode = currency.code,
        ),
        paymentMethod = method,
        currency = currency,
    )

    private fun entry(
        id: Long = 1,
        category: Category,
        expense: Pair<Long, Currency>? = null,
        income: Pair<Long, Currency>? = null,
        quantityMilli: Long? = null,
        wallet: TripWalletDetails = wallet(usd),
        expenseSettlementMilli: Long? = null,
        incomeSettlementMilli: Long? = null,
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
            expenseSettlementAmountMilli = expenseSettlementMilli,
            incomeWalletId = wallet.wallet.id.takeIf { income != null },
            incomeAmountMilli = income?.first,
            incomeCurrencyCode = income?.second?.code,
            incomeSettlementAmountMilli = incomeSettlementMilli,
        ),
        category = category,
        expenseWallet = wallet.takeIf { expense != null },
        incomeWallet = wallet.takeIf { income != null },
        expenseCurrency = expense?.second,
        incomeCurrency = income?.second,
    )
}
