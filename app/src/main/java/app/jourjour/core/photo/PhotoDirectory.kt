package app.jourjour.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A folder of photos inside the app's own storage.
 *
 * No media scanner looks in there, so nothing kept here turns up in the gallery. The price
 * is that the photos go when the app does.
 *
 * A record stores only the file name. A `content://` link would be the wrong thing to
 * keep: the permission behind it can be revoked, while a name inside our own folder always
 * resolves.
 */
abstract class PhotoDirectory(
    private val context: Context,
    private val directoryName: String,
) {

    protected val directory: File
        get() = File(context.filesDir, directoryName).apply { mkdirs() }

    fun file(name: String): File = File(directory, name)

    fun exists(name: String?): Boolean = name != null && file(name).exists()

    /** Reserves a name for a photo that is about to be written. */
    fun newName(): String = "${UUID.randomUUID()}.jpg"

    private val _arrivals = MutableStateFlow(0)

    /**
     * Counts the times photos have been put into the folder behind the back of whatever is
     * drawing them. Something showing a photo can take this as part of what it remembers
     * by, and look again for a file that was not there the first time.
     */
    val arrivals: StateFlow<Int> = _arrivals.asStateFlow()

    /** Announces photos written straight into the folder rather than through this class. */
    fun noteArrivals() = _arrivals.update { it + 1 }

    suspend fun delete(name: String?) = withContext(Dispatchers.IO) {
        if (name != null) file(name).delete()
        Unit
    }

    suspend fun deleteAll(names: Collection<String>) = withContext(Dispatchers.IO) {
        names.forEach { file(it).delete() }
    }

    /**
     * Removes photos no record points at any more. A form abandoned after a photo was
     * added, or a crash between writing the file and saving the record, both leave such
     * files behind.
     */
    suspend fun deleteOrphans(referenced: Set<String>) = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.filter { it.isFile && it.name !in referenced }
            ?.forEach { it.delete() }
        Unit
    }

    /**
     * Reads a photo for showing, no larger than [maxSide] and already turned the right way
     * up. Without applying EXIF here a photo that was not re-encoded would appear on its
     * side: a plain decode ignores the tag.
     */
    suspend fun load(name: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(name)
        if (file.exists()) decodeFile(file, maxSide) else null
    }
}

/**
 * What a receipt is encoded at. A receipt is kept in order to be read back — a line of
 * figures, often in small print — so it is not the place to save bytes.
 */
internal const val JPEG_QUALITY = 75

/**
 * What a cover is encoded at, which is harder than a receipt.
 *
 * A cover is never examined: it is drawn as a wide band behind a dimming gradient with the
 * trip's name over it, which hides what compression does to a photograph. And it is the
 * only photo in the backup — receipts are excluded, so the covers are what spends the
 * 25 MB Auto Backup allows before it silently stops backing the app up at all.
 */
internal const val COVER_JPEG_QUALITY = 60

internal fun decodeFile(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(longest, maxSide) }
    val decoded = runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
        ?: return null

    val rotated = rotate(decoded, orientationOf { file.inputStream() })
    return scaleToLongSide(rotated, maxSide)
}

/**
 * Decoding at a power-of-two step is what BitmapFactory can do cheaply; the exact size is
 * reached afterwards by scaling what is left.
 */
internal fun sampleSize(dimension: Int, target: Int): Int {
    var sample = 1
    while (dimension / (sample * 2) >= target) sample *= 2
    return sample
}

internal fun orientationOf(open: () -> InputStream): Int = runCatching {
    open().use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/** Whether the orientation turns the picture on its side, swapping width and height. */
internal fun swapsSides(orientation: Int): Boolean = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
    orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
    orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
    orientation == ExifInterface.ORIENTATION_TRANSVERSE

internal fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it != bitmap) bitmap.recycle() }
}

internal fun scaleToLongSide(bitmap: Bitmap, maxSide: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxSide) return bitmap

    val ratio = maxSide.toFloat() / longest
    return scaled(bitmap, (bitmap.width * ratio), (bitmap.height * ratio))
}

internal fun scaled(bitmap: Bitmap, width: Float, height: Float): Bitmap {
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        width.roundToInt().coerceAtLeast(1),
        height.roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled != bitmap) bitmap.recycle()
    return scaled
}
