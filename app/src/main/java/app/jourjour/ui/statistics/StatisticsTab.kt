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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import app.jourjour.core.stats.BaseTotals
import app.jourjour.core.stats.CategoryTotals
import app.jourjour.core.stats.CurrencyReport
import app.jourjour.core.text.localizedName
import app.jourjour.ui.common.EmptyList

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

/** Rates for some currency are missing, and the trip form is where they are entered. */
@Composable
private fun MissingRatesWarning(codes: List<String>, onEditTrip: () -> Unit) {
    val linkWord = stringResource(R.string.link_trip_form)
    WhyNoTotal(
        sentence = pluralStringResource(
            R.plurals.warning_base_totals_missing,
            codes.size,
            codes.joinToString(", "),
            linkWord,
        ),
        linkWord = linkWord,
        // Red: a trip that carries some rates and not others is half done, and the
        // half that is missing is holding up a figure somebody wanted.
        colour = MaterialTheme.colorScheme.error,
        onFollow = onEditTrip,
    )
}

/**
 * Nothing says what to convert to, and the settings are where that is said.
 *
 * Shown rather than left silent because the absence of a section explains nothing: a
 * person looking for the total of their trip has to be told why there is not one, and
 * whose answer it is.
 */
@Composable
private fun NoBaseCurrencyWarning(onSettings: () -> Unit) {
    val linkWord = stringResource(R.string.link_settings)
    WhyNoTotal(
        sentence = stringResource(R.string.warning_base_currency_missing, linkWord),
        linkWord = linkWord,
        // Muted, not red: nothing is wrong. A base currency is an option nobody has
        // taken up, and a line about it should say so once rather than scold.
        colour = MaterialTheme.colorScheme.onSurfaceVariant,
        onFollow = onSettings,
    )
}

/**
 * Why there is no single figure, and where to go about it.
 *
 * The way there is a link inside the sentence rather than a button under it: it is the
 * tail of an explanation, not an action the screen is offering.
 */
@Composable
private fun WhyNoTotal(
    sentence: String,
    linkWord: String,
    colour: Color,
    onFollow: () -> Unit,
) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    )
    val text = remember(sentence, linkWord, linkStyle, onFollow) {
        buildAnnotatedString {
            val at = sentence.lastIndexOf(linkWord)
            if (at < 0) {
                append(sentence)
                return@buildAnnotatedString
            }
            append(sentence.substring(0, at))
            withLink(LinkAnnotation.Clickable(WHERE_TO_GO, linkStyle) { onFollow() }) {
                append(linkWord)
            }
            append(sentence.substring(at + linkWord.length))
        }
    }

    // On the plain background rather than in a tinted band: a band here would sit directly
    // against a section heading and read as one with it. The colour is the one the trip
    // form gives the same class of problem.
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colour,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
    )
}

/** The tag a link annotation carries; nothing reads it, but one is required. */
private const val WHERE_TO_GO = "where-to-go"

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
        Title(R.string.stats_quantity, QUANTITY_WEIGHT)
        Title(R.string.section_income, NUMBER_WEIGHT)
        Title(R.string.section_expense, NUMBER_WEIGHT)
    }
}

@Composable
private fun RowScope.Title(@StringRes titleRes: Int, width: Float) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        autoSize = fitsTheColumn(MaterialTheme.typography.labelSmall.fontSize),
        modifier = Modifier.weight(width),
    )
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
        Number(
            text = totals.quantity?.let(MoneyFormat::formatQuantity).orEmpty(),
            weight = FontWeight.Normal,
            width = QUANTITY_WEIGHT,
        )
        Number(text = amount(totals.income, decimals), weight = FontWeight.Normal)
        Number(text = amount(totals.expense, decimals), weight = FontWeight.Normal)
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
        Number(text = "", weight = FontWeight.Bold, width = QUANTITY_WEIGHT)
        Number(text = amount(report.netIncome, report.currency.decimals), weight = FontWeight.Bold)
        Number(text = amount(report.netExpense, report.currency.decimals), weight = FontWeight.Bold)
    }
}

@Composable
private fun RowScope.Number(text: String, weight: FontWeight, width: Float = NUMBER_WEIGHT) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = weight,
        textAlign = TextAlign.End,
        maxLines = 1,
        autoSize = fitsTheColumn(MaterialTheme.typography.bodyMedium.fontSize),
        modifier = Modifier.weight(width),
    )
}

/**
 * Lets a cell shrink rather than break.
 *
 * The columns are shares of the width, so a larger system font does not widen them: the
 * text inside is what has to give. Broken across two lines a heading loses a letter to the
 * next one and a sum stops being a number at all, and a table where one row is taller than
 * the rest is worse than a table set a point smaller.
 *
 * It only ever shrinks — the ceiling is the size the style asks for, which is the size
 * every cell keeps at the font the phone is normally set to.
 */
@Composable
private fun fitsTheColumn(ceiling: TextUnit): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = SMALLEST_READABLE, maxFontSize = ceiling)

/** How small a figure may be set before it stops being worth setting at all. */
private val SMALLEST_READABLE = 8.sp

/** Zero is left blank: a column of noughts reads as noise, not as information. */
private fun amount(money: Money?, decimals: Int): String =
    if (money == null || money.isZero) "" else MoneyFormat.formatAmount(money, decimals)
