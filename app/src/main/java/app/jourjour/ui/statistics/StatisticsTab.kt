package app.jourjour.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.stats.BaseTotals
import app.jourjour.core.stats.CategoryTotals
import app.jourjour.core.stats.CurrencyReport
import app.jourjour.core.text.localizedName
import app.jourjour.ui.common.ColumnTitle
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.MissingRatesWarning
import app.jourjour.ui.common.NoBaseCurrencyWarning
import app.jourjour.ui.common.NumberCell

/** The number columns share the width left over by the category name. */
private const val NUMBER_WEIGHT = 0.9f

/** A count is a handful of digits, not a sum: what it does not need goes to the name. */
private const val QUANTITY_WEIGHT = 0.5f
private const val NAME_WEIGHT = 1.7f


/**
 * What a trip came to: every currency on its own, the categories under it, and the two
 * totals at the foot.
 *
 * Amounts carry no currency mark of their own — the whole block is one currency, and the
 * heading says which. On a phone that is the difference between a table that fits and one
 * that does not.
 */
@Composable
fun StatisticsTab(
    state: StatisticsState,
    /** Where a missing rate is entered; the warning about one links straight to it. */
    onEditTrip: () -> Unit,
    /** Where the base currency is chosen, for the same reason. */
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.reports.isEmpty() -> EmptyList(R.string.empty_statistics)

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                // What happened first, what it comes to at the foot. The exact figures are
                // what the entries actually say; the converted ones are a reading of them,
                // and a reading belongs after the thing it reads.
                item(key = "by-currency") { SectionHeading(R.string.stats_by_currency) }
                state.reports.forEach { report ->
                    item(key = "currency-${report.currency.code}") { CurrencyBlock(report) }
                }
                state.baseTotals?.let { totals ->
                    item(key = "base-totals") { BaseTotalsBlock(totals, onEditTrip, onSettings) }
                }
                // Last, and a section of its own: it says the same thing as the figures
                // above in a way figures cannot, which is a third thing to read rather
                // than a decoration on the second.
                if (state.chartCategories.isNotEmpty()) {
                    item(key = "chart") {
                        SectionHeading(R.string.stats_expense_chart)
                        CategoryPie(
                            categories = state.chartCategories,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the trip came to, all of it in one currency — or the reason it cannot be said in
 * one, which is always a rate nobody has entered.
 */
@Composable
private fun BaseTotalsBlock(
    totals: BaseTotals,
    onEditTrip: () -> Unit,
    onSettings: () -> Unit,
) {
    when (totals) {
        is BaseTotals.Ready -> {
            SectionHeading(R.string.stats_base_header, totals.report.currency.code)
            ColumnTitles()
            totals.report.categories.forEach { row ->
                CategoryRow(row, totals.report.currency.decimals)
            }
            TotalsRow(totals.report)
        }

        is BaseTotals.RatesMissing -> MissingRatesWarning(totals.codes, onEditTrip)

        BaseTotals.NoBaseCurrency -> NoBaseCurrencyWarning(onSettings)
    }
}

/**
 * The name of one of the tab's two halves.
 *
 * A shade deeper than the currency headings inside them, so that the hierarchy is legible
 * at a glance: these two say what a run of blocks is, the ones under them say which
 * currency a block is.
 */
@Composable
private fun SectionHeading(@StringRes textRes: Int, vararg formatArgs: Any) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(textRes, *formatArgs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun CurrencyBlock(report: CurrencyReport) {
    CurrencyHeader(report)
    ColumnTitles()
    report.categories.forEach { totals -> CategoryRow(totals, report.currency.decimals) }
    TotalsRow(report)
}

@Composable
private fun CurrencyHeader(report: CurrencyReport) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Named, then coded. A symbol beside a column of figures that already carry it
        // says nothing; the name says which money this block is counting.
        Text(
            text = "${localizedName(report.currency.name, report.currency.nameKey)} " +
                "(${report.currency.code})",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ColumnTitles() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(text = "", modifier = Modifier.weight(NAME_WEIGHT))
        // Abbreviated, unlike the name it carries in a file: the column is as narrow as
        // the counts in it, and a full word would set the width instead of the figures.
        ColumnTitle(R.string.stats_quantity, QUANTITY_WEIGHT)
        ColumnTitle(R.string.section_income, NUMBER_WEIGHT)
        ColumnTitle(R.string.section_expense, NUMBER_WEIGHT)
    }
}

@Composable
private fun CategoryRow(totals: CategoryTotals, decimals: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${totals.category.icon} " +
                localizedName(totals.category.name, totals.category.nameKey),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(NAME_WEIGHT),
        )
        NumberCell(
            text = totals.quantity?.let(MoneyFormat::formatQuantity).orEmpty(),
            weight = FontWeight.Normal,
            width = QUANTITY_WEIGHT,
        )
        NumberCell(text = amount(totals.income, decimals), width = NUMBER_WEIGHT)
        NumberCell(text = amount(totals.expense, decimals), width = NUMBER_WEIGHT)
    }
}

@Composable
private fun TotalsRow(report: CurrencyReport) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.entries_totals),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(NAME_WEIGHT),
        )
        // The quantities are not added up: litres and nights have nothing in common.
        NumberCell(text = "", width = QUANTITY_WEIGHT, weight = FontWeight.Bold)
        NumberCell(
            text = amount(report.netIncome, report.currency.decimals),
            width = NUMBER_WEIGHT,
            weight = FontWeight.Bold,
        )
        NumberCell(
            text = amount(report.netExpense, report.currency.decimals),
            width = NUMBER_WEIGHT,
            weight = FontWeight.Bold,
        )
    }
}


/** Zero is left blank: a column of noughts reads as noise, not as information. */
private fun amount(money: Money?, decimals: Int): String =
    if (money == null || money.isZero) "" else MoneyFormat.formatAmount(money, decimals)
