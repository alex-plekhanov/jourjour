package app.jourjour.core.stats

import app.jourjour.core.money.Money
import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.countedQuantityMilli
import app.jourjour.data.entity.TripRate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The trip as one figure: everything it cost, brought to the base currency through the
 * rates the trip carries.
 *
 * It answers a different question from the per-currency blocks under it. Those say what
 * happened — so many baht here, so many lari there — and are exact. This one says what the
 * trip came to, and is only ever as good as the rates a person chose to reckon in. That is
 * why it stands above them rather than replacing them.
 */
sealed interface BaseTotals {

    /** The totals, in the same shape a currency block has, so they are read the same way. */
    data class Ready(val report: CurrencyReport) : BaseTotals

    /**
     * Nothing was converted: these currencies have no rate and no way round one. Listed by
     * code, in the order the currency blocks below are listed, so the reader can find them.
     */
    data class RatesMissing(val codes: List<String>) : BaseTotals

    /**
     * Nothing was converted because nothing says what to convert to.
     *
     * Only where converting would have been worth something — a trip that spent in more
     * than one currency. A trip spent in one currency is already a whole, and telling
     * somebody to go and choose a base currency for it would be asking them to fix a
     * problem they have not got.
     */
    data object NoBaseCurrency : BaseTotals

    companion object {

        /**
         * @param base the base currency from the settings, or null while none is chosen.
         * @return null where the section would say nothing — nothing to count, a trip
         * spent in the base currency alone, or a trip of one currency with no base
         * currency chosen, where the block below it already carries the same figures.
         */
        fun of(
            entries: List<EntryDetails>,
            base: Currency?,
            rates: List<TripRate>,
        ): BaseTotals? {
            // An exchange moves money between a person's own pockets. Counted here it would
            // add a trip's own float to what the trip cost, and counted on both halves it
            // would do so twice. The categories go with it: a column of zeroes is not
            // information.
            val counted = entries.filterNot { it.category.isExpense && it.category.isIncome }
            if (counted.isEmpty()) return null

            val spentIn = counted.flatMapTo(mutableSetOf()) { details ->
                listOfNotNull(details.expenseCurrency?.code, details.incomeCurrency?.code)
            }
            // With nothing to convert to, say so and leave it there. Even a trip of one
            // currency has something to gain — what it cost in the money a person reckons
            // in — and it is not for this to decide that somebody would rather not know.
            if (base == null) return NoBaseCurrency
            // One currency and it is the base: the block below is this block.
            if (spentIn == setOf(base.code)) return null

            val quotes = quotes(base, rates)
            val missing = sortedSetOf<String>()
            val running = linkedMapOf<Long, Running>()

            counted.forEach { details ->
                val entry = details.entry
                val row = running.getOrPut(details.category.id) { Running(details.category) }

                details.expenseCurrency?.let { currency ->
                    val converted = convert(
                        amount = Money(entry.expenseAmountMilli ?: 0),
                        currency = currency,
                        settlement = entry.expenseSettlementAmountMilli?.let(::Money),
                        settlementCurrency = details.expenseWallet?.currency,
                        quotes = quotes,
                    )
                    if (converted == null) missing += currency.code else row.expense += converted
                }
                details.incomeCurrency?.let { currency ->
                    val converted = convert(
                        amount = Money(entry.incomeAmountMilli ?: 0),
                        currency = currency,
                        settlement = entry.incomeSettlementAmountMilli?.let(::Money),
                        settlementCurrency = details.incomeWallet?.currency,
                        quotes = quotes,
                    )
                    if (converted == null) missing += currency.code else row.income += converted
                }

                // Once per entry, not once per half: a quantity belongs to what was bought.
                details.countedQuantityMilli?.let { row.addQuantity(Quantity(it)) }
            }

            if (missing.isNotEmpty()) return RatesMissing(missing.toList())

            val totals = running.values
                .map { it.toTotals() }
                .sortedWith(
                    compareBy<CategoryTotals> { it.category.sortOrder }.thenBy { it.category.id }
                )
            return Ready(
                CurrencyReport(
                    currency = base,
                    categories = totals,
                    income = totals.fold(Money.ZERO) { sum, it -> sum + it.income },
                    expense = totals.fold(Money.ZERO) { sum, it -> sum + it.expense },
                )
            )
        }

        /**
         * What one half of an entry comes to in the base currency, or null when neither
         * figure it offers can be converted.
         *
         * The settled amount goes first where there is one: it is what the account was
         * actually charged, the entered amount being what the price tag said. But a settled
         * amount is in the wallet's currency, and if that one has no rate while the entered
         * one does, the entered figure answers the question well enough — better, at any
         * rate, than refusing to answer it.
         */
        private fun convert(
            amount: Money,
            currency: Currency,
            settlement: Money?,
            settlementCurrency: Currency?,
            quotes: Map<String, Quote>,
        ): Money? {
            if (settlement != null && settlementCurrency != null) {
                quotes[settlementCurrency.code]?.let { return it.applyTo(settlement) }
            }
            return quotes[currency.code]?.applyTo(amount)
        }

        /**
         * The rates as a lookup, with the base currency in it at one to one: converting a
         * currency to itself is not something anybody should have to enter a rate for.
         *
         * Rates pointing at some other currency are left out. They were made against a base
         * that has since changed, and reading them as though they still pointed at this one
         * would put figures on the screen that nobody entered. The trip form is where that
         * is reported; here they simply do not exist.
         */
        private fun quotes(base: Currency, rates: List<TripRate>): Map<String, Quote> =
            rates.filter { it.toCurrencyCode == base.code }
                .associate { it.fromCurrencyCode to Quote(it.rateMilli, it.multiplier) }
                .plus(base.code to Quote(rateMilli = 1_000, multiplier = 1))
    }
}

/** A rate reduced to what converting needs: so many units are worth so much. */
private class Quote(private val rateMilli: Long, private val multiplier: Long) {

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
}

/** A total being added up; it becomes a [CategoryTotals] once every entry is in. */
private class Running(val category: Category) {
    var income: Money = Money.ZERO
    var expense: Money = Money.ZERO
    private var quantityMilli: Long? = null

    fun addQuantity(quantity: Quantity) {
        quantityMilli = (quantityMilli ?: 0) + quantity.milli
    }

    fun toTotals() = CategoryTotals(
        category = category,
        quantity = quantityMilli?.let(::Quantity),
        income = income,
        expense = expense,
    )
}
