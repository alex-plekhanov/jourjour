package app.jourjour.core.rates

import app.jourjour.core.money.Money
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.TripRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Converting one of a trip's currencies into another, which only ever happens through the
 * base currency: the rates all point at it, and there is no road between two of them that
 * does not pass through it.
 */
class QuoteTest {

    private val rub = Currency("RUB", "Russian Ruble", "₽", decimals = 2)

    /** 1 USD = 80 RUB. */
    private val usdRate = rate(from = "USD", rateMilli = 80_000)

    /** 10 THB = 25 RUB, the shape a rate takes when one unit buys little. */
    private val thbRate = rate(from = "THB", rateMilli = 25_000, multiplier = 10)

    private val quotes = quotesTo(rub, listOf(usdRate, thbRate))

    @Test
    fun `a currency converts into the base one`() {
        assertEquals(8_000_000L, convert(100_000, from = "USD", to = "RUB"))
    }

    @Test
    fun `the base currency converts back out of itself`() {
        assertEquals(100_000L, convert(8_000_000, from = "RUB", to = "USD"))
    }

    @Test
    fun `two currencies meet through the base one`() {
        // A hundred dollars is eight thousand roubles, and eight thousand roubles buys
        // 3 200 baht at two and a half roubles apiece.
        assertEquals(3_200_000L, convert(100_000, from = "USD", to = "THB"))
    }

    @Test
    fun `a multiplier is read the same way in both directions`() {
        assertEquals(250_000L, convert(100_000, from = "THB", to = "RUB"))
        assertEquals(100_000L, convert(250_000, from = "RUB", to = "THB"))
    }

    @Test
    fun `the base currency converts into itself unchanged`() {
        assertEquals(123_456L, convert(123_456, from = "RUB", to = "RUB"))
    }

    @Test
    fun `a currency with no rate converts into nothing, either way round`() {
        assertNull(convertBetween(Money(1_000), "JPY", "RUB", quotes))
        assertNull(convertBetween(Money(1_000), "RUB", "JPY", quotes))
    }

    private fun convert(milli: Long, from: String, to: String): Long? =
        convertBetween(Money(milli), from, to, quotes)?.milli

    private fun rate(from: String, rateMilli: Long, multiplier: Long = 1) = TripRate(
        tripId = 1,
        fromCurrencyCode = from,
        toCurrencyCode = rub.code,
        rateMilli = rateMilli,
        multiplier = multiplier,
    )
}
