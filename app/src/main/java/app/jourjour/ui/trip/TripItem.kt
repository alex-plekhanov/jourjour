package app.jourjour.ui.trip

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.jourjour.R
import app.jourjour.core.photo.TripCoverStorage
import app.jourjour.data.entity.Trip
import app.jourjour.ui.common.RowIconButton
import app.jourjour.ui.common.rememberDateFormatter
import app.jourjour.ui.common.withHalo
import app.jourjour.ui.theme.JourJourTheme
import java.time.LocalDate
import java.time.format.FormatStyle

/** Tall enough for the cover to read as a picture rather than a stripe of colour. */
private val CARD_MIN_HEIGHT = 120.dp

/**
 * A card is a wide band of a dimmed background, so it asks little of the picture; decoding
 * a cover at its full width for every visible card would cost far more memory than it is
 * worth here.
 */
private const val CARD_COVER_MAX_SIDE = 480

/** The dates sit under the name and should not compete with it for attention. */
private const val SUBTITLE_ALPHA = 0.75f

@Composable
fun TripItem(
    trip: Trip,
    coverStorage: TripCoverStorage,
    onTripClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Neutral, and the same whether or not there is a cover: a tinted card shows through a
    // faded photograph and turns it the colour of the tint, which no photograph should be.
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    // The halo is the card's own colour: over a cover it rims the letters, off one it is not there.
    val halo = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.clickable(onClick = onTripClick)) {
            trip.photoFile?.let { photoFile ->
                TripCover(
                    photoFile = photoFile,
                    coverStorage = coverStorage,
                    fade = Brush.horizontalGradient(fadeColors()),
                    maxSide = CARD_COVER_MAX_SIDE,
                    // Sized by the card rather than the other way round: a cover must never
                    // decide how tall a trip is.
                    modifier = Modifier.matchParentSize(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CARD_MIN_HEIGHT)
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The name takes what the mark beside it leaves, and wraps within
                        // that — two lines at most, so a long name cannot stretch the card
                        // — rather than taking the row and squeezing the mark to a sliver.
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).withHalo(halo),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // An ongoing trip is marked: dates alone make people work it out by eye.
                        if (LocalDate.now() in trip.startDate..trip.endDate) {
                            Text(
                                text = stringResource(R.string.trip_ongoing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "${trip.startDate.format(dateFormatter)} — ${trip.endDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium).withHalo(halo),
                        // Taken from the card's own content colour, which is what a cover
                        // photograph behind the text also lightens or darkens against.
                        color = LocalContentColor.current.copy(alpha = SUBTITLE_ALPHA),
                    )
                }
                RowIconButton(onClick = onEditClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.action_edit),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripItemPreview() {
    JourJourTheme {
        TripItem(
            trip = Trip(
                id = 1,
                name = "Japan",
                startDate = LocalDate.of(2026, 4, 3),
                endDate = LocalDate.of(2026, 4, 17),
            ),
            coverStorage = TripCoverStorage(LocalContext.current),
            onTripClick = {},
            onEditClick = {},
        )
    }
}
