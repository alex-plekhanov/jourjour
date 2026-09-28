package app.jourjour.core.stats

import app.jourjour.core.money.Money
import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.countedQuantityMilli

/** What one category came to in one currency. */
data class CategoryTotals(
    val category: Category,
    /** Null when nothing in this category counts anything: most of them do not. */
    val quantity: Quantity?,
    val income: Money,
    val expense: Money,
)

/** Everything spent and received in one currency, broken down by category. */
data class CurrencyReport(
    val currency: Currency,
    val categories: List<CategoryTotals>,
    val income: Money,
    val expense: Money,
) {
    /**
     * What the currency came to, on the one side it came out on.
     *
     * Spending a thousand and receiving a hundred is being nine hundred down, not both at
     * once: the foot of the block nets the two and leaves the other column empty, so the
     * figure to read is the only one there.
     */
    val netExpense: Money? get() = (expense - income).takeIf { it.milli >= 0 }

    val netIncome: Money? get() = (income - expense).takeIf { it.milli > 0 }
}

/**
 * What a trip came to.
 *
 * Currencies are kept apart from first to last: yen cannot be added to dollars without a
 * rate, and the app holds no rates. An entry lands in the group of whichever currency it
 * names, and a currency exchange — dollars out, roubles in — therefore shows up in two
 * groups, as an expense in one and an income in the other. That is what happened.
 */
object TripStatistics {

    fun of(entries: List<EntryDetails>): List<CurrencyReport> {
        val byCurrency = linkedMapOf<String, MutableMap<Long, Running>>()
        val currencies = linkedMapOf<String, Currency>()

        entries.forEach { details ->
            val entry = details.entry

            details.expenseCurrency?.let { currency ->
                currencies[currency.code] = currency
                byCurrency.running(currency.code, details.category).apply {
                    expense += Money(entry.expenseAmountMilli ?: 0)
                }
            }
            details.incomeCurrency?.let { currency ->
                currencies[currency.code] = currency
                byCurrency.running(currency.code, details.category).apply {
                    income += Money(entry.incomeAmountMilli ?: 0)
                }
            }

            // A quantity belongs to the entry, not to a half, so it is counted once — under
            // the currency the money went out in, or the one it came in in when there was
            // no expense. Counting it in both groups of an exchange would double it.
            val quantityCode = details.expenseCurrency?.code ?: details.incomeCurrency?.code
            details.countedQuantityMilli?.let { milli ->
                quantityCode?.let { code ->
                    byCurrency.running(code, details.category).addQuantity(Quantity(milli))
                }
            }
        }

        return byCurrency.map { (code, categories) ->
            val totals = categories.values
                .map { it.toTotals() }
                // The order of the dictionary, the same one the categories are listed in
                // everywhere else. Sorting by amount instead would put a category in a
                // different place in every currency block, and the blocks could no longer
                // be read against each other.
                .sortedWith(
                    compareBy<CategoryTotals> { it.category.sortOrder }
                        .thenBy { it.category.id }
                )
            CurrencyReport(
                currency = currencies.getValue(code),
                categories = totals,
                income = totals.fold(Money.ZERO) { sum, it -> sum + it.income },
                expense = totals.fold(Money.ZERO) { sum, it -> sum + it.expense },
            )
        }.sortedBy { it.currency.code }
    }

    private fun MutableMap<String, MutableMap<Long, Running>>.running(
        code: String,
        category: Category,
    ): Running = getOrPut(code) { linkedMapOf() }.getOrPut(category.id) { Running(category) }

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
}
