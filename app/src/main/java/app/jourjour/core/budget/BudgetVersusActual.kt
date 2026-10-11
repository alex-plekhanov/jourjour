package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.core.rates.quotesTo
import app.jourjour.core.stats.BaseTotals
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
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
    /**
     * The same three figures again, split by the way of paying — what is still to be found
     * in cash, what is still to go on the card.
     *
     * Empty for a closed category: nothing more is coming from it, so there is nothing
     * left to arrange and no question for this to answer.
     */
    val byMethod: List<PaymentMethodStanding> = emptyList(),
)

/**
 * One way of paying, read the same three ways: what was planned to go through it, what has
 * gone through it, and what that leaves to arrange.
 *
 * @param paymentMethod null for the money nobody has said how to pay — a bucket rather
 *   than a method, and the first one the overspending of the others is taken out of.
 */
data class PaymentMethodStanding(
    val paymentMethod: PaymentMethod?,
    val planned: Money,
    val spent: Money,
    val remaining: Money,
) {
    /** A row of noughts says nothing anybody needs to read. */
    val isEmpty: Boolean get() = planned.isZero && spent.isZero && remaining.isZero
}

/**
 * What is left under each way of paying, once the overspending of one has been taken out of
 * the others.
 *
 * Paying four hundred on a card that two hundred was planned for does not leave the plan
 * owing two hundred: the money came out of the trip, and what it really means is that two
 * hundred of what was planned elsewhere has gone. It comes out of the unsaid bucket first,
 * that being the money nobody has committed to a method, and then out of the rest in their
 * own order. What a category has left over all is untouched by this — only how it is
 * expected to be paid changes.
 *
 * A plan that names no method at all is not split. The question being asked is how much is
 * still to be found in cash and how much on the card, and the whole of such a remainder is
 * unallocated whatever the receipts say: splitting the fact under it would answer a
 * question nobody asked and print three rows where the answer is one figure.
 */
private fun methodStandings(
    plan: Map<Long?, Money>,
    fact: Map<Long?, Money>,
    methods: Map<Long, PaymentMethod>,
): List<PaymentMethodStanding> {
    if (plan.keys.none { it != null }) return listOf(unsplit(plan, fact))

    val ids = (plan.keys + fact.keys).sortedWith(
        // The unsaid bucket first, then the dictionary's own order.
        compareBy<Long?> { it != null }
            .thenBy { it?.let { id -> methods[id]?.sortOrder } ?: 0 }
            .thenBy { it ?: 0L }
    )
    val planned = ids.associateWith { plan[it] ?: Money.ZERO }
    val spent = ids.associateWith { fact[it] ?: Money.ZERO }
    val remaining = linkedMapOf<Long?, Money>()
    var excess = Money.ZERO
    ids.forEach { id ->
        val left = planned.getValue(id) - spent.getValue(id)
        if (left.isNegative) {
            remaining[id] = Money.ZERO
            excess -= left
        } else {
            remaining[id] = left
        }
    }

    // In the order the ids already stand in, which is the unsaid bucket and then the rest.
    for (id in ids) {
        if (excess.isZero) break
        val taken = minOf(remaining.getValue(id), excess)
        remaining[id] = remaining.getValue(id) - taken
        excess -= taken
    }

    return ids.map { id ->
        PaymentMethodStanding(
            paymentMethod = id?.let(methods::get),
            planned = planned.getValue(id),
            spent = spent.getValue(id),
            remaining = remaining.getValue(id),
        )
    }
}

/**
 * A category whose plan says nothing about paying, as one row in the unsaid bucket.
 *
 * The same three figures the table above gives it, which is the point: there is nothing
 * here to break down, and the row is printed so that the category still carries its share
 * of the totals underneath.
 */
private fun unsplit(plan: Map<Long?, Money>, fact: Map<Long?, Money>): PaymentMethodStanding {
    val planned = plan.values.fold(Money.ZERO) { sum, it -> sum + it }
    val spent = fact.values.fold(Money.ZERO) { sum, it -> sum + it }
    val left = planned - spent
    return PaymentMethodStanding(
        paymentMethod = null,
        planned = planned,
        spent = spent,
        // Never less than nothing, as everywhere: money spent over a plan is not money
        // owed back to it.
        remaining = if (left.isNegative) Money.ZERO else left,
    )
}

/** The same rows added up over the categories, in the order they came. */
private fun totalByMethod(
    categories: List<BudgetCategoryStanding>,
): List<PaymentMethodStanding> {
    val running = linkedMapOf<Long?, PaymentMethodStanding>()
    categories.flatMap { it.byMethod }.forEach { row ->
        val key = row.paymentMethod?.id
        val sum = running[key]
        running[key] = PaymentMethodStanding(
            paymentMethod = row.paymentMethod,
            planned = (sum?.planned ?: Money.ZERO) + row.planned,
            spent = (sum?.spent ?: Money.ZERO) + row.spent,
            remaining = (sum?.remaining ?: Money.ZERO) + row.remaining,
        )
    }
    return running.values.toList()
}

/** What one category plans or has spent, by the way of paying; the unsaid bucket is null. */
private fun MutableMap<Long, MutableMap<Long?, Money>>.add(
    categoryId: Long,
    methodId: Long?,
    amount: Money,
) {
    val ofCategory = getOrPut(categoryId) { linkedMapOf() }
    ofCategory[methodId] = (ofCategory[methodId] ?: Money.ZERO) + amount
}

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
        /** What is left under each way of paying, over every category still spending. */
        val byMethod: List<PaymentMethodStanding> = emptyList(),
        /**
         * Whether anything planned says how it is to be paid for.
         *
         * Where nothing does, the whole of the breakdown is one unsaid bucket repeating
         * the figures above it, and there is nothing to be gained by printing it.
         */
        val plansSayHow: Boolean = false,
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

            // The ways of paying anybody has named, kept by id so that a row of the
            // breakdown can be given back the method it belongs to.
            val methods = linkedMapOf<Long, PaymentMethod>()
            val budgeted = linkedMapOf<Long, Money>()
            val budgetedByMethod = linkedMapOf<Long, MutableMap<Long?, Money>>()
            items.forEach { details ->
                val quote = quotes[details.currency.code]
                if (quote == null) {
                    missing += details.currency.code
                    return@forEach
                }
                known[details.category.id] = details.category
                details.paymentMethod?.let { methods[it.id] = it }
                val amount = quote.applyTo(Money(details.item.amountMilli))
                budgeted[details.category.id] = (budgeted[details.category.id] ?: Money.ZERO) +
                    amount
                budgetedByMethod.add(details.category.id, details.item.paymentMethodId, amount)
            }

            val booked = linkedMapOf<Long, Money>()
            val bookedByMethod = linkedMapOf<Long, MutableMap<Long?, Money>>()
            scheduled.forEach { details ->
                val category = requireNotNull(details.category)
                val currency = details.currency ?: return@forEach
                val quote = quotes[currency.code]
                if (quote == null) {
                    missing += currency.code
                    return@forEach
                }
                known[category.id] = category
                details.paymentMethod?.let { methods[it.id] = it }
                val amount = quote.applyTo(Money(requireNotNull(details.event.amountMilli)))
                booked[category.id] = (booked[category.id] ?: Money.ZERO) + amount
                bookedByMethod.add(category.id, details.event.paymentMethodId, amount)
            }

            val actual = when (val converted = BaseTotals.convert(entries, base, rates)) {
                is BaseTotals.Converted.RatesMissing -> {
                    missing += converted.codes
                    emptyList()
                }
                is BaseTotals.Converted.Ready -> converted.categories
            }
            if (missing.isNotEmpty()) return RatesMissing(missing.toList())

            // What has gone, by the way it went: the wallet of each half says which, and
            // the rates have already been shown to be there for all of it.
            val actualByMethod = linkedMapOf<Long, MutableMap<Long?, Money>>()
            BaseTotals.convertByPaymentMethod(entries, base, rates).forEach { row ->
                row.paymentMethod?.let { methods[it.id] = it }
                actualByMethod.add(row.categoryId, row.paymentMethod?.id, row.net)
            }

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
                        // A closed category is settled; see PaymentMethodStanding.
                        byMethod = when (category.id) {
                            in closed -> emptyList()
                            else -> methodStandings(
                                plan = when {
                                    fromSchedule -> bookedByMethod[category.id]
                                    else -> budgetedByMethod[category.id]
                                }.orEmpty(),
                                fact = actualByMethod[category.id].orEmpty(),
                                methods = methods,
                            )
                        },
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
                byMethod = totalByMethod(categories),
                plansSayHow = items.any { it.item.paymentMethodId != null } ||
                    scheduled.any { it.event.paymentMethodId != null },
            )
        }
    }
}
