package app.jourjour.core.photo

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max

/**
 * Photos are the bulk of what the app stores, so both halves of keeping that in check are
 * covered: shrinking what the camera hands over, and clearing files no record points at.
 */
@RunWith(AndroidJUnit4::class)
class PhotoStorageTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = PhotoStorage(context)

    @After
    fun tearDown() = runBlocking {
        storage.deleteOrphans(emptySet())
    }

    @Test
    fun a_photo_is_shrunk_to_the_given_side() = runBlocking {
        val name = storage.newName()
        writeBitmap(storage.file(name), width = 3000, height = 2000)
        val before = storage.file(name).length()

        storage.compress(name, maxSide = 800)

        val bitmap = storage.load(name, maxSide = 4096)!!
        assertEquals(800, max(bitmap.width, bitmap.height))
        assertTrue("became no smaller", storage.file(name).length() < before)
    }

    @Test
    fun proportions_are_kept() = runBlocking {
        val name = storage.newName()
        writeBitmap(storage.file(name), width = 2000, height = 1000)

        storage.compress(name, maxSide = 500)

        val bitmap = storage.load(name, maxSide = 4096)!!
        assertEquals(500, bitmap.width)
        assertEquals(250, bitmap.height)
    }

    @Test
    fun files_nothing_points_at_are_deleted() = runBlocking {
        val kept = storage.newName()
        val orphan = storage.newName()
        writeBitmap(storage.file(kept), width = 100, height = 100)
        writeBitmap(storage.file(orphan), width = 100, height = 100)

        storage.deleteOrphans(setOf(kept))

        assertTrue(storage.exists(kept))
        assertFalse(storage.exists(orphan))
    }

    @Test
    fun the_camera_link_points_at_our_own_file() {
        val name = storage.newName()
        val uri = storage.uri(name)

        // A content:// link through the provider, not a file path: since Android 7 a
        // file:// URI handed to another app throws.
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue(uri.toString(), uri.toString().endsWith(name))
    }

    private fun writeBitmap(file: File, width: Int, height: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
    }
}
