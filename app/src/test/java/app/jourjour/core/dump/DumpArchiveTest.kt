package app.jourjour.core.dump

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The archive is the document plus the photos under the paths they have on the device.
 * What is checked is that the document comes back, that the photos are listed and then
 * taken out to where they are asked for, and that a bare document is told apart from an
 * archive by its first bytes.
 */
class DumpArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dump = Dump(databaseVersion = 13, exportedAt = "2026-09-16T10:00:00")

    @Test
    fun `the document and the photos come back out of the archive`() {
        val receipt = folder.newFile("a.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val cover = folder.newFile("c.jpg").apply { writeBytes(byteArrayOf(9)) }
        val out = ByteArrayOutputStream()

        val written = DumpArchive.write(
            dump = dump,
            receipts = mapOf("a.jpg" to receipt, "missing.jpg" to File(folder.root, "missing.jpg")),
            covers = mapOf("c.jpg" to cover),
            target = out,
        )
        val bytes = out.toByteArray()

        // The missing file is passed over, not written empty.
        assertEquals(2, written)
        assertTrue(DumpArchive.isArchive(bytes.copyOf(4)))

        val contents = DumpArchive.read(ByteArrayInputStream(bytes))
        assertEquals(dump, contents.dump)
        assertEquals(setOf("a.jpg"), contents.receipts)
        assertEquals(setOf("c.jpg"), contents.covers)

        val into = folder.newFolder("into")
        val extracted = DumpArchive.extract(
            source = ByteArrayInputStream(bytes),
            receipts = { name -> File(into, "r-$name") },
            covers = { null },
        )
        assertEquals(1, extracted)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(into, "r-a.jpg").readBytes())
    }

    @Test
    fun `a bare document is not taken for an archive`() {
        val head = Dump.encode(dump).toByteArray().copyOf(4)

        assertFalse(DumpArchive.isArchive(head))
    }
}
