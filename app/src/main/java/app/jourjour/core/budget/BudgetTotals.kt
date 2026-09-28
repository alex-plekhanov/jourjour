package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.core.rates.quotesTo
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.TripRate

/** What the budget comes to in one currency. */
data class BudgetCurrencyTotal(val currency: Currency, val amount: Money)

/**
 * What a trip is expected to cost, added up.
 *
 * Currencies are kept apart, as they are in the statistics: a line priced in roubles and a
 * line priced in dollars are two answers until somebody supplies a rate. The one figure
 * that puts them together comes from the trip's own rates and the base currency, which is
 * what those rates were entered for.
 */
sealed interface BudgetTotals {

    /** Every currency the budget names, in the order of the currency dictionary. */
    val byCurrency: List<BudgetCurrencyTotal>

    /** One figure, because everything the budget names can be brought to the base. */
    data class Ready(
        override val byCurrency: List<BudgetCurrencyTotal>,
        val total: BudgetCurrencyTotal,
    ) : BudgetTotals

    /** These currencies have no rate to the base, so there is no one figure. */
    data class RatesMissing(
        override val byCurrency: List<BudgetCurrencyTotal>,
        val codes: List<String>,
    ) : BudgetTotals

    /** Nothing says what to convert to. */
    data class NoBaseCurrency(override val byCurrency: List<BudgetCurrencyTotal>) : BudgetTotals

    companion object {

        /**
         * @param base the base currency from the settings, or null while none is chosen.
         * @return null where there is nothing to add up at all.
         */
        fun of(
            items: List<BudgetItemDetails>,
            base: Currency?,
            rates: List<TripRate>,
        ): BudgetTotals? {
            if (items.isEmpty()) return null

            val sums = linkedMapOf<String, BudgetCurrencyTotal>()
            items.forEach { details ->
                val currency = details.currency
                val running = sums[currency.code]?.amount ?: Money.ZERO
                sums[currency.code] =
                    BudgetCurrencyTotal(currency, running + Money(details.item.amountMilli))
            }
            val byCurrency = sums.values.sortedWith(
                compareBy<BudgetCurrencyTotal> { it.currency.sortOrder }.thenBy { it.currency.code }
            )

            if (base == null) return NoBaseCurrency(byCurrency)
            // One currency and it is the base: the figure is already on the screen.
            if (byCurrency.map { it.currency.code } == listOf(base.code)) {
                return Ready(byCurrency, byCurrency.single())
            }

            val quotes = quotesTo(base, rates)
            val missing = byCurrency.map { it.currency.code }.filterNot { it in quotes }
            if (missing.isNotEmpty()) return RatesMissing(byCurrency, missing.sorted())

            val total = byCurrency.fold(Money.ZERO) { sum, line ->
                sum + quotes.getValue(line.currency.code).applyTo(line.amount)
            }
            return Ready(byCurrency, BudgetCurrencyTotal(base, total))
        }
    }
}
