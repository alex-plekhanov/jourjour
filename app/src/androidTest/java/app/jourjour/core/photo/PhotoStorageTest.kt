package app.jourjour.core.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.jourjour.data.prefs.PhotoSettings
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
 * covered: rendering what the camera hands over, and clearing files no record points at.
 *
 * The rendering is covered twice over because a shot is looked at before it is kept, and
 * every turn renders it again: what must hold is that each rendering comes from the
 * camera's own pixels rather than from the one before it.
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
    fun a_shot_is_shrunk_to_the_given_side() = runBlocking {
        val name = shotFromCamera(width = 3000, height = 2000)
        val before = storage.file(name).length()

        storage.setAside(name)
        storage.render(name, maxSide = 800, quality = PhotoSettings.DEFAULT_QUALITY)

        val bitmap = storage.load(name, maxSide = 4096)!!
        assertEquals(800, max(bitmap.width, bitmap.height))
        assertTrue("became no smaller", storage.file(name).length() < before)
    }

    @Test
    fun proportions_are_kept() = runBlocking {
        val name = shotFromCamera(width = 2000, height = 1000)

        storage.setAside(name)
        storage.render(name, maxSide = 500, quality = PhotoSettings.DEFAULT_QUALITY)

        val bitmap = storage.load(name, maxSide = 4096)!!
        assertEquals(500, bitmap.width)
        assertEquals(250, bitmap.height)
    }

    @Test
    fun a_turn_arrives_with_the_shrinking() = runBlocking {
        val name = shotFromCamera(width = 2000, height = 1000)

        storage.setAside(name)
        storage.render(name, maxSide = 500, quality = PhotoSettings.DEFAULT_QUALITY, quarterTurns = 1)

        // Turned onto its end and shrunk in the one encode, not one after the other.
        val bitmap = storage.load(name, maxSide = 4096)!!
        assertEquals(250, bitmap.width)
        assertEquals(500, bitmap.height)
    }

    @Test
    fun every_rendering_is_made_from_the_shot_the_camera_wrote() = runBlocking {
        val name = shotFromCamera(width = 2000, height = 1000)
        storage.setAside(name)

        // Turned a quarter, then back again: the second rendering has to come out as the
        // camera had it, which it only can if it was made from the camera's own pixels
        // rather than from the rendering standing in its place.
        storage.render(name, maxSide = 500, quality = PhotoSettings.DEFAULT_QUALITY, quarterTurns = 1)
        storage.render(name, maxSide = 500, quality = PhotoSettings.DEFAULT_QUALITY, quarterTurns = 0)

        val bitmap = storage.load(name, maxSide = 4096)!!
        assertEquals(500, bitmap.width)
        assertEquals(250, bitmap.height)
    }

    @Test
    fun a_turn_without_shrinking_only_rewrites_the_tag() = runBlocking {
        val name = shotFromCamera(width = 200, height = 100)
        val before = storage.file(name).length()

        storage.setAside(name)
        storage.renderUnshrunk(name, quarterTurns = 1)

        // The pixels are the camera's own, down to the byte count — the photo stands up
        // because everything that draws it reads the tag.
        val stored = BitmapFactory.decodeFile(storage.file(name).path)
        assertEquals(200, stored.width)
        assertEquals(100, stored.height)
        assertTrue("the pixels were re-encoded", storage.file(name).length() >= before)
        val shown = storage.load(name, maxSide = 4096)!!
        assertEquals(100, shown.width)
        assertEquals(200, shown.height)
    }

    @Test
    fun a_shot_thrown_away_leaves_nothing_to_render_from() = runBlocking {
        val name = shotFromCamera(width = 200, height = 100)
        storage.setAside(name)
        storage.render(name, maxSide = 100, quality = PhotoSettings.DEFAULT_QUALITY)

        storage.discard(name)
        storage.render(name, maxSide = 100, quality = PhotoSettings.DEFAULT_QUALITY)

        // Both halves went: the photo and the shot it was rendered from.
        assertFalse(storage.exists(name))
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

    /** What the state of the folder is when a capture returns: the shot under its own name. */
    private fun shotFromCamera(width: Int, height: Int): String {
        val name = storage.newName()
        writeBitmap(storage.file(name), width, height)
        return name
    }

    private fun writeBitmap(file: File, width: Int, height: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
    }
}
