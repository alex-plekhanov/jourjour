package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.core.rates.quotesTo
import app.jourjour.core.stats.BaseTotals
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.ScheduleEventDetails
import app.jourjour.data.entity.TripRate

/**
 * One category read against itself: what was planned for it, what has gone on it, and what
 * that leaves.
 *
 * @param planned null where nothing was planned for it at all — money went somewhere
 *   nobody had thought of, which is worth seeing rather than hiding.
 * @param spent what the entries of the category came to, net of anything that came back.
 *   A category that only ever brings money in — a refund, cashback — reads as a negative
 *   figure, which is exactly what it does to what the trip has cost so far.
 * @param remaining null where nothing was planned, and nil where the category is closed or
 *   has already had more spent on it than was planned for it.
 * @param fromSchedule whether [planned] is the schedule's figure rather than the budget's
 *   own — which it is once the planning of the category has been declared finished, and
 *   wherever the budget never had a line for it.
 */
data class BudgetCategoryStanding(
    val category: Category,
    val planned: Money?,
    val spent: Money,
    val remaining: Money?,
    val isSpendingClosed: Boolean,
    val fromSchedule: Boolean = false,
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
         * @param schedule what the trip's events expect to cost, which is the finer plan.
         * @param closed the categories of this trip whose spending a person has declared
         *   finished: whatever was spent against what was planned, nothing is left.
         * @param planningClosed the categories whose plan a person has declared complete,
         *   and which are therefore read off the schedule rather than off the budget.
         * @return null where there is nothing to read against anything — no budget, no
         *   schedule and no spending at all.
         */
        fun of(
            items: List<BudgetItemDetails>,
            entries: List<EntryDetails>,
            schedule: List<ScheduleEventDetails>,
            base: Currency?,
            rates: List<TripRate>,
            closed: Set<Long>,
            planningClosed: Set<Long>,
        ): BudgetVersusActual? {
            val scheduled = schedule.filter { it.category != null && it.event.amountMilli != null }
            if (items.isEmpty() && entries.isEmpty() && scheduled.isEmpty()) return null
            if (base == null) return NoBaseCurrency

            val quotes = quotesTo(base, rates)
            val missing = sortedSetOf<String>()
            val known = linkedMapOf<Long, Category>()

            val budgeted = linkedMapOf<Long, Money>()
            items.forEach { details ->
                val quote = quotes[details.currency.code]
                if (quote == null) {
                    missing += details.currency.code
                    return@forEach
                }
                known[details.category.id] = details.category
                budgeted[details.category.id] = (budgeted[details.category.id] ?: Money.ZERO) +
                    quote.applyTo(Money(details.item.amountMilli))
            }

            val booked = linkedMapOf<Long, Money>()
            scheduled.forEach { details ->
                val category = requireNotNull(details.category)
                val currency = details.currency ?: return@forEach
                val quote = quotes[currency.code]
                if (quote == null) {
                    missing += currency.code
                    return@forEach
                }
                known[category.id] = category
                booked[category.id] = (booked[category.id] ?: Money.ZERO) +
                    quote.applyTo(Money(requireNotNull(details.event.amountMilli)))
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
            val categories = (known.values + actual.map { it.category })
                .distinctBy { it.id }
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.id })
                .map { category ->
                    // Which of the two plans counts. The budget is the estimate and holds
                    // while the finer plan is still being made; once the planning is
                    // declared finished the schedule is the better answer and replaces it
                    // outright — a closed category with nothing scheduled plans nothing,
                    // which is a figure and not an absence. A category the budget never
                    // had a line for is read off the schedule whatever the flag says,
                    // there being nothing else to read.
                    val fromSchedule = category.id in planningClosed ||
                        category.id !in budgeted && category.id in booked
                    val plan = when {
                        fromSchedule -> booked[category.id] ?: Money.ZERO
                        else -> budgeted[category.id]
                    }
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
                        fromSchedule = fromSchedule && plan != null,
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
