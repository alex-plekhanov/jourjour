package app.jourjour.ui.entry

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jourjour.R
import app.jourjour.core.photo.PhotoStorage
import app.jourjour.ui.common.PrimaryButton
import app.jourjour.ui.common.SecondaryButton

/**
 * The shot just taken, shown the way it is about to be stored, and the two arrows that
 * stand it upright.
 *
 * A receipt is photographed lying on a table, straight down. That is the one position in
 * which a phone cannot tell which way up it is being held — gravity runs along the lens
 * rather than across it — so the orientation the camera writes into the shot is a guess,
 * and it comes out on its side or upside down as often as not. The camera's own
 * confirmation screen is no help: it shows every shot the same way round whatever the tag
 * says, so there is nothing in it to tell a good guess from a bad one.
 *
 * Hence this step, which shows the photo through the very code that will draw it in the
 * form afterwards — what is on the screen here is what the entry will carry. A turn asked
 * for is carried as a count of quarter turns rather than written into the file, so that it
 * reaches the photo inside the one encode that shrinks it; see [PhotoStorage.compress].
 *
 * The picture is turned by the layer it is drawn in rather than by making a turned copy of
 * it: a receipt decoded for a phone screen is a few megabytes of bitmap, and tapping an
 * arrow four times round should not cost four of those.
 */
@Composable
fun PhotoReview(
    photoFile: String,
    photoStorage: PhotoStorage,
    onKeep: (Int) -> Unit,
    onRetake: () -> Unit,
) {
    var bitmap by remember(photoFile) { mutableStateOf<Bitmap?>(null) }
    var turns by remember(photoFile) { mutableIntStateOf(0) }

    LaunchedEffect(photoFile) {
        bitmap = photoStorage.load(photoFile, REVIEW_MAX_SIDE)
    }

    Dialog(
        // Backing out keeps the shot as it stands rather than throwing it away. The photo
        // has already been taken, and losing it to a stray tap would be worse than keeping
        // one that is the wrong way up — which the form it lands on can still put right by
        // retaking it.
        onDismissRequest = { onKeep(turns) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Everything here is a decision about the photo; a tap beside it is not one.
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = REVIEW_BACKDROP_ALPHA))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.photo_review_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val photo = bitmap
                if (photo == null) {
                    CircularProgressIndicator()
                } else {
                    // On its side the picture is handed the frame with its sides swapped,
                    // and the turn then brings it back inside the frame as it stands.
                    // Fitting it to the frame first and turning it afterwards would push
                    // it off both edges.
                    val sideways = turns % 2 != 0
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = stringResource(R.string.field_photo),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(
                                    width = if (sideways) maxHeight else maxWidth,
                                    height = if (sideways) maxWidth else maxHeight,
                                )
                                .graphicsLayer { rotationZ = turns * 90f },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TurnButton(
                    iconRes = R.drawable.ic_rotate_left,
                    labelRes = R.string.action_rotate_left,
                    onClick = { turns = (turns + 3) % 4 },
                )
                TurnButton(
                    iconRes = R.drawable.ic_rotate_right,
                    labelRes = R.string.action_rotate_right,
                    onClick = { turns = (turns + 1) % 4 },
                )
                Box(modifier = Modifier.weight(1f))
                SecondaryButton(
                    onClick = onRetake,
                    text = stringResource(R.string.action_retake_photo),
                )
                PrimaryButton(
                    onClick = { onKeep(turns) },
                    text = stringResource(R.string.action_keep_photo),
                )
            }
        }
    }
}

/** One of the two arrows: white on the backdrop, like the button that closes the viewer. */
@Composable
private fun TurnButton(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(labelRes),
            modifier = Modifier.size(28.dp),
        )
    }
}

/** As dark as the viewer's backdrop: the same photo, over the same form. */
private const val REVIEW_BACKDROP_ALPHA = 0.94f

/**
 * Large enough to tell which way up a receipt is and to read the shop off it, no larger:
 * nothing here is magnified, and a photo off a camera may be several thousand pixels wide.
 */
private const val REVIEW_MAX_SIDE = 1440
