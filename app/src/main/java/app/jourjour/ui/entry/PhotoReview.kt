package app.jourjour.ui.entry

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jourjour.R
import app.jourjour.core.photo.PhotoStorage

/**
 * The shot just taken, shown as the photo the entry would keep, before it keeps it.
 *
 * Two things are being asked here, and the one screen answers both.
 *
 * Which way up it goes. A receipt is photographed lying on a table, straight down, which is
 * the one position in which a phone cannot work out which way up it is being held — gravity
 * runs along the lens rather than across it — so the orientation the camera records is a
 * guess, and the receipt arrives on its side or upside down as often as not. The camera's
 * own confirmation screen shows every shot the same way round whatever it recorded, so
 * nothing there gives the guess away. The two arrows under the photo turn it a quarter at a
 * time.
 *
 * And whether it is still readable. Receipts are shrunk hard, to keep a hundred of them
 * from eating half the storage, and a total in small print is exactly what that can cost.
 * So what is shown here is not the shot the camera wrote: it is the rendering that would be
 * stored, read back off the disk, and it can be pinched into as far as the viewer allows,
 * at the size the viewer will show it at. A receipt that cannot be read here will not read
 * any better in a fortnight, and Retake is still on the screen.
 *
 * Every turn re-renders from the shot set aside as the camera wrote it — see
 * [PhotoStorage.setAside] — so turning it costs no quality at all: four turns round is one
 * encode, not four over each other.
 *
 * Three answers, as the camera's own screen has three: keep it, take it again, or turn it
 * down. Turning it down is what backing out does too, and it leaves the entry with whatever
 * photo it already had — a shot refused here never becomes the entry's photo, so there is
 * nothing to undo.
 *
 * All five controls are marks rather than words, which is what lets them stand on one line
 * with the answers where a hand expects them, and the line above says in words what the
 * screen is for.
 */
@Composable
fun PhotoReview(
    photoFile: String,
    photoStorage: PhotoStorage,
    onRender: suspend (String, Int) -> Unit,
    onKeep: () -> Unit,
    onRetake: () -> Unit,
    onDiscard: () -> Unit,
) {
    var bitmap by remember(photoFile) { mutableStateOf<Bitmap?>(null) }
    var turns by remember(photoFile) { mutableIntStateOf(0) }
    var rendering by remember(photoFile) { mutableStateOf(true) }

    // Both the first showing and every turn come through here: a turn is a new rendering,
    // and there is nothing to show until it has been written and read back.
    LaunchedEffect(photoFile, turns) {
        rendering = true
        onRender(photoFile, turns)
        bitmap = photoStorage.load(photoFile, ZOOMED_PHOTO_MAX_SIDE)
        rendering = false
    }

    Dialog(
        // Backing out turns the shot down, as it does on the camera's own screen before
        // this one: a shot is kept by saying so. Nothing is lost that was there before —
        // the entry goes on carrying whatever photo it already had.
        onDismissRequest = onDiscard,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Everything here is a decision about the photo; a tap beside it is not one.
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA))
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
                bitmap?.let { photo ->
                    ZoomablePhoto(
                        bitmap = photo,
                        contentDescription = stringResource(R.string.field_photo),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Over the last rendering rather than instead of it: a turn is a short wait
                // and the screen going black for it would read as the photo being lost.
                if (rendering) {
                    CircularProgressIndicator()
                } else if (bitmap == null) {
                    // Nothing came back from the disk at all. Keep is out of reach below,
                    // so what is left is to take the shot again.
                    Text(
                        text = stringResource(R.string.error_photo_unreadable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Marks rather than words, all five of them. Three answers and two arrows do
            // not fit a phone in one line as words, and a row that wrapped put the one
            // thing a person is reaching for on a line of its own below the rest.
            //
            // Two groups, pushed apart: what changes the picture on the left, what settles
            // it on the right, with Keep at the end of the row in the filled colours every
            // other screen gives the action it exists for.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MarkButton(
                    painter = painterResource(R.drawable.ic_rotate_left),
                    labelRes = R.string.action_rotate_left,
                    onClick = { turns = (turns + 3) % 4 },
                )
                MarkButton(
                    painter = painterResource(R.drawable.ic_rotate_right),
                    labelRes = R.string.action_rotate_right,
                    onClick = { turns = (turns + 1) % 4 },
                )

                Box(modifier = Modifier.weight(1f))

                // Turning the shot down, the way the camera's own screen allows: what the
                // entry had before this shot it still has.
                MarkButton(
                    painter = rememberVectorPainter(Icons.Default.Close),
                    labelRes = R.string.action_cancel,
                    onClick = onDiscard,
                )
                MarkButton(
                    painter = rememberVectorPainter(Icons.Default.Refresh),
                    labelRes = R.string.action_retake_photo,
                    onClick = onRetake,
                )
                FilledIconButton(
                    onClick = onKeep,
                    // Out of reach while a rendering is being made: what the entry keeps is
                    // the file on the disk, and accepting one that is still being written
                    // would keep the turn before the last one.
                    enabled = !rendering && bitmap != null,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_keep_photo),
                    )
                }
            }
        }
    }
}

/**
 * One of the marks on the row: white on the backdrop, like the button that closes the
 * viewer. The one filled mark is Keep, and it is spelt out where it stands.
 */
@Composable
private fun MarkButton(painter: Painter, @StringRes labelRes: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(
            painter = painter,
            contentDescription = stringResource(labelRes),
            modifier = Modifier.size(26.dp),
        )
    }
}
