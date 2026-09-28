package app.jourjour.ui.trip

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.jourjour.core.photo.TripCoverStorage

/** How far the visible end of a cover is faded in: the rest of it fades away to nothing. */
const val COVER_ALPHA = 0.6f

/**
 * The cover photo of a trip, drawn behind whatever sits on top of it and fading out along
 * [fade].
 *
 * The fade works on the picture's own alpha instead of being laid over it as a veil of the
 * background colour, because a veil would have to know the colour beneath it, and the same
 * cover is drawn over a list row and over the top of the entries screen. Rendering goes
 * through an offscreen layer so that the fade eats into the picture alone and not into
 * everything already drawn under it.
 */
@Composable
fun TripCover(
    photoFile: String,
    coverStorage: TripCoverStorage,
    fade: Brush,
    maxSide: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(photoFile) { mutableStateOf<Bitmap?>(null) }
    val arrivals by coverStorage.arrivals.collectAsState()

    // A cover that was not on disk when the row first looked for it is looked for again once
    // covers arrive: an imported dump writes the trip rows before the pictures they name, so
    // a row already on screen has nothing to draw. Only a missing cover is fetched again —
    // a stored name belongs to one picture for good, so what is already drawn cannot be out
    // of date.
    LaunchedEffect(photoFile, arrivals) {
        if (bitmap == null) bitmap = coverStorage.load(photoFile, maxSide)
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            // Decoration behind the content: a screen reader has nothing to say about it.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = fade, blendMode = BlendMode.DstIn)
                },
        )
    }
}

/** Only the alpha of these colours matters: the fade multiplies what the picture already has. */
fun fadeColors(): List<Color> = listOf(Color.Black.copy(alpha = COVER_ALPHA), Color.Transparent)
