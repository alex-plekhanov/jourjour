package app.jourjour.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.jourjour.R

/**
 * Rates for some currency are missing, and the trip form is where they are entered.
 *
 * The same line serves the statistics and the budget: it is the same missing rate stopping
 * the same arithmetic, and a person who has read it once should not be told twice in two
 * different wordings.
 */
@Composable
fun MissingRatesWarning(codes: List<String>, onEditTrip: () -> Unit) {
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
fun NoBaseCurrencyWarning(onSettings: () -> Unit) {
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
 * Why a figure cannot be given, and where to go about it.
 *
 * The way there is a link inside the sentence rather than a button under it: it is the
 * tail of an explanation, not an action the screen is offering.
 */
@Composable
fun WhyNoTotal(
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
