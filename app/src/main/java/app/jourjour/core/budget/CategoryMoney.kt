package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.ScheduleEventDetails

/**
 * So much of one currency, paid one way.
 *
 * The way of paying is part of what makes a figure a figure here: five hundred on the card
 * and five hundred in cash are two things a person has to arrange, not one. Null where
 * nobody has said — which is most lines of most budgets, and reads as "not set" rather
 * than as a method of its own.
 */
data class CurrencyAmount(
    val currency: Currency,
    val amount: Money,
    val paymentMethod: PaymentMethod? = null,
)

/**
 * What a category's budget comes to, currency by currency and method by method, for every
 * category that has one.
 *
 * Kept apart rather than converted: this is what a person wrote down, and it is worth
 * seeing in the money they wrote it in. Bringing the currencies together is the business
 * of the block that reads the budget against the spending, and needs rates to do it.
 *
 * Sums algebraically, which is what lets a refund stand in the plan: ten nights at fifty
 * and a deposit of a hundred coming back is four hundred to arrange. What the deposit
 * itself comes to is a separate question, see [plannedDeposits].
 */
fun plannedByCurrency(items: List<BudgetItemDetails>): Map<Long, List<CurrencyAmount>> =
    items.lines().byCategory()

/**
 * What has to be put down as a deposit, currency by currency and method by method.
 *
 * A negative line of the plan is money coming back, and money only comes back where it
 * went first: the refund is the deposit. Positive here however it was written, because a
 * deposit is a thing a person has to have on them, and read beside the net figure rather
 * than inside it — the net says what the category will cost, this says what has to be
 * carried to pay it.
 */
fun plannedDeposits(items: List<BudgetItemDetails>): Map<Long, List<CurrencyAmount>> =
    items.lines().deposits().byCategory()

/**
 * What the schedule expects each category to cost, currency by currency and method by
 * method.
 *
 * The finer plan: a budget line says ten nights at fifty, and the schedule says which
 * nights and what each of them actually came to when it was booked. Read the same way as
 * the budget's own figures and shown beside them — the two are different answers to the
 * same question, and which one counts is settled by whether the planning is closed.
 */
fun scheduledByCurrency(events: List<ScheduleEventDetails>): Map<Long, List<CurrencyAmount>> =
    events.plans().byCategory()

/** The deposits the schedule carries, as [plannedDeposits] reads the budget's own. */
fun scheduledDeposits(events: List<ScheduleEventDetails>): Map<Long, List<CurrencyAmount>> =
    events.plans().deposits().byCategory()

/**
 * What has actually gone on each category, currency by currency and method by method, net
 * of anything that came back the same way.
 *
 * The method is the wallet's, not something anybody chose twice: money that went out of
 * the card wallet went out on the card, whatever the plan had said.
 *
 * Exchanges are left out whole, as they are everywhere: money moved between a person's own
 * pockets is not money a trip has cost. A currency that nets out to nothing is left out
 * too — a nought says less than the absence of a figure.
 */
fun spentByCurrency(entries: List<EntryDetails>): Map<Long, List<CurrencyAmount>> =
    entries
        .filterNot { it.category.isExpense && it.category.isIncome }
        .groupBy { it.category.id }
        .mapValues { (_, ofCategory) ->
            val running = linkedMapOf<Key, CurrencyAmount>()
            ofCategory.forEach { details ->
                details.expenseCurrency?.let { currency ->
                    running.add(
                        currency = currency,
                        method = details.expenseWallet?.paymentMethod,
                        amount = Money(details.entry.expenseAmountMilli ?: 0),
                    )
                }
                details.incomeCurrency?.let { currency ->
                    running.add(
                        currency = currency,
                        method = details.incomeWallet?.paymentMethod,
                        amount = -Money(details.entry.incomeAmountMilli ?: 0),
                    )
                }
            }
            running.values.filterNot { it.amount.isZero }.sorted()
        }
        .filterValues { it.isNotEmpty() }

/**
 * One planned cost, with everything a figure needs to be read: the budget's own lines and
 * the schedule's both come to this before they are added up.
 */
private data class PlanLine(
    val categoryId: Long,
    val currency: Currency,
    val paymentMethod: PaymentMethod?,
    val amount: Money,
)

private fun List<BudgetItemDetails>.lines(): List<PlanLine> = map { details ->
    PlanLine(
        categoryId = details.category.id,
        currency = details.currency,
        paymentMethod = details.paymentMethod,
        amount = Money(details.item.amountMilli),
    )
}

/** The events that say what they will cost and what they are for; the rest plan nothing. */
private fun List<ScheduleEventDetails>.plans(): List<PlanLine> = mapNotNull { details ->
    val category = details.category ?: return@mapNotNull null
    val currency = details.currency ?: return@mapNotNull null
    val amount = details.event.amountMilli ?: return@mapNotNull null
    PlanLine(category.id, currency, details.paymentMethod, Money(amount))
}

/** The lines that are money coming back, as the money that has to go out first. */
private fun List<PlanLine>.deposits(): List<PlanLine> =
    filter { it.amount.isNegative }.map { it.copy(amount = -it.amount) }

private fun List<PlanLine>.byCategory(): Map<Long, List<CurrencyAmount>> =
    groupBy { it.categoryId }
        .mapValues { (_, lines) ->
            val running = linkedMapOf<Key, CurrencyAmount>()
            lines.forEach { running.add(it.currency, it.paymentMethod, it.amount) }
            running.values.toList().sorted()
        }

/** What makes one figure of the list: the money it is in and the way it is to be paid. */
private typealias Key = Pair<String, Long?>

private fun MutableMap<Key, CurrencyAmount>.add(
    currency: Currency,
    method: PaymentMethod?,
    amount: Money,
) {
    val key = currency.code to method?.id
    val running = this[key]?.amount ?: Money.ZERO
    this[key] = CurrencyAmount(currency, running + amount, method)
}

/**
 * The order of the dictionaries, which is the order every other list uses, and inside one
 * currency the methods in their own order with the unsaid one first — it is the figure a
 * person has not got round to, and it reads first for the same reason a blank does.
 */
private fun List<CurrencyAmount>.sorted(): List<CurrencyAmount> = sortedWith(
    compareBy<CurrencyAmount> { it.currency.sortOrder }
        .thenBy { it.currency.code }
        .thenBy { it.paymentMethod != null }
        .thenBy { it.paymentMethod?.sortOrder ?: 0 }
        .thenBy { it.paymentMethod?.id ?: 0 }
)
