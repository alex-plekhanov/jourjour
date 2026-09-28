package app.jourjour.core.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A cover is always drawn across the full width of the screen, so what the import has to
 * guarantee is the width — and that it never keeps more height than any of those places
 * would ever show.
 */
@RunWith(AndroidJUnit4::class)
class TripCoverStorageTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = TripCoverStorage(context)
    private val sources = mutableListOf<File>()

    @After
    fun tearDown() = runBlocking {
        storage.deleteOrphans(emptySet())
        sources.forEach { it.delete() }
    }

    @Test
    fun a_wide_photo_is_scaled_by_its_width() = runBlocking {
        val name = storage.importFrom(source(width = 3000, height = 1500))
        assertNotNull(name)

        val cover = storage.load(name!!, maxSide = 4096)!!
        assertEquals(TripCoverStorage.WIDTH, cover.width)
        assertEquals(TripCoverStorage.WIDTH / 2, cover.height)
    }

    @Test
    fun an_upright_photo_is_cropped_to_a_square() = runBlocking {
        val name = storage.importFrom(source(width = 2000, height = 6000))
        assertNotNull(name)

        val cover = storage.load(name!!, maxSide = 4096)!!
        assertEquals(TripCoverStorage.WIDTH, cover.width)
        assertEquals(TripCoverStorage.WIDTH, cover.height)
    }

    @Test
    fun a_small_photo_is_not_enlarged() = runBlocking {
        val name = storage.importFrom(source(width = 400, height = 300))
        assertNotNull(name)

        val cover = storage.load(name!!, maxSide = 4096)!!
        assertEquals(400, cover.width)
        assertEquals(300, cover.height)
    }

    @Test
    fun covers_live_apart_from_receipts() = runBlocking {
        val receipts = PhotoStorage(context)
        val receipt = receipts.newName()
        writeBitmap(receipts.file(receipt), width = 100, height = 100)

        val cover = storage.importFrom(source(width = 200, height = 200))!!
        // The covers are swept against their own list of names, and a receipt is not on it.
        storage.deleteOrphans(setOf(cover))

        assertTrue("the receipt was caught by the covers' sweep", receipts.exists(receipt))
        assertTrue(storage.exists(cover))

        receipts.delete(receipt)
    }

    @Test
    fun a_photo_that_cannot_be_read_is_not_stored() = runBlocking {
        val broken = File(context.cacheDir, "broken.jpg").also { sources += it }
        broken.writeText("not a picture")

        assertNull(storage.importFrom(Uri.fromFile(broken)))
        assertFalse(storage.file("broken.jpg").exists())
    }

    private fun source(width: Int, height: Int): Uri {
        val file = File(context.cacheDir, "source-${sources.size}.jpg").also { sources += it }
        writeBitmap(file, width, height)
        return Uri.fromFile(file)
    }

    private fun writeBitmap(file: File, width: Int, height: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
    }
}
