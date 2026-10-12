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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.jourjour.R
import androidx.annotation.StringRes
import app.jourjour.core.budget.CurrencyAmount
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.data.entity.Category
import app.jourjour.ui.common.ConfirmDialog
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.InfoTooltipButton
import app.jourjour.ui.common.OverflowMenu
import app.jourjour.ui.common.PaymentMethodMark

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
 * A category, as a band across the list, with the two things a category of a budget has to
 * say about itself behind the three dots.
 *
 * Banded as the second-level headings of the block below it are, and for the same reason:
 * it heads the lines under it. The names inside the tables below carry no band — there a
 * category is a name over three columns and not a division of the page.
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
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer),
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
    // No rule above these: the lines they add up are blocks standing off the page, and a
    // line drawn under the last of them would belong to that block rather than part it
    // from what follows.
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
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
        // The finer plan, between the estimate and the fact because that is where it
        // stands: what was booked, against what was guessed and what was paid.
        if (block.scheduled.isNotEmpty()) AmountsRow(R.string.budget_row_planned, block.scheduled)
        // The deposit goes under the booking it is part of, that being the only plan that
        // has any: a deposit is paid on a day and given back on another, which is what
        // the schedule is for and what the budget's rough figures do not say.
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

    // Raised off the page with the corners an event of the schedule has, and a wallet of
    // the wallets tab: it is the same kind of thing to read and the same thing to do with
    // it — one record of a list, on a tab of a trip, that opens on a tap. The bands of the
    // categories are the page it stands on, as empty time is the page a booking stands on.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BLOCK_MARGIN, vertical = BLOCK_GAP)
            .shadow(BLOCK_ELEVATION, RoundedCornerShape(BLOCK_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
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
        Spacer(modifier = Modifier.width(12.dp))
    }
}


/**
 * What a line of the budget stands off the page by, and the corners it lifts as it does.
 *
 * The same block an event of the schedule is and a wallet of the wallets tab: one record
 * of a list, on a tab of a trip, that opens on a tap.
 */
private val BLOCK_CORNER = 12.dp
private val BLOCK_ELEVATION = 2.dp

/** How far such a block is set in from the edges, and off the one above it. */
private val BLOCK_MARGIN = 12.dp
private val BLOCK_GAP = 3.dp

