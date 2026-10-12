package app.jourjour.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipt photos on disk.
 *
 * The camera writes into this folder through a link handed to it for one file at a time,
 * so a shot never reaches the gallery — sharing a photo out is the way to keep one.
 *
 * A shot does not become the entry's photo as it lands. It is first set aside as the camera
 * wrote it, and what the entry would keep is rendered from it under the photo's own name —
 * turned and shrunk — for the person to look at and accept. Every turn is rendered from the
 * shot that was set aside rather than from the last rendering, so turning a receipt four
 * times round costs it no more than turning it once.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PhotoDirectory(context, DIRECTORY) {

    /**
     * A link the camera can write through. Granted per file and only while the capture
     * runs, so nothing else of ours becomes reachable.
     */
    fun uri(name: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file(name),
    )

    /**
     * Puts the shot the camera wrote aside, under a name of its own.
     *
     * Called before the first rendering and doing nothing afterwards: what is set aside is
     * the camera's own pixels, and a second call once a rendering stands under the photo's
     * name would replace them with the rendering.
     */
    suspend fun setAside(name: String) = withContext(Dispatchers.IO) {
        val shot = file(name)
        val aside = original(name)
        if (aside.exists() || !shot.exists()) return@withContext
        if (!shot.renameTo(aside)) shot.copyTo(aside, overwrite = true)
        Unit
    }

    /**
     * Renders what the entry would keep, shrunk: the shot set aside, turned by
     * [quarterTurns] and encoded at [quality] no larger than [maxSide].
     *
     * A phone camera writes several megabytes; a receipt stays readable at a fraction of
     * that, and a hundred of them should not eat half a gigabyte of the app's storage. How
     * far to shrink and how hard to encode are both settings: what a receipt has to stay
     * readable for is the person keeping it, not this class.
     *
     * The rotation the camera recorded in EXIF is baked into the pixels here, because
     * re-encoding drops the tag that carried it; the turn asked for by hand goes in with
     * it, so standing a receipt upright costs it nothing beyond this one encode.
     */
    suspend fun render(
        name: String,
        maxSide: Int,
        quality: Int,
        quarterTurns: Int = 0,
    ) = withContext(Dispatchers.IO) {
        val source = original(name)
        if (!source.exists()) return@withContext

        val decoded = decodeFile(source, maxSide) ?: return@withContext
        val upright = turned(decoded, quarterTurns)
        try {
            write(name) { out -> upright.compress(Bitmap.CompressFormat.JPEG, quality, out) }
        } finally {
            upright.recycle()
        }
    }

    /**
     * Renders what the entry would keep where nothing is being shrunk: the shot set aside,
     * copied across with its orientation tag turned by [quarterTurns].
     *
     * Not one pixel is touched. Shrinking is a setting, and somebody who turned it off did
     * so to keep every pixel the camera wrote; re-encoding a photo to stand it upright
     * would take back exactly what they asked for. Everything that draws a photo reads that
     * tag, so it stands upright all the same.
     *
     * The tag is turned from the one the camera wrote, the copy being made afresh each
     * time, so this says where the photo should end up rather than how far to turn it from
     * wherever it is now.
     */
    suspend fun renderUnshrunk(name: String, quarterTurns: Int = 0) = withContext(Dispatchers.IO) {
        val source = original(name)
        if (!source.exists()) return@withContext

        val turns = wholeTurns(quarterTurns)
        write(name) { out ->
            source.inputStream().use { shot -> shot.copyTo(out) }
            true
        }
        if (turns == 0) return@withContext

        // A tag that cannot be written leaves the photo as the camera had it: lying on its
        // side is worth less than losing the shot.
        runCatching {
            ExifInterface(file(name)).apply {
                rotate(turns * 90)
                saveAttributes()
            }
        }
        Unit
    }

    /**
     * Drops the shot set aside, the rendering under the photo's name being what the entry
     * keeps from here on.
     */
    suspend fun dropAside(name: String) = withContext(Dispatchers.IO) {
        original(name).delete()
        Unit
    }

    /** Throws away a shot that was not taken on, and whatever was rendered from it. */
    suspend fun discard(name: String) = withContext(Dispatchers.IO) {
        original(name).delete()
        file(name).delete()
        Unit
    }

    /**
     * Writes the file the photo is known by, through a draft swapped in whole: a write that
     * fails halfway would otherwise leave a truncated JPEG under the name the entry is
     * about to keep. A draft left behind by a crash is a file no record points at, and goes
     * with the other orphans on the next start.
     */
    private fun write(name: String, body: (OutputStream) -> Boolean) {
        val draft = File(directory, "$name.part")
        val written = runCatching { draft.outputStream().use(body) }.getOrDefault(false)
        if (!written || !draft.renameTo(file(name))) draft.delete()
    }

    /**
     * The shot as the camera wrote it, while it is being looked at. No record points at
     * this name, so one left behind by a crash goes with the orphans on the next start.
     */
    private fun original(name: String): File = File(directory, "$name.shot")

    private companion object {
        const val DIRECTORY = "receipts"
    }
}
