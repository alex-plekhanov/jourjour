package app.jourjour.ui.entry

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.jourjour.R
import app.jourjour.core.photo.PhotoStorage

/** How far in a receipt may be taken: enough to read a line of small print. */
private const val MAX_SCALE = 6f

/** What a double tap goes to, and comes back from. */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * A receipt, filling the screen, to be zoomed into and moved about.
 *
 * The preview on the form shows the whole of a photo but shows it small, which is enough
 * to tell one receipt from another and no use at all for reading a total off it. This is
 * where it is read: pinch or double tap to go in, drag to move, and the picture is held to
 * its own edges so that it cannot be pushed out of sight.
 *
 * It is decoded afresh at [VIEWER_MAX_SIDE] rather than reusing the preview's bitmap:
 * magnifying a picture already shrunk for a 240dp band would only magnify what was thrown
 * away with it.
 */
@Composable
fun PhotoViewer(photoFile: String, photoStorage: PhotoStorage, onClose: () -> Unit) {
    var bitmap by remember(photoFile) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoFile) {
        bitmap = photoStorage.load(photoFile, VIEWER_MAX_SIDE)
    }

    Dialog(
        onDismissRequest = onClose,
        // Left to fill the screen rather than sit in a dialog's usual margins: a receipt is
        // read by covering as much of the glass with it as there is.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA)),
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var size by remember { mutableStateOf(IntSize.Zero) }

            // How far the picture may be moved at this magnification: past this it would
            // leave the frame and there would be nothing under the finger.
            fun held(at: Offset, magnified: Float): Offset {
                val maxX = size.width * (magnified - 1) / 2f
                val maxY = size.height * (magnified - 1) / 2f
                return Offset(at.x.coerceIn(-maxX, maxX), at.y.coerceIn(-maxY, maxY))
            }

            val photo = bitmap
            if (photo == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = stringResource(R.string.field_photo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tap ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = DOUBLE_TAP_SCALE
                                        // Towards what was tapped, so that a double tap on
                                        // the total brings the total up rather than the
                                        // middle of the receipt.
                                        val centre = Offset(size.width / 2f, size.height / 2f)
                                        offset = held((centre - tap) * (scale - 1f), scale)
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                offset = held(offset + pan, scale)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close_photo),
                )
            }
        }
    }
}

/** Dark enough to leave nothing of the form showing, light enough not to read as a crash. */
private const val BACKDROP_ALPHA = 0.94f

/**
 * How large the picture is decoded for reading. A receipt is stored no larger than the
 * photo settings allow, so this is a ceiling rather than a size anything is scaled up to.
 */
private const val VIEWER_MAX_SIDE = 2048
