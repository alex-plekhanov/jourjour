package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.core.rates.quotesTo
import app.jourjour.core.stats.BaseTotals
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.TripRate

/**
 * One category read against itself: what was planned for it, what has gone on it, and what
 * that leaves.
 *
 * @param planned null where the category was never budgeted for — money went somewhere
 *   nobody had thought of, which is worth seeing rather than hiding.
 * @param spent what the entries of the category came to, net of anything that came back.
 *   A category that only ever brings money in — a refund, cashback — reads as a negative
 *   figure, which is exactly what it does to what the trip has cost so far.
 * @param remaining null where nothing was planned, and nil where the category is closed or
 *   has already had more spent on it than was planned for it.
 */
data class BudgetCategoryStanding(
    val category: Category,
    val planned: Money?,
    val spent: Money,
    val remaining: Money?,
    val isSpendingClosed: Boolean,
)

/**
 * The budget against the spending, both brought to the base currency.
 *
 * Only the base currency can answer the question: a line priced in dollars and a receipt
 * in baht are the same money to a person and nothing to arithmetic until a rate says
 * otherwise. So this is the one part of the budget that needs the trip's rates, and says
 * plainly when it has not got them.
 */
sealed interface BudgetVersusActual {

    data class Ready(
        val currency: Currency,
        val categories: List<BudgetCategoryStanding>,
        val planned: Money,
        val spent: Money,
        val remaining: Money,
    ) : BudgetVersusActual {

        /**
         * What the trip is heading for: what has gone plus what is still expected to.
         *
         * Not the same figure as the plan once anything has happened. Overspending has
         * already raised it and will not be given back; a category closed with money still
         * in it has lowered it, since nothing more is coming from there; and an expense
         * nobody planned for has raised it too.
         */
        val expected: Money get() = spent + remaining
    }

    /** These currencies have no rate to the base, so nothing can be read against anything. */
    data class RatesMissing(val codes: List<String>) : BudgetVersusActual

    /** Nothing says what to bring the two sides to. */
    data object NoBaseCurrency : BudgetVersusActual

    companion object {

        /**
         * @param closed the categories of this trip whose spending a person has declared
         *   finished: whatever was spent against what was planned, nothing is left.
         * @return null where there is nothing to read against anything — no budget and no
         *   spending at all.
         */
        fun of(
            items: List<BudgetItemDetails>,
            entries: List<EntryDetails>,
            base: Currency?,
            rates: List<TripRate>,
            closed: Set<Long>,
        ): BudgetVersusActual? {
            if (items.isEmpty() && entries.isEmpty()) return null
            if (base == null) return NoBaseCurrency

            val quotes = quotesTo(base, rates)
            val missing = sortedSetOf<String>()
            val planned = linkedMapOf<Long, Pair<Category, Money>>()
            items.forEach { details ->
                val quote = quotes[details.currency.code]
                if (quote == null) {
                    missing += details.currency.code
                    return@forEach
                }
                val running = planned[details.category.id]?.second ?: Money.ZERO
                planned[details.category.id] =
                    details.category to running + quote.applyTo(Money(details.item.amountMilli))
            }

            val actual = when (val converted = BaseTotals.convert(entries, base, rates)) {
                is BaseTotals.Converted.RatesMissing -> {
                    missing += converted.codes
                    emptyList()
                }
                is BaseTotals.Converted.Ready -> converted.categories
            }
            if (missing.isNotEmpty()) return RatesMissing(missing.toList())

            val spent = actual.associate { it.category.id to (it.expense - it.income) }
            val categories = (planned.values.map { it.first } + actual.map { it.category })
                .distinctBy { it.id }
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.id })
                .map { category ->
                    val plan = planned[category.id]?.second
                    val fact = spent[category.id] ?: Money.ZERO
                    BudgetCategoryStanding(
                        category = category,
                        planned = plan,
                        spent = fact,
                        // Closed says there will be no more of it, whatever the figures
                        // came to; otherwise what is left is what was planned and has not
                        // gone yet, and never less than nothing: money spent over a plan
                        // is not money owed back to it.
                        remaining = plan?.let {
                            when {
                                category.id in closed -> Money.ZERO
                                else -> (it - fact).takeIf { left -> !left.isNegative } ?: Money.ZERO
                            }
                        },
                        isSpendingClosed = category.id in closed,
                    )
                }

            return Ready(
                currency = base,
                categories = categories,
                planned = categories.fold(Money.ZERO) { sum, it -> sum + (it.planned ?: Money.ZERO) },
                // Every category, budgeted or not: what the trip has cost so far is what
                // it has cost, and an unplanned expense is part of that.
                spent = categories.fold(Money.ZERO) { sum, it -> sum + it.spent },
                remaining = categories.fold(Money.ZERO) { sum, it ->
                    sum + (it.remaining ?: Money.ZERO)
                },
            )
        }
    }
}
