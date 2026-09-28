package app.jourjour.core.budget

import app.jourjour.core.money.Money
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails

/** So much of one currency. */
data class CurrencyAmount(val currency: Currency, val amount: Money)

/**
 * What a category's budget comes to, currency by currency, for every category that has
 * one.
 *
 * Kept apart rather than converted: this is what a person wrote down, and it is worth
 * seeing in the money they wrote it in. Bringing the currencies together is the business
 * of the block that reads the budget against the spending, and needs rates to do it.
 */
fun plannedByCurrency(items: List<BudgetItemDetails>): Map<Long, List<CurrencyAmount>> =
    items
        .groupBy { it.category.id }
        .mapValues { (_, lines) ->
            lines
                .groupBy { it.currency.code }
                .map { (_, ofCurrency) ->
                    CurrencyAmount(
                        currency = ofCurrency.first().currency,
                        amount = ofCurrency.fold(Money.ZERO) { sum, it ->
                            sum + Money(it.item.amountMilli)
                        },
                    )
                }
                .sorted()
        }

/**
 * What has actually gone on each category, currency by currency, net of anything that came
 * back under the same category.
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
            val running = linkedMapOf<String, CurrencyAmount>()
            ofCategory.forEach { details ->
                details.expenseCurrency?.let { currency ->
                    running.add(currency, Money(details.entry.expenseAmountMilli ?: 0))
                }
                details.incomeCurrency?.let { currency ->
                    running.add(currency, -Money(details.entry.incomeAmountMilli ?: 0))
                }
            }
            running.values.filterNot { it.amount.isZero }.sorted()
        }
        .filterValues { it.isNotEmpty() }

private fun MutableMap<String, CurrencyAmount>.add(currency: Currency, amount: Money) {
    val running = this[currency.code]?.amount ?: Money.ZERO
    this[currency.code] = CurrencyAmount(currency, running + amount)
}

/** The order of the currency dictionary, which is the order every other list uses. */
private fun List<CurrencyAmount>.sorted(): List<CurrencyAmount> = sortedWith(
    compareBy<CurrencyAmount> { it.currency.sortOrder }.thenBy { it.currency.code }
)
