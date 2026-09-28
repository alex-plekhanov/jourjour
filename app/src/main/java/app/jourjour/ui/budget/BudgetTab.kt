package app.jourjour.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.R
import androidx.annotation.StringRes
import app.jourjour.core.budget.BudgetCategoryStanding
import app.jourjour.core.budget.BudgetVersusActual
import app.jourjour.core.budget.CurrencyAmount
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.ui.common.ColumnTitle
import app.jourjour.ui.common.ConfirmDialog
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.InfoTooltipButton
import app.jourjour.ui.common.MissingRatesWarning
import app.jourjour.ui.common.NoBaseCurrencyWarning
import app.jourjour.ui.common.NumberCell
import app.jourjour.ui.common.OverflowMenu

/**
 * What the trip is expected to cost, by category, and how it is holding up.
 *
 * Two sections, laid out as the statistics are: what was written down, category by
 * category with its own lines and its own totals in the currencies they were written in;
 * and then the whole thing brought to the base currency and read against what has actually
 * been spent. The exact figures first, the reading of them after — as in the statistics,
 * where converted figures follow the ones the entries actually say.
 */
@Composable
fun BudgetTab(
    state: BudgetState,
    viewModel: BudgetViewModel,
    /** Where a missing rate is entered; the line about one links straight to it. */
    onEditTrip: () -> Unit,
    /** Where the base currency is chosen, for the same reason. */
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.blocks.isEmpty() -> EmptyList(
                R.string.empty_budget,
                R.string.empty_budget_hint,
                R.string.about_budget,
            )

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                item(key = "planned") { SectionHeading(stringResource(R.string.budget_planned_section)) }
                state.blocks.forEach { block ->
                    item(key = "category-${block.category.id}") {
                        CategoryHeader(
                            category = block.category,
                            onToggleClosed = { viewModel.toggleSpendingClosed(block.category) },
                            isSpendingClosed = block.isSpendingClosed,
                        )
                    }
                    items(block.items, key = { "item-${it.item.id}" }) { details ->
                        ItemRow(
                            details = details,
                            onEdit = { viewModel.openSheet(details) },
                            onDelete = { viewModel.delete(details) },
                        )
                    }
                    item(key = "category-totals-${block.category.id}") { CategoryTotals(block) }
                }
                state.standing?.let { standing ->
                    item(key = "standing") { Standing(standing, onEditTrip, onSettings) }
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        BudgetItemSheetContent(sheet = sheet, state = state, viewModel = viewModel)
    }

    state.converter?.let { converter ->
        ConverterSheetContent(
            converter = converter,
            viewModel = viewModel,
            onEditTrip = onEditTrip,
            onSettings = onSettings,
        )
    }

    if (state.isClosingAll) {
        ConfirmDialog(
            title = stringResource(R.string.action_close_all_spending),
            message = stringResource(R.string.confirm_close_all_spending),
            onConfirm = viewModel::confirmCloseAll,
            onDismiss = viewModel::cancelCloseAll,
        )
    }
}

/**
 * A category, as a band across the list, with the one thing a category of a budget has to
 * say about itself behind the three dots.
 */
@Composable
private fun CategoryHeader(
    category: Category,
    onToggleClosed: () -> Unit,
    isSpendingClosed: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${category.icon} ${localizedName(category.name, category.nameKey)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        OverflowMenu { dismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_close_spending)) },
                leadingIcon = {
                    Checkbox(checked = isSpendingClosed, onCheckedChange = null)
                },
                // What closing a category does to the figures takes a paragraph to say,
                // and the menu is where somebody is standing when they wonder.
                trailingIcon = {
                    InfoTooltipButton(
                        titleRes = R.string.action_close_spending,
                        textRes = R.string.hint_close_spending,
                        descriptionRes = R.string.action_about_close_spending,
                    )
                },
                onClick = { dismiss(); onToggleClosed() },
            )
        }
    }
}

/**
 * What a category comes to: what was budgeted for it and what has gone on it, each in the
 * currencies it was written in and joined by commas the way a day's takings are.
 *
 * No conversion here on purpose — these are the figures as entered, and the block below
 * is where they are brought together. A closed category says so in a line of its own
 * rather than by being struck through: what it means is about the money still to come, and
 * a line through the name would read as the category itself being cancelled.
 */
@Composable
private fun CategoryTotals(block: BudgetCategoryBlock) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (block.isSpendingClosed) {
            Text(
                text = stringResource(R.string.action_close_spending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
        Text(
            text = stringResource(R.string.budget_category_totals),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        )
        // A side with nothing on it says nothing: a category nothing has been spent on
        // yet is a category whose plan is the whole of what there is to read.
        if (block.planned.isNotEmpty()) AmountsRow(R.string.budget_row_budget, block.planned)
        if (block.spent.isNotEmpty()) AmountsRow(R.string.budget_row_actual, block.spent)
    }
}

/** A name on the left and a run of amounts on the right, one per currency. */
@Composable
private fun AmountsRow(@StringRes labelRes: Int, amounts: List<CurrencyAmount>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amounts.joinToString(", ") {
                MoneyFormat.formatAmountWithCurrency(it.amount, it.currency)
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * One line: what it is on the left, what it comes to on the right, and underneath the two
 * figures it was arrived at — so many at so much — where there are any.
 */
@Composable
private fun ItemRow(
    details: BudgetItemDetails,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = details.item
    val quantity = item.quantityMilli?.let(::Quantity)
    val price = unitPrice(Money(item.amountMilli), quantity)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val figures = if (quantity != null && price != null) {
            stringResource(
                R.string.budget_quantity_at_price,
                MoneyFormat.formatQuantity(quantity),
                MoneyFormat.formatAmount(price, details.currency.decimals),
            )
        } else {
            null
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            // A line with no name of its own is known by how its figure was arrived at, and
            // failing that by the category it is filed under — which is the band above it,
            // but a row that said nothing at all would be worse than one that repeats.
            Text(
                text = item.title.ifBlank {
                    figures ?: localizedName(details.category.name, details.category.nameKey)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (figures != null && item.title.isNotBlank()) {
                Text(
                    text = figures,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = MoneyFormat.formatAmountWithCurrency(Money(item.amountMilli), details.currency),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        OverflowMenu { dismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { dismiss(); onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { dismiss(); onDelete() },
            )
        }
    }
}

/** The name of the block, a shade deeper than the category bands inside the list. */
@Composable
private fun SectionHeading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * The budget read against what has actually been spent, category by category.
 *
 * Every category with either side to it: one that was budgeted for and has had nothing
 * spent on it yet, and one that was never budgeted for and has had money go on it anyway —
 * the second being the more worth seeing of the two.
 */
@Composable
private fun Standing(
    standing: BudgetVersusActual,
    onEditTrip: () -> Unit,
    onSettings: () -> Unit,
) {
    when (standing) {
        is BudgetVersusActual.Ready -> {
            SectionHeading(
                stringResource(R.string.stats_base_header, standing.currency.code)
            )
            StandingTitles()
            standing.categories.forEach { row -> StandingRow(row, standing.currency.decimals) }
            StandingTotals(standing)
        }

        // The same two lines the statistics give, said once and in one place: it is the
        // same missing rate holding up the same arithmetic.
        is BudgetVersusActual.RatesMissing -> MissingRatesWarning(standing.codes, onEditTrip)

        BudgetVersusActual.NoBaseCurrency -> NoBaseCurrencyWarning(onSettings)
    }
}

@Composable
private fun StandingTitles() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(text = "", modifier = Modifier.weight(NAME_WEIGHT))
        ColumnTitle(R.string.budget_planned, NUMBER_WEIGHT)
        ColumnTitle(R.string.budget_spent, NUMBER_WEIGHT)
        ColumnTitle(R.string.budget_remaining, NUMBER_WEIGHT)
    }
}

@Composable
private fun StandingRow(row: BudgetCategoryStanding, decimals: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.category.icon} " +
                localizedName(row.category.name, row.category.nameKey),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(NAME_WEIGHT),
        )
        NumberCell(text = amount(row.planned, decimals), width = NUMBER_WEIGHT)
        NumberCell(text = amount(row.spent, decimals), width = NUMBER_WEIGHT)
        NumberCell(text = amount(row.remaining, decimals), width = NUMBER_WEIGHT)
    }
}

@Composable
private fun StandingTotals(standing: BudgetVersusActual.Ready) {
    val decimals = standing.currency.decimals
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.entries_totals),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(NAME_WEIGHT),
        )
        NumberCell(amount(standing.planned, decimals), NUMBER_WEIGHT, FontWeight.Bold)
        NumberCell(amount(standing.spent, decimals), NUMBER_WEIGHT, FontWeight.Bold)
        NumberCell(amount(standing.remaining, decimals), NUMBER_WEIGHT, FontWeight.Bold)
    }

    // Where the trip is heading, which the three columns above do not say anywhere: the
    // plan is what was meant to happen, and this is what will have happened if the rest of
    // it goes as intended.
    //
    // Ruled off from the totals although it is set like them: standing in the last column
    // under a column of remainders, an unruled figure would read as one more of them.
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.budget_expected_total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            // The name takes the two columns its figure does not, so that the figure lands
            // in the last one and reads down the page against the totals above it.
            modifier = Modifier.weight(NAME_WEIGHT + NUMBER_WEIGHT * 2),
        )
        NumberCell(amount(standing.expected, decimals), NUMBER_WEIGHT, FontWeight.Bold)
    }
}

/** The name column takes what the three figures leave, as it does in the statistics. */
private const val NAME_WEIGHT = 1.7f
private const val NUMBER_WEIGHT = 0.9f

/** Zero is left blank: a column of noughts reads as noise, not as information. */
private fun amount(money: Money?, decimals: Int): String =
    if (money == null || money.isZero) "" else MoneyFormat.formatAmount(money, decimals)

