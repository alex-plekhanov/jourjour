package app.jourjour.core.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipt photos on disk.
 *
 * The camera writes into this folder through a link handed to it for one file at a time,
 * so a shot never reaches the gallery — sharing a photo out is the way to keep one.
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
     * Shrinks a freshly taken photo. A phone camera writes several megabytes; a receipt
     * stays readable at a fraction of that, and a hundred of them should not eat half a
     * gigabyte of the app's storage.
     *
     * The rotation the camera recorded in EXIF is baked into the pixels here, because
     * re-encoding drops the tag that carried it.
     *
     * How far to shrink and how hard to encode are both settings, and both are handed in:
     * what a receipt has to stay readable for is the person keeping it, not this class.
     */
    suspend fun compress(name: String, maxSide: Int, quality: Int) = withContext(Dispatchers.IO) {
        val file = file(name)
        if (!file.exists()) return@withContext

        val decoded = decodeFile(file, maxSide) ?: return@withContext
        // Written beside the original and swapped in whole: a write that fails halfway
        // would otherwise leave a truncated JPEG under the name the entry is about to keep.
        // A draft left behind by a crash is a file no record points at, and goes with the
        // other orphans on the next start.
        val draft = File(file.parentFile, "$name.part")
        try {
            val written = runCatching {
                draft.outputStream().use {
                    decoded.compress(Bitmap.CompressFormat.JPEG, quality, it)
                }
            }.getOrDefault(false)
            if (!written || !draft.renameTo(file)) draft.delete()
        } finally {
            decoded.recycle()
        }
    }

    private companion object {
        const val DIRECTORY = "receipts"
    }
}
