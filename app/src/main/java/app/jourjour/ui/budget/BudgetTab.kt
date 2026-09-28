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
import app.jourjour.core.budget.BudgetCurrencyTotal
import app.jourjour.core.budget.BudgetTotals
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.money.Quantity
import app.jourjour.core.money.unitPrice
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.BudgetItemDetails
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.OverflowMenu

/**
 * What the trip is expected to cost, by category.
 *
 * Read before the trip and against the spending during it, so it is laid out like the
 * statistics rather than like a list of things: a band per category, the lines under it,
 * and what it all comes to at the foot.
 */
@Composable
fun BudgetTab(
    state: BudgetState,
    viewModel: BudgetViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.blocks.isEmpty() -> EmptyList(R.string.empty_budget, R.string.empty_budget_hint)

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                state.blocks.forEach { block ->
                    item(key = "category-${block.category.id}") {
                        CategoryHeader(
                            icon = block.category.icon,
                            name = localizedName(block.category.name, block.category.nameKey),
                        )
                    }
                    items(block.items, key = { "item-${it.item.id}" }) { details ->
                        ItemRow(
                            details = details,
                            onEdit = { viewModel.openSheet(details) },
                            onDelete = { viewModel.delete(details) },
                        )
                    }
                }
                state.totals?.let { totals ->
                    item(key = "totals") { Totals(totals) }
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        BudgetItemSheetContent(sheet = sheet, state = state, viewModel = viewModel)
    }
}

@Composable
private fun CategoryHeader(icon: String, name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = "$icon $name", style = MaterialTheme.typography.titleSmall)
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

/**
 * What the budget comes to: each currency on its own, and one figure under them where the
 * trip carries the rates to make one.
 */
@Composable
private fun Totals(totals: BudgetTotals) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        totals.byCurrency.forEach { line -> TotalRow(line, bold = false) }

        when (totals) {
            is BudgetTotals.Ready -> {
                // Only where it says something the lines above do not: a budget written in
                // one currency is already its own total.
                if (totals.byCurrency.size > 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    TotalRow(totals.total, bold = true)
                }
            }

            is BudgetTotals.RatesMissing -> Note(
                stringResource(
                    R.string.warning_budget_rates_missing,
                    totals.codes.joinToString(", "),
                )
            )

            is BudgetTotals.NoBaseCurrency -> Note(
                stringResource(R.string.warning_budget_base_currency)
            )
        }
    }
}

@Composable
private fun TotalRow(line: BudgetCurrencyTotal, bold: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (bold) {
                stringResource(R.string.budget_total_in_base, line.currency.code)
            } else {
                "${localizedName(line.currency.name, line.currency.nameKey)} (${line.currency.code})"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = MoneyFormat.formatAmountWithCurrency(line.amount, line.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
        )
    }
}

/** Why there is no single figure. Muted: nothing is wrong, something is simply not said. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    )
}
