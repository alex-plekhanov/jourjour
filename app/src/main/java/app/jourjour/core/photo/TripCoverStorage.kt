package app.jourjour.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cover photo of a trip: one picture per trip, shown behind the trip's row in the list
 * and behind the top of its entries screen.
 *
 * Covers live apart from the receipts so that neither the "delete the trip's receipt
 * photos" sweep nor the backup rules, which keep receipts out of cloud copies, touch them.
 * A cover is small and worth carrying to a new phone, so it is backed up like the rest of
 * the app's data — which is also why it is encoded harder than a receipt is. See
 * [COVER_JPEG_QUALITY].
 */
@Singleton
class TripCoverStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PhotoDirectory(context, DIRECTORY) {

    /**
     * Copies a picked photo into our own folder, scaled and re-encoded, and answers with
     * the name it was stored under, or null if the picture could not be read.
     *
     * Nothing of the original is kept: the picker hands out a link whose permission ends
     * with the screen that asked for it.
     */
    suspend fun importFrom(source: Uri): String? = withContext(Dispatchers.IO) {
        val bitmap = decode(source) ?: return@withContext null
        val cover = fitWidth(bitmap)

        val name = newName()
        val written = runCatching {
            file(name).outputStream().use {
                cover.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, it)
            }
        }.isSuccess
        cover.recycle()

        if (written) {
            name
        } else {
            file(name).delete()
            null
        }
    }

    private fun decode(source: Uri): Bitmap? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val orientation = orientationOf { requireNotNull(resolver.openInputStream(source)) }
        // A picture stored on its side reports its sides the other way round, and it is the
        // width after turning it upright that decides how much detail is worth decoding.
        val width = if (swapsSides(orientation)) bounds.outHeight else bounds.outWidth

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(width, WIDTH) }
        val decoded = runCatching {
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null

        return rotate(decoded, orientation)
    }

    /**
     * Brings a cover to [WIDTH] across, keeping its proportions, and centre-crops anything
     * taller than a square.
     *
     * Width is what matters: a cover is always drawn across the full width of the screen,
     * so scaling by the longest side would leave an upright photo too coarse for it.
     * Height past a square is height nothing shows — both places that draw a cover give it
     * a band far wider than it is tall — so keeping it would only cost storage. A picture
     * narrower than [WIDTH] is left as it is: enlarging it adds weight, not detail.
     */
    private fun fitWidth(bitmap: Bitmap): Bitmap {
        val fitted = if (bitmap.width > WIDTH) {
            scaled(bitmap, WIDTH.toFloat(), bitmap.height * WIDTH.toFloat() / bitmap.width)
        } else {
            bitmap
        }

        val maxHeight = fitted.width
        if (fitted.height <= maxHeight) return fitted

        val top = (fitted.height - maxHeight) / 2
        return Bitmap.createBitmap(fitted, 0, top, fitted.width, maxHeight)
            .also { if (it != fitted) fitted.recycle() }
    }

    companion object {
        /** How wide a stored cover is, in pixels. */
        const val WIDTH = 1000

        private const val DIRECTORY = "trip_covers"
    }
}
