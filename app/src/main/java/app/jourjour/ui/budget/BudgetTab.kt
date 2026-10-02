package app.jourjour.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import app.jourjour.ui.common.FIGURE_GUTTER
import app.jourjour.ui.common.FigureColumn
import app.jourjour.ui.common.FigureLine
import app.jourjour.ui.common.figuresFit
import app.jourjour.ui.common.InfoTooltipButton
import app.jourjour.ui.common.MissingRatesWarning
import app.jourjour.ui.common.NoBaseCurrencyWarning
import app.jourjour.ui.common.NumberCell
import app.jourjour.ui.common.OverflowMenu
import app.jourjour.ui.common.PaymentMethodMark
import app.jourjour.ui.common.UNSET_METHOD_MARK

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
                            onTogglePlanningClosed = { viewModel.togglePlanningClosed(block.category) },
                            isSpendingClosed = block.isSpendingClosed,
                            isPlanningClosed = block.isPlanningClosed,
                        )
                    }
                    items(block.items, key = { "item-${it.item.id}" }) { details ->
                        ItemRow(details = details, onEdit = { viewModel.openSheet(details) })
                    }
                    item(key = "category-totals-${block.category.id}") { CategoryTotals(block) }
                }
                state.standing?.let { standing ->
                    item(key = "standing") { Standing(standing, onEditTrip, onSettings) }
                }
            }
        }
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
 * A category, as a line across the list, with the two things a category of a budget has
 * to say about itself behind the three dots.
 *
 * Unbanded: what is banded on this tab is what divides it — the two sections and the parts
 * of them — and a category is a name over its own lines, not another division.
 *
 * They are separate questions and either can be answered without the other. The spending
 * being over says nothing about whether the plan was finished; a plan declared complete
 * says nothing about whether the money has gone.
 */
@Composable
private fun CategoryHeader(
    category: Category,
    onToggleClosed: () -> Unit,
    onTogglePlanningClosed: () -> Unit,
    isSpendingClosed: Boolean,
    isPlanningClosed: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = { Text(stringResource(R.string.action_close_planning)) },
                leadingIcon = {
                    Checkbox(checked = isPlanningClosed, onCheckedChange = null)
                },
                trailingIcon = {
                    InfoTooltipButton(
                        titleRes = R.string.action_close_planning,
                        textRes = R.string.hint_close_planning,
                        descriptionRes = R.string.action_about_close_planning,
                    )
                },
                onClick = { dismiss(); onTogglePlanningClosed() },
            )
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
        if (block.isPlanningClosed) {
            Settled(R.string.action_close_planning)
        }
        if (block.isSpendingClosed) {
            Settled(R.string.action_close_spending)
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
        // Each deposit under the plan it is part of, rather than one line for the two of
        // them: the estimate and the booking are two answers to the same question, and so
        // are the deposits inside them. Added together they would be counted twice.
        if (block.plannedDeposits.isNotEmpty()) {
            AmountsRow(R.string.budget_row_deposit, block.plannedDeposits)
        }
        // The finer plan, between the estimate and the fact because that is where it
        // stands: what was booked, against what was guessed and what was paid.
        if (block.scheduled.isNotEmpty()) AmountsRow(R.string.budget_row_planned, block.scheduled)
        if (block.scheduledDeposits.isNotEmpty()) {
            AmountsRow(R.string.budget_row_deposit, block.scheduledDeposits)
        }
        if (block.spent.isNotEmpty()) AmountsRow(R.string.budget_row_actual, block.spent)
    }
}

/** A line saying what has been declared about the category, above what it comes to. */
@Composable
private fun Settled(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
    )
}

/**
 * A name on the left and the amounts on the right, one line each: one per currency and,
 * inside a currency, one per way of paying.
 *
 * A line rather than a comma-separated run because each of these is a separate thing to
 * arrange — five hundred in cash and five hundred on the card are two errands — and
 * because the way of paying is an icon, which does not read in the middle of a sentence
 * of figures. The name stands beside the first of them and the rest hang under it.
 */
@Composable
private fun AmountsRow(@StringRes labelRes: Int, amounts: List<CurrencyAmount>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            amounts.forEach { figure ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MoneyFormat.formatAmountWithCurrency(figure.amount, figure.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                    )
                    PaymentMethodMark(figure.paymentMethod)
                }
            }
        }
    }
}

/**
 * One line: what it is on the left, what it comes to on the right, and underneath the two
 * figures it was arrived at — so many at so much — where there are any.
 *
 * The whole row opens the form, and there is nothing else on it to press: changing the
 * words and taking the line away both happen there, which leaves the list to be read.
 */
@Composable
private fun ItemRow(
    details: BudgetItemDetails,
    onEdit: () -> Unit,
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
            modifier = Modifier.padding(start = 4.dp),
        )
        // The same mark the totals under the list carry, and the same room kept for it
        // where there is none: the lines and the figure they add up to read down one edge.
        PaymentMethodMark(details.paymentMethod)
        Spacer(modifier = Modifier.width(16.dp))
    }
}

/** The name of the block, a shade deeper than the headings of its parts. */
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
            // Whether this is still a table is asked once, of the whole of it: a block
            // where some rows kept their columns and others gave them up would be two
            // tables printed into one another. See figuresFit.
            BoxWithConstraints {
                val decimals = standing.currency.decimals
                val stacked = !figuresFit(
                    columns = standingColumns(standing, decimals),
                    nameWeight = NAME_WEIGHT,
                    available = maxWidth - SIDE_MARGIN * 2,
                )
                Column {
                    // Two readings of the same converted figures, each with its own
                    // heading: how the budget is holding up, and what is left to find in
                    // each pocket. The second is named because the first was not the only
                    // thing in the block any more.
                    SubsectionHeading(R.string.budget_standing_section)
                    // Headings over columns that are no longer there would be heading
                    // nothing; stacked, every figure carries its own.
                    if (!stacked) StandingTitles()
                    standing.categories.forEach { row -> StandingRow(row, decimals, stacked) }
                    StandingTotals(standing, stacked)
                    // Nothing to say where no plan says how it is to be paid: one bucket
                    // of unsaid money repeating the table above it.
                    if (standing.plansSayHow) ByMethod(standing, stacked)
                }
            }
        }

        // The same two lines the statistics give, said once and in one place: it is the
        // same missing rate holding up the same arithmetic.
        is BudgetVersusActual.RatesMissing -> MissingRatesWarning(standing.codes, onEditTrip)

        BudgetVersusActual.NoBaseCurrency -> NoBaseCurrencyWarning(onSettings)
    }
}

/**
 * What is left to find, pocket by pocket, for every category still spending.
 *
 * The question the table above cannot answer: it says four hundred is left of the
 * accommodation, and this says three hundred of it has to be cash. Category by category,
 * because that is where a plan and a spending meet — the way of paying is a property of a
 * line, not of the trip.
 *
 * A closed category is left out altogether. Nothing more is coming from it, so there is
 * nothing left to arrange, and a band with no rows under it would be worse than silence.
 */
@Composable
private fun ByMethod(standing: BudgetVersusActual.Ready, stacked: Boolean) {
    val decimals = standing.currency.decimals
    SubsectionHeading(R.string.budget_by_method_section)
    if (!stacked) StandingTitles()
    standing.categories.forEach { row ->
        val methods = row.byMethod.filterNot { it.isEmpty }
        if (methods.isEmpty()) return@forEach
        CategoryBand(row.category)
        methods.forEach { method ->
            StandingLine(
                // The icon alone, the name of the method being the one thing the column
                // has no room for and the icon the thing a person recognises anyway. Set
                // in from the edge so that it is not read as the category's own icon.
                name = method.paymentMethod?.icon ?: UNSET_METHOD_MARK,
                planned = amount(method.planned, decimals),
                spent = amount(method.spent, decimals),
                remaining = amount(method.remaining, decimals),
                stacked = stacked,
                nameInset = METHOD_INSET,
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    // Only the remainder of the three: a plan and a spending added up over the categories
    // are the figures the table above already gives, and this block is about what is left.
    StandingLine(
        name = stringResource(R.string.entries_totals),
        planned = "",
        spent = "",
        remaining = amount(standing.remaining, decimals),
        stacked = stacked,
        weight = FontWeight.Bold,
        bottomPadding = 8.dp,
    )
    // What the total is made of: here the method is named as well as drawn, there being
    // room for it where no category stands in front of it.
    standing.byMethod
        .filterNot { it.remaining.isZero }
        .forEach { row ->
            RemainderLine(
                name = row.paymentMethod
                    ?.let { "${it.icon} ${localizedName(it.name, it.nameKey)}" }
                    ?: "$UNSET_METHOD_MARK ${stringResource(R.string.value_not_set)}",
                amount = amount(row.remaining, decimals),
                stacked = stacked,
            )
        }
    Spacer(modifier = Modifier.height(8.dp))
}

/** The category a block of method rows belongs to, named the way the list above names its own. */
@Composable
private fun CategoryBand(category: Category) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            text = "${category.icon} ${localizedName(category.name, category.nameKey)}",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** A name and one figure, the figure standing in the last of the columns above it. */
@Composable
private fun RemainderLine(name: String, amount: String, stacked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(FIGURE_GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            // Takes the two columns its figure does not, as the expected total does, so
            // that the figure lands under the remainders it is a breakdown of.
            modifier = Modifier
                .weight(if (stacked) 1f else NAME_WEIGHT + NUMBER_WEIGHT * 2)
                .padding(start = METHOD_INSET),
        )
        if (stacked) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
        } else {
            NumberCell(amount, NUMBER_WEIGHT)
        }
    }
}

/**
 * The name of a part of a block, under the heading of the block itself.
 *
 * Banded, a shade lighter than the block's own heading: what divides the page is a
 * heading, and a category inside one is a name over its rows rather than another divider.
 */
@Composable
private fun SubsectionHeading(@StringRes textRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Everything the three columns will print, for deciding whether they still fit. */
@Composable
private fun standingColumns(
    standing: BudgetVersusActual.Ready,
    decimals: Int,
): List<FigureColumn> {
    val titles = listOf(
        stringResource(R.string.budget_planned),
        stringResource(R.string.budget_spent),
        stringResource(R.string.budget_remaining),
    )
    val figures = listOf(
        standing.categories.map { amount(it.planned, decimals) } +
            amount(standing.planned, decimals),
        standing.categories.map { amount(it.spent, decimals) } +
            amount(standing.spent, decimals),
        standing.categories.map { amount(it.remaining, decimals) } +
            amount(standing.remaining, decimals) +
            amount(standing.expected, decimals),
    )
    return titles.zip(figures) { title, column ->
        FigureColumn(NUMBER_WEIGHT, column + title)
    }
}

@Composable
private fun StandingTitles() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(FIGURE_GUTTER),
    ) {
        Text(text = "", modifier = Modifier.weight(NAME_WEIGHT))
        ColumnTitle(R.string.budget_planned, NUMBER_WEIGHT)
        ColumnTitle(R.string.budget_spent, NUMBER_WEIGHT)
        ColumnTitle(R.string.budget_remaining, NUMBER_WEIGHT)
    }
}

@Composable
private fun StandingRow(row: BudgetCategoryStanding, decimals: Int, stacked: Boolean) {
    StandingLine(
        name = "${row.category.icon} " +
            localizedName(row.category.name, row.category.nameKey),
        planned = amount(row.planned, decimals),
        spent = amount(row.spent, decimals),
        remaining = amount(row.remaining, decimals),
        stacked = stacked,
    )
}

/**
 * One line of the block, either way round: the name with its three figures beside it in
 * their columns, or the name with them listed underneath.
 */
@Composable
private fun StandingLine(
    name: String,
    planned: String,
    spent: String,
    remaining: String,
    stacked: Boolean,
    weight: FontWeight = FontWeight.Normal,
    bottomPadding: Dp = 6.dp,
    /**
     * How far the name is set in inside its own column, for a row that belongs under the
     * one above it. Inside the column rather than on the row, so that the figures stay in
     * the columns they are read down.
     */
    nameInset: Dp = 0.dp,
) {
    if (stacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp + nameInset, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
            )
            // A figure nobody has written is nothing to label: in a column an empty cell
            // is a blank, listed it would be a heading with nothing after it.
            if (planned.isNotBlank()) FigureLine(R.string.budget_planned, planned, weight)
            if (spent.isNotBlank()) FigureLine(R.string.budget_spent, spent, weight)
            if (remaining.isNotBlank()) FigureLine(R.string.budget_remaining, remaining, weight)
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(FIGURE_GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            modifier = Modifier
                .weight(NAME_WEIGHT)
                .padding(start = nameInset),
        )
        NumberCell(planned, NUMBER_WEIGHT, weight)
        NumberCell(spent, NUMBER_WEIGHT, weight)
        NumberCell(remaining, NUMBER_WEIGHT, weight)
    }
}

@Composable
private fun StandingTotals(standing: BudgetVersusActual.Ready, stacked: Boolean) {
    val decimals = standing.currency.decimals
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    StandingLine(
        name = stringResource(R.string.entries_totals),
        planned = amount(standing.planned, decimals),
        spent = amount(standing.spent, decimals),
        remaining = amount(standing.remaining, decimals),
        stacked = stacked,
        weight = FontWeight.Bold,
        bottomPadding = 8.dp,
    )

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
        horizontalArrangement = Arrangement.spacedBy(FIGURE_GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.budget_expected_total),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            // The name takes the two columns its figure does not, so that the figure lands
            // in the last one and reads down the page against the totals above it. Where
            // the columns have been given up there is nothing to line up with, and the
            // figure simply follows the name across the width.
            modifier = Modifier.weight(
                if (stacked) 1f else NAME_WEIGHT + NUMBER_WEIGHT * 2,
            ),
        )
        if (stacked) {
            Text(
                text = amount(standing.expected, decimals),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        } else {
            NumberCell(amount(standing.expected, decimals), NUMBER_WEIGHT, FontWeight.Bold)
        }
    }
}

/** The name column takes what the three figures leave, as it does in the statistics. */
/** What every row of the block is set in from the edges of the screen by. */
private val SIDE_MARGIN = 16.dp

/** The name column takes what the three figures leave, as it does in the statistics. */
private const val NAME_WEIGHT = 1.7f
private const val NUMBER_WEIGHT = 0.9f

/** How far a row about a way of paying is set in from the category it belongs to. */
private val METHOD_INSET = 16.dp


/** Zero is left blank: a column of noughts reads as noise, not as information. */
private fun amount(money: Money?, decimals: Int): String =
    if (money == null || money.isZero) "" else MoneyFormat.formatAmount(money, decimals)

