package app.jourjour.core.stats

/**
 * What a chart of the trip's expenses can be drawn from, or nothing where a circle would
 * be comparing unlike things.
 *
 * Two cases and no others. Where the totals have been brought to the base currency, those
 * are what the shares are of. Where everything the trip cost was spent in one currency,
 * that currency's own figures already are that — there is nothing to convert, and they are
 * comparable as they stand. A trip that spent in several currencies with no rates between
 * them has no whole for a circle to divide: baht and lari cannot be added, and a pie of
 * them would be a picture of an arithmetic mistake.
 */
fun expenseChartCategories(
    baseTotals: BaseTotals?,
    reports: List<CurrencyReport>,
): List<CategoryTotals> {
    if (baseTotals is BaseTotals.Ready) return baseTotals.report.categories.spent()

    // One currency is its own whole, whatever the conversion is doing: a trip spent
    // entirely in lira can be drawn even while the rate to the base is missing.
    //
    // Counted by what was actually spent rather than by how many blocks there are. A trip
    // where dollars were changed into lira and the lira spent has two blocks — the
    // exchange put the dollars in one of its own — and only one of them is money the trip
    // cost, which is the one the circle is of.
    val spending = reports.map { it.categories.withoutExchanges().spent() }.filter { it.isNotEmpty() }
    return spending.singleOrNull().orEmpty()
}

/**
 * Money moved between a person's own pockets is not a thing the trip cost. The converted
 * totals have already dropped it; a single currency's own block has not, since there the
 * question is what happened to that currency.
 */
private fun List<CategoryTotals>.withoutExchanges(): List<CategoryTotals> =
    filterNot { it.category.isExpense && it.category.isIncome }

/** A slice of nothing is not a slice. */
private fun List<CategoryTotals>.spent(): List<CategoryTotals> =
    filter { !it.expense.isZero && !it.expense.isNegative }
