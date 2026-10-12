package app.jourjour.ui.entry

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** How far in a receipt may be taken: enough to read a line of small print. */
private const val MAX_SCALE = 6f

/** What a double tap goes to, and comes back from. */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * How large a photo is decoded wherever it can be magnified.
 *
 * A receipt is stored no larger than the photo settings allow, so for the sizes anybody
 * shrinks to this is a ceiling rather than a size the photo is scaled up to — and the one
 * place it does bite, a photo kept larger than this, it bites the same whether the photo is
 * being accepted or read back later. Which is the point: the shot being looked at before it
 * is kept is shown at the size it will be read at afterwards.
 */
internal const val ZOOMED_PHOTO_MAX_SIDE = 2048

/**
 * A photo that can be gone into: pinch or double tap to magnify, drag to move about.
 *
 * The picture is held to its own edges, so that it cannot be pushed out of sight and leave
 * nothing under the finger. Magnification starts afresh on a new bitmap: a receipt turned
 * or retaken is a different picture, and carrying a corner of the last one over to it would
 * land the person somewhere they never looked.
 */
@Composable
fun ZoomablePhoto(bitmap: Bitmap, contentDescription: String, modifier: Modifier = Modifier) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // How far the picture may be moved at this magnification: past this it would leave the
    // frame and there would be nothing under the finger.
    fun held(at: Offset, magnified: Float): Offset {
        val maxX = size.width * (magnified - 1) / 2f
        val maxY = size.height * (magnified - 1) / 2f
        return Offset(at.x.coerceIn(-maxX, maxX), at.y.coerceIn(-maxY, maxY))
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                            // Towards what was tapped, so that a double tap on the total
                            // brings the total up rather than the middle of the receipt.
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            offset = held((centre - tap) * (scale - 1f), scale)
                        }
                    },
                )
            }
            .pointerInput(bitmap) {
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
