package app.jourjour.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jourjour.BuildConfig
import app.jourjour.R
import app.jourjour.ui.theme.JourJourTheme

/**
 * How much of the shorter side of the screen the icon takes.
 *
 * Half, not more: the mark is the first thing on the screen and does not have to be the
 * largest. What a person came here to read is under it, and on a short screen every point
 * the icon gives up is a line of that they can see without scrolling.
 */
private const val ICON_FRACTION = 1f / 2f

/**
 * A launcher shows the middle 72dp of an adaptive icon's 108dp canvas, and the mark is
 * drawn to fit that; painting the whole canvas into the circle would show it smaller than
 * the launcher does, so the two layers are scaled up by the same ratio.
 */
private const val ICON_CANVAS_SCALE = 108f / 72f

/**
 * What the app is, whose it is, and where it lives.
 *
 * The screen is in two parts. The head of it is centred — the icon, what the app is, and
 * the two lines of small print that belong to the icon rather than to the reader: a title
 * page. Everything under it is prose and a list, and prose is read from a left edge, so
 * from there down the text is ranged left like any other page of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Two thirds of whichever side is shorter: the width upright, the height
            // on its side, where two thirds of the width would not fit on the screen.
            val diameter = minOf(maxWidth, maxHeight) * ICON_FRACTION
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(diameter),
                )
                Paragraph(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    align = TextAlign.Center,
                )
                // Tight against each other rather than a whole gap apart: two lines of the
                // same small print, not two things to be read one after the other.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.about_copyright),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FeatureList()
                Paragraph(stringResource(R.string.about_free))
                Paragraph(stringResource(R.string.about_open_source))
                Column {
                    Paragraph(stringResource(R.string.about_feedback))
                    GitHubLink()
                }
            }
        }
    }
}

/**
 * What the app is for, one line apiece, under a line that says the lines are a list of
 * that rather than more of the sentence above them.
 *
 * That line is set in the same face as the list under it and ends in a colon, because it
 * is the opening of the sentence the list finishes — not a section heading. The lines are
 * a string array so that the next thing the app learns to do is one line in each language
 * and nothing else.
 */
@Composable
private fun FeatureList() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.about_features_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        stringArrayResource(R.array.about_features).forEach { feature ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = BULLET, style = MaterialTheme.typography.bodyLarge)
                Text(text = feature, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Not a hyphen: a list of things is not a list of dashes. */
private const val BULLET = "•"

/** The launcher icon as the launcher shows it: the two layers under a round mask. */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.about_icon)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .semantics { contentDescription = description },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(ICON_CANVAS_SCALE),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(ICON_CANVAS_SCALE),
        )
    }
}

/** Ranged left unless it belongs to the title page at the top, which is centred. */
@Composable
private fun Paragraph(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = style,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The one address on the screen, shown without its scheme and opened with it. */
@Composable
private fun GitHubLink() {
    val url = stringResource(R.string.about_github_url)
    val text = buildAnnotatedString {
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        ) {
            append(stringResource(R.string.about_github_label))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutScreenPreview() {
    JourJourTheme {
        AboutScreen(onBack = {})
    }
}
