package app.jourjour.core.rates

import app.jourjour.core.money.Money
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.TripRate
import java.math.BigDecimal
import java.math.RoundingMode

/** A rate reduced to what converting needs: so many units are worth so much. */
class Quote(private val rateMilli: Long, private val multiplier: Long) {

    /**
     * Thousandths times thousandths overflows a Long at around a billion units, which a
     * trip in dong or rupiah can reach, so the arithmetic goes through [BigDecimal] and
     * comes back rounded to the thousandth the ledger holds.
     */
    fun applyTo(amount: Money): Money {
        if (multiplier <= 0) return Money.ZERO
        val converted = BigDecimal.valueOf(amount.milli)
            .multiply(BigDecimal.valueOf(rateMilli))
            .divide(BigDecimal.valueOf(1_000L * multiplier), 0, RoundingMode.HALF_UP)
        return Money(converted.longValueExact())
    }

    /**
     * The same rate read the other way: what an amount of the base currency comes to in
     * this one. A rate of nothing converts nothing — it would divide by zero.
     */
    fun applyBack(amount: Money): Money {
        if (multiplier <= 0 || rateMilli <= 0) return Money.ZERO
        val converted = BigDecimal.valueOf(amount.milli)
            .multiply(BigDecimal.valueOf(1_000L * multiplier))
            .divide(BigDecimal.valueOf(rateMilli), 0, RoundingMode.HALF_UP)
        return Money(converted.longValueExact())
    }
}

/**
 * What an amount in one of a trip's currencies comes to in another, through the base
 * currency they both have a rate to — which is the only road between them the app has.
 *
 * @return null where either end has no rate, the base currency itself excepted: it is in
 *   [quotes] at one to one.
 */
fun convertBetween(
    amount: Money,
    from: String,
    to: String,
    quotes: Map<String, Quote>,
): Money? {
    val inBase = quotes[from]?.applyTo(amount) ?: return null
    return quotes[to]?.applyBack(inBase)
}

/**
 * The trip's rates as a lookup by the currency they convert from, with the base currency
 * in it at one to one: converting a currency to itself is not something anybody should
 * have to enter a rate for.
 *
 * Rates pointing at some other currency are left out. They were made against a base that
 * has since changed, and reading them as though they still pointed at this one would put
 * figures on the screen that nobody entered. The trip form is where that is reported; to
 * everything that converts, they simply do not exist.
 */
fun quotesTo(base: Currency, rates: List<TripRate>): Map<String, Quote> =
    rates.filter { it.toCurrencyCode == base.code }
        .associate { it.fromCurrencyCode to Quote(it.rateMilli, it.multiplier) }
        .plus(base.code to Quote(rateMilli = 1_000, multiplier = 1))
