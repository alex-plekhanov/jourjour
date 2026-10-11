package app.jourjour.core.stats

import app.jourjour.core.money.Money
import app.jourjour.core.money.Quantity
import app.jourjour.core.rates.Quote
import app.jourjour.core.rates.quotesTo
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.countedQuantityMilli
import app.jourjour.data.entity.TripRate

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

    /** Entries brought to the base currency, or the currencies that stopped them. */
    sealed interface Converted {
        data class Ready(val categories: List<CategoryTotals>) : Converted
        data class RatesMissing(val codes: List<String>) : Converted
    }

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

            return when (val converted = convert(entries, base, rates)) {
                is Converted.RatesMissing -> RatesMissing(converted.codes)
                is Converted.Ready -> Ready(
                    CurrencyReport(
                        currency = base,
                        categories = converted.categories,
                        income = converted.categories.fold(Money.ZERO) { sum, it -> sum + it.income },
                        expense = converted.categories.fold(Money.ZERO) { sum, it -> sum + it.expense },
                    )
                )
            }
        }

        /**
         * Every category's figures brought to the base currency, whether or not there is
         * anything to be gained by saying so.
         *
         * Apart from [of] because the budget reads the same figures and asks a different
         * question of them: what was actually spent under a category, against what was
         * planned for it — which is worth knowing even for a trip spent in the base
         * currency alone, where there is no conversion to show anybody.
         */
        fun convert(
            entries: List<EntryDetails>,
            base: Currency,
            rates: List<TripRate>,
        ): Converted {
            // An exchange moves money between a person's own pockets. Counted here it would
            // add a trip's own float to what the trip cost, and counted on both halves it
            // would do so twice. The categories go with it: a column of zeroes is not
            // information.
            val counted = entries.filterNot { it.category.isExpense && it.category.isIncome }
            val quotes = quotesTo(base, rates)
            val missing = sortedSetOf<String>()
            val running = linkedMapOf<Long, Running>()

            counted.forEach { details ->
                val entry = details.entry
                val row = running.getOrPut(details.category.id) { Running(details.category) }

                details.expenseCurrency?.let { currency ->
                    val converted = convertHalf(
                        amount = Money(entry.expenseAmountMilli ?: 0),
                        currency = currency,
                        settlement = entry.expenseSettlementAmountMilli?.let(::Money),
                        settlementCurrency = details.expenseWallet?.currency,
                        quotes = quotes,
                    )
                    if (converted == null) missing += currency.code else row.expense += converted
                }
                details.incomeCurrency?.let { currency ->
                    val converted = convertHalf(
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

            if (missing.isNotEmpty()) return Converted.RatesMissing(missing.toList())

            return Converted.Ready(
                running.values
                    .map { it.toTotals() }
                    .sortedWith(
                        compareBy<CategoryTotals> { it.category.sortOrder }.thenBy { it.category.id }
                    )
            )
        }

        /**
         * What each category cost in the base currency, split by the way the money went.
         *
         * The way of paying is the wallet's own: an entry does not choose one, it names a
         * wallet, and a wallet is a method and a currency. The two halves are kept apart
         * rather than netted, as [convert] keeps them: money that came back through the
         * card is a deposit returned as often as it is a card that spent less.
         *
         * A half with no rate is left out rather than reported: this is read beside a
         * block that has already refused to say anything where a rate is missing, and
         * never reached otherwise. Exchanges are left out whole, as everywhere.
         */
        fun convertByPaymentMethod(
            entries: List<EntryDetails>,
            base: Currency,
            rates: List<TripRate>,
        ): List<MethodTotals> {
            val counted = entries.filterNot { it.category.isExpense && it.category.isIncome }
            val quotes = quotesTo(base, rates)
            val running = linkedMapOf<Pair<Long, Long?>, MethodTotals>()

            counted.forEach { details ->
                val entry = details.entry
                details.expenseCurrency?.let { currency ->
                    val converted = convertHalf(
                        amount = Money(entry.expenseAmountMilli ?: 0),
                        currency = currency,
                        settlement = entry.expenseSettlementAmountMilli?.let(::Money),
                        settlementCurrency = details.expenseWallet?.currency,
                        quotes = quotes,
                    ) ?: return@let
                    running.add(
                        categoryId = details.category.id,
                        method = details.expenseWallet?.paymentMethod,
                        expense = converted,
                    )
                }
                details.incomeCurrency?.let { currency ->
                    val converted = convertHalf(
                        amount = Money(entry.incomeAmountMilli ?: 0),
                        currency = currency,
                        settlement = entry.incomeSettlementAmountMilli?.let(::Money),
                        settlementCurrency = details.incomeWallet?.currency,
                        quotes = quotes,
                    ) ?: return@let
                    running.add(
                        categoryId = details.category.id,
                        method = details.incomeWallet?.paymentMethod,
                        income = converted,
                    )
                }
            }
            return running.values.toList()
        }

        private fun MutableMap<Pair<Long, Long?>, MethodTotals>.add(
            categoryId: Long,
            method: PaymentMethod?,
            expense: Money = Money.ZERO,
            income: Money = Money.ZERO,
        ) {
            val key = categoryId to method?.id
            val running = this[key]
            this[key] = MethodTotals(
                categoryId = categoryId,
                paymentMethod = method,
                expense = (running?.expense ?: Money.ZERO) + expense,
                income = (running?.income ?: Money.ZERO) + income,
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
        private fun convertHalf(
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
    }
}

/**
 * What one category cost through one way of paying, in the base currency, and what came
 * back through it.
 *
 * The two kept apart: what came back is as often a deposit returned as it is a cost
 * undone, and which it is cannot be told by netting them.
 *
 * Null method where the half named no wallet, which reads as "not set" — the same bucket
 * a plan that said nothing lands in.
 */
data class MethodTotals(
    val categoryId: Long,
    val paymentMethod: PaymentMethod?,
    val expense: Money,
    val income: Money,
) {
    /** What the category cost through this method, net of what came back through it. */
    val net: Money get() = expense - income
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
