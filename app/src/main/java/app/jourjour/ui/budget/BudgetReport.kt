package app.jourjour.ui.budget

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.budget.BudgetCategoryStanding
import app.jourjour.core.budget.BudgetSide
import app.jourjour.core.budget.BudgetVersusActual
import app.jourjour.core.budget.PaymentMethodStanding
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.text.localizedName
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.ui.common.ColumnTitle
import app.jourjour.ui.common.FIGURE_GUTTER
import app.jourjour.ui.common.FigureColumn
import app.jourjour.ui.common.FigureLine
import app.jourjour.ui.common.InfoTooltipButton
import app.jourjour.ui.common.MissingRatesWarning
import app.jourjour.ui.common.NoBaseCurrencyWarning
import app.jourjour.ui.common.NumberCell
import app.jourjour.ui.common.figuresFit

/**
 * What the budget comes to, over the whole screen and off the tab's own menu.
 *
 * Not a part of the tab any more. The tab is the plan as it is written and worked on — a
 * list of lines a person adds to — and this is the reading of it: everything brought to
 * one currency and set against what has gone. That is a thing somebody opens to look at
 * rather than something to scroll past on the way to the next line, which is also why the
 * statistics of the entries are where they are.
 *
 * What stops the reading — a currency with no rate, no base currency chosen — is said
 * here too, for the same reason: it is this block that needs them and nothing else does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetReportScreen(
    standing: BudgetVersusActual?,
    onEditTrip: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    // A screen over the tab rather than a destination of its own, as the forms are, so
    // Back is wired here: shutting it is what Back means while it is open.
    BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_budget_report)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                standing?.let { item { Standing(it, onEditTrip, onSettings) } }
            }
        }
    }
}

/**
 * The name of what the whole screen is about, banded a step deeper than the parts of it.
 *
 * Two depths of stripe, which is what the page has levels of: the reading as a whole, and
 * the two readings it is made of. They are the same blue a step apart, as the buttons of
 * the app are — see the palette.
 */
@Composable
private fun SectionHeading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
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
 * One figure to a row, and that figure the remainder. What was planned and what has gone
 * are the table above, printed there once; repeated here they made four columns of which
 * three were already answered, and the one answer this block exists for was the narrowest
 * of them.
 *
 * A closed category is left out altogether, and so is a pocket with nothing left in it.
 * Nothing more is coming from them, so there is nothing left to arrange.
 */
@Composable
private fun ByMethod(standing: BudgetVersusActual.Ready, stacked: Boolean) {
    val decimals = standing.currency.decimals
    SubsectionHeading(
        textRes = R.string.budget_by_method_section,
        hintRes = R.string.hint_by_method,
        descriptionRes = R.string.action_about_by_method,
    )
    standing.categories.forEach { row -> ByMethodRows(row, decimals, stacked) }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    RemainderLine(
        name = stringResource(R.string.entries_totals),
        amount = amount(standing.remaining, decimals),
        stacked = stacked,
        inset = 0.dp,
        weight = FontWeight.Bold,
    )
    // What the total is made of: here the method is named as well as drawn, there being
    // room for it where no category stands in front of it.
    standing.byMethod
        .filterNot { it.remaining.isZero }
        .forEach { row ->
            RemainderLine(methodName(row.paymentMethod), amount(row.remaining, decimals), stacked)
        }
    // And what of it is coming back, which is money to be had rather than money to find
    // and does not belong in the same column of pockets. Broken down in its turn where
    // the deposits say which pocket they will come back to.
    val back = standing.depositRemaining
    if (!back.isZero) {
        RemainderLine(
            name = stringResource(R.string.budget_row_deposit_back),
            amount = amount(back, decimals),
            stacked = stacked,
            // A total of its own and set like one: what is to come back is not one more
            // of the pockets above it, and under them in their own weight it would read
            // as one.
            inset = 0.dp,
            weight = FontWeight.Bold,
        )
        if (standing.depositByMethod.any { it.paymentMethod != null }) {
            standing.depositByMethod
                .filterNot { it.remaining.isZero }
                .forEach { row ->
                    RemainderLine(
                        name = methodName(row.paymentMethod),
                        amount = amount(row.remaining, decimals),
                        stacked = stacked,
                    )
                }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * One category of the block: what is left to pay out under it, and what is left to come
 * back.
 *
 * A side whose plan named no method is one line carrying its remainder; one that named
 * some is a name over a row per pocket.
 *
 * The two sides are named at the same edge — the category and, under it, the deposit back
 * — and each has its own ways of paying set in one step from it, so that the two little
 * tables are read as the same table twice rather than as one nested in the other.
 */
@Composable
private fun ByMethodRows(row: BudgetCategoryStanding, decimals: Int, stacked: Boolean) {
    // A pocket with nothing left in it is not an answer to the question this block asks,
    // whatever was planned or has gone through it — those are the table above.
    val paying = row.main.byMethod.filterNot { it.remaining.isZero }
    val back = row.deposit?.byMethod?.filterNot { it.remaining.isZero }.orEmpty()
    if (paying.isEmpty() && back.isEmpty()) return

    when {
        // Nothing of its own left, but a deposit under it: the name is a heading then.
        paying.isEmpty() -> RemainderLine(categoryName(row.category), "", stacked, inset = 0.dp)
        row.main.isSplit -> {
            RemainderLine(categoryName(row.category), "", stacked, inset = 0.dp)
            paying.forEach { MethodLine(it, decimals, stacked) }
        }
        else -> RemainderLine(
            name = categoryName(row.category),
            amount = amount(row.main.remaining, decimals),
            stacked = stacked,
            inset = 0.dp,
        )
    }

    if (back.isEmpty()) return
    val deposit = requireNotNull(row.deposit)
    val name = stringResource(R.string.budget_row_deposit_back)
    if (deposit.isSplit) {
        RemainderLine(name, "", stacked, inset = 0.dp)
        back.forEach { MethodLine(it, decimals, stacked) }
    } else {
        RemainderLine(name, amount(deposit.remaining, decimals), stacked, inset = 0.dp)
    }
}

/**
 * One way of paying inside a category: its icon and its name, as the totals name theirs.
 *
 * Set in from the edge so that it is read as belonging to the name above it. The name of
 * the method goes with its icon rather than the icon standing alone — the first column is
 * read downwards, and a run of icons with nothing beside them is a column of things to
 * recognise where every other row of the block says what it is.
 */
@Composable
private fun MethodLine(method: PaymentMethodStanding, decimals: Int, stacked: Boolean) {
    RemainderLine(
        name = methodName(method.paymentMethod),
        amount = amount(method.remaining, decimals),
        stacked = stacked,
    )
}

/** A way of paying where there is room to name it as well as draw it. */
@Composable
private fun methodName(method: PaymentMethod?): String = method
    ?.let { "${it.icon} ${localizedName(it.name, it.nameKey)}" }
    ?: stringResource(R.string.budget_method_unset)

/** A name and one figure, the figure standing in the last of the columns above it. */
@Composable
private fun RemainderLine(
    name: String,
    amount: String,
    stacked: Boolean,
    inset: Dp = METHOD_INSET,
    weight: FontWeight = FontWeight.Normal,
) {
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
            fontWeight = weight,
            // Takes the two columns its figure does not, as the expected total does, so
            // that the figure lands under the remainders it is a breakdown of.
            modifier = Modifier
                .weight(if (stacked) 1f else NAME_WEIGHT + NUMBER_WEIGHT * 2)
                .padding(start = inset),
        )
        if (stacked) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                textAlign = TextAlign.End,
            )
        } else {
            NumberCell(amount, NUMBER_WEIGHT, weight)
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
private fun SubsectionHeading(
    @StringRes textRes: Int,
    /** What this part says and where its figures come from, where that takes saying. */
    @StringRes hintRes: Int? = null,
    @StringRes descriptionRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        if (hintRes != null && descriptionRes != null) {
            InfoTooltipButton(
                titleRes = textRes,
                textRes = hintRes,
                descriptionRes = descriptionRes,
            )
        }
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
        sideColumn(standing, decimals) { it.planned } + amount(standing.planned, decimals),
        sideColumn(standing, decimals) { it.spent } + amount(standing.spent, decimals),
        sideColumn(standing, decimals) { it.remaining } +
            amount(standing.remaining, decimals) +
            amount(standing.expected, decimals),
    )
    return titles.zip(figures) { title, column ->
        FigureColumn(NUMBER_WEIGHT, column + title)
    }
}

/**
 * One column of the table, both sides of every category in it.
 *
 * A deposit is the widest figure a category prints — it carries a minus in front of it —
 * so a column measured without the deposits is a column measured short.
 */
private fun sideColumn(
    standing: BudgetVersusActual.Ready,
    decimals: Int,
    of: (BudgetSide) -> Money,
): List<String> = standing.categories.flatMap { row ->
    listOfNotNull(
        amount(of(row.main), decimals),
        row.deposit?.let { amount(of(it), decimals) },
    )
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
        name = categoryName(row.category),
        planned = amount(row.main.planned, decimals),
        spent = amount(row.main.spent, decimals),
        remaining = amount(row.main.remaining, decimals),
        stacked = stacked,
        bottomPadding = if (row.deposit == null) 6.dp else 2.dp,
        // Each lock against the figure that has stopped moving: the plan where the
        // planning is declared finished, what has gone where the spending is.
        plannedLocked = row.isPlanningClosed,
        spentLocked = row.isSpendingClosed,
    )
    // What has to be put down and is to come back, on a line of its own under the costs
    // it belongs to. Counted apart rather than netted — a deposit is a thing to carry and
    // not a discount — and written as the negative figures it is, so that the two lines
    // read as the totals at the foot are made.
    row.deposit?.let { back ->
        StandingLine(
            name = stringResource(R.string.budget_row_deposit_back),
            planned = amount(back.planned, decimals),
            spent = amount(back.spent, decimals),
            remaining = amount(back.remaining, decimals),
            stacked = stacked,
            // The same locks: a category is closed whole, and the side of it that comes
            // back is as settled as the side that goes out.
            plannedLocked = row.isPlanningClosed,
            spentLocked = row.isSpendingClosed,
        )
    }
}

/** A category as every row of this block names one: its icon and then its name. */
@Composable
private fun categoryName(category: Category): String =
    "${category.icon} ${localizedName(category.name, category.nameKey)}"

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
    /** Whether the plan of this row is settled, and whether its spending is. */
    plannedLocked: Boolean = false,
    spentLocked: Boolean = false,
) {
    val plannedMark = stringResource(R.string.action_close_planning).takeIf { plannedLocked }
    val spentMark = stringResource(R.string.action_close_spending).takeIf { spentLocked }
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
            if (planned.isNotBlank()) {
                FigureLine(R.string.budget_planned, planned, weight, plannedMark)
            }
            if (spent.isNotBlank()) FigureLine(R.string.budget_spent, spent, weight, spentMark)
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
        NumberCell(planned, NUMBER_WEIGHT, weight, plannedMark)
        NumberCell(spent, NUMBER_WEIGHT, weight, spentMark)
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

