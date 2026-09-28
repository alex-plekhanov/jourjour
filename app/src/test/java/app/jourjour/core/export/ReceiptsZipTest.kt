package app.jourjour.core.export

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

/**
 * What leaves the app is a UUID on disk and a readable name in the archive, so these check
 * the naming as much as the archiving: the order, the fallback when an entry has no title,
 * and that one missing file does not cost a person the rest of the pictures.
 */
class ReceiptsZipTest {

    @get:Rule
    val folder = TemporaryFolder()


    @Test
    fun photos_are_numbered_and_come_oldest_first() {
        val archive = write(
            receipt(photo = file("a.jpg"), title = "later", at = day(10)),
            receipt(photo = file("b.jpg"), title = "earlier", at = day(3)),
        )

        assertEquals(
            listOf("001 2026-04-03 earlier.jpg", "002 2026-04-10 later.jpg"),
            archive.names(),
        )
    }

    @Test
    fun without_a_title_the_category_is_used() {
        val archive = write(receipt(photo = file("a.jpg"), title = ""))

        assertEquals(listOf("001 2026-04-03 Restaurants.jpg"), archive.names())
    }

    @Test
    fun anything_a_file_system_dislikes_leaves_the_name() {
        val archive = write(receipt(photo = file("a.jpg"), title = "Lunch: 50%/60% \"pie\""))

        assertEquals(listOf("001 2026-04-03 Lunch 50 60 pie.jpg"), archive.names())
    }

    @Test
    fun a_missing_file_does_not_break_the_export() {
        val archive = write(
            receipt(photo = "gone.jpg", at = day(3), title = "missing"),
            receipt(photo = file("b.jpg"), at = day(4), title = "here"),
        )

        // The numbering counts what actually went in, so there is no gap at 001.
        assertEquals(listOf("001 2026-04-04 here.jpg"), archive.names())
    }

    @Test
    fun entries_without_a_photo_are_skipped() {
        val archive = write(receipt(photo = null), receipt(photo = file("a.jpg"), title = "kept"))

        assertEquals(listOf("001 2026-04-03 kept.jpg"), archive.names())
    }

    @Test
    fun the_contents_of_a_photo_arrive_whole() {
        val archive = write(receipt(photo = file("a.jpg", "receipt bytes"), title = "one"))

        assertEquals(listOf("receipt bytes"), archive.contents())
    }

    private fun write(vararg entries: TestEntry): ByteArray {
        val out = ByteArrayOutputStream()
        val count = ReceiptsZip.write(
            entries = entries.toList(),
            nameOf = { name, _ -> name },
            fileOf = { File(folder.root, it) },
            target = out,
        )
        assertEquals(out.toByteArray().names().size, count)
        return out.toByteArray()
    }

    private fun file(name: String, content: String = "x"): String {
        File(folder.root, name).writeText(content)
        return name
    }

    private fun day(day: Int) = LocalDateTime.of(2026, 4, day, 12, 0)

    private fun ByteArray.names(): List<String> = read().map { it.first }

    private fun ByteArray.contents(): List<String> = read().map { it.second }

    private fun ByteArray.read(): List<Pair<String, String>> =
        ZipInputStream(inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .map { it.name to zip.readBytes().decodeToString() }
                .toList()
        }

    private fun receipt(
        photo: String?,
        title: String = "",
        at: LocalDateTime = day(3),
    ) = entry(photoFile = photo, title = title, dateTime = at)
}
