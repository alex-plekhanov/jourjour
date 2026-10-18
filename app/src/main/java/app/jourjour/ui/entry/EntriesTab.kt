package app.jourjour.ui.entry

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.EntryDetails
import app.jourjour.ui.common.EmptyList
import app.jourjour.ui.common.PaymentMethodMark
import app.jourjour.ui.common.recordBlock
import app.jourjour.ui.common.rememberDayFormatter

/**
 * The entries of a trip, day by day, with what each day and the whole trip came to.
 *
 * The tally per currency sits at the top rather than at the foot: on a trip of any length
 * the foot of the list is a scroll away, and the totals are what a person opens the screen
 * for.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntriesTab(
    state: EntriesState,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            // A search that found nothing is not a trip with nothing in it, and inviting
            // someone to write their first entry when they have plenty is no answer.
            state.days.isEmpty() && state.search != null ->
                EmptyList(R.string.empty_search, R.string.empty_search_hint)

            // No days means no entries, and so no totals either: there is nothing to show
            // but the invitation, and it belongs in the middle of the empty screen.
            state.days.isEmpty() ->
                EmptyList(
                    R.string.empty_entries,
                    R.string.empty_entries_hint,
                    R.string.about_entries,
                )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                if (state.totals.isNotEmpty()) {
                    item { TripTotals(state) }
                }
                state.days.forEach { day ->
                    stickyHeader(key = "day-${day.date}") {
                        DayHeader(day)
                    }
                    items(day.entries, key = { it.entry.id }) { details ->
                        EntryRow(details = details, onClick = { onOpenEntry(details.entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TripTotals(state: EntriesState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.entries_totals),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.totals.forEach { total ->
            TotalRow(
                // Named in full: a code alone is a thing to look up, and the block is read
                // once at the top of a trip rather than scanned like a column of figures.
                name = "${localizedName(total.currency.name, total.currency.nameKey)} " +
                    "(${total.currency.code})",
                total = total,
            )
        }
    }
}

/**
 * A currency's name on the left, what it came to on the right — and on the next line down
 * when the two will not share one. A currency's name runs long in some languages, and
 * squeezing it into a column of syllables to keep the figures alongside serves neither.
 */
@Composable
private fun TotalRow(name: String, total: CurrencyTotal) {
    Layout(
        contents = listOf(
            {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    // The name is what the figures are about, not one of them.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            { TotalAmounts(total) },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) { (nameContent, valueContent), constraints ->
        val width = constraints.maxWidth
        val gap = TOTALS_GAP.roundToPx()
        val value = valueContent.first().measure(Constraints(maxWidth = width))
        val name = nameContent.first().measure(Constraints(maxWidth = width))
        val together = name.width + gap + value.width <= width

        layout(width, if (together) maxOf(name.height, value.height) else name.height + value.height) {
            name.place(0, 0)
            value.place(width - value.width, if (together) 0 else name.height)
        }
    }
}

/** The least that may stand between a currency's name and its total on one line. */
private val TOTALS_GAP = 12.dp

/**
 * What a currency came to, written the way an entry writes it: what went out plainly, what
 * came in under it with a plus and in the colour income is given everywhere else. The two
 * used to be one sentence — "spent X, received Y" — which read as prose and ran into the
 * name beside it.
 *
 * The symbol stays: it is what decodes the one printed against every entry below.
 */
@Composable
private fun TotalAmounts(total: CurrencyTotal) {
    Column(horizontalAlignment = Alignment.End) {
        // A zero is worth printing only when it is the whole answer.
        if (!total.expense.isZero || total.income.isZero) {
            Text(
                text = MoneyFormat.formatAmountWithCurrency(total.expense, total.currency),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!total.income.isZero) {
            Text(
                text = "+" + MoneyFormat.formatAmountWithCurrency(total.income, total.currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    val dayFormatter = rememberDayFormatter()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The deeper band the days of the plan and the categories of the budget are:
            // the entries under it stand on it as blocks. See [recordBlock].
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.date.format(dayFormatter),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = day.totals
                .filterNot { it.expense.isZero }
                .joinToString(", ") {
                    MoneyFormat.formatAmountWithCurrency(it.expense, it.currency)
                },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EntryRow(details: EntryDetails, onClick: () -> Unit) {
    val entry = details.entry
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // One record of a list that opens on a tap, as an event, a budget line and a
            // checklist item are, and drawn as they are. See [recordBlock].
            .recordBlock()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            // As tall as the wording needs and no taller, so that the figures can be held
            // against its top and the way of paying against its foot.
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = details.category.icon,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.width(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank {
                    localizedName(details.category.name, details.category.nameKey)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val subtitle = listOfNotNull(
                entry.place.takeIf { it.isNotBlank() },
                entry.title.takeIf { it.isNotBlank() }
                    ?.let { localizedName(details.category.name, details.category.nameKey) },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                EntryAmounts(details)
            }
            // How it was paid, in the corner the plan puts it in, and held against the foot
            // of the block however far the wording grows it. Only where the entry is one
            // movement of money: an exchange has two wallets, and one mark between two
            // figures would say which of them it belonged to no better than none.
            //
            // Set as small as the plan sets it, which also makes the figure and the mark
            // exactly as tall as a name and the line under it: a block of two lines comes
            // out the height it had before the mark, with the name on the line of the
            // figure, and one of a name alone keeps the name centred between the two.
            if ((details.expenseCurrency == null) != (details.incomeCurrency == null)) {
                PaymentMethodMark(
                    method = (if (details.expenseCurrency != null) details.expenseWallet else details.incomeWallet)
                        ?.paymentMethod,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** What went out plainly, what came in under it with a plus, as the totals write them. */
@Composable
private fun EntryAmounts(details: EntryDetails) {
    val entry = details.entry
    details.expenseCurrency?.let { currency ->
        Text(
            text = MoneyFormat.formatAmountWithCurrency(Money(entry.expenseAmountMilli ?: 0), currency),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    details.incomeCurrency?.let { currency ->
        Text(
            text = "+" + MoneyFormat.formatAmountWithCurrency(Money(entry.incomeAmountMilli ?: 0), currency),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
