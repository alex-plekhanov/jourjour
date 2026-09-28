package app.jourjour.core.dump

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A [Dump] and the photos that go with it, as one ZIP.
 *
 * The document sits at the root as `dump.json`; the photos keep the layout they have on
 * the device — `receipts/` and `trip_covers/`, each file under the name the rows point
 * at — so that an archive is read the same way the app's own storage is, and a name in
 * the document finds its file by nothing more than a path.
 *
 * JPEGs are already compressed, so the entries are stored as they are.
 */
object DumpArchive {

    const val MIME_TYPE = "application/zip"

    const val DOCUMENT = "dump.json"

    const val RECEIPTS = "receipts"

    const val COVERS = "trip_covers"

    /** The document and what is in the archive beside it, by folder. */
    class Contents(
        val dump: Dump,
        val receipts: Set<String>,
        val covers: Set<String>,
    )

    /**
     * @param receipts the receipt files to put in, by name; a name whose file is missing
     *   is passed over, since the document already says what was there.
     */
    fun write(
        dump: Dump,
        receipts: Map<String, File>,
        covers: Map<String, File>,
        target: OutputStream,
    ): Int {
        var photos = 0
        ZipOutputStream(target).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry(DOCUMENT))
            zip.write(Dump.encode(dump).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photos += zip.putAll(RECEIPTS, receipts)
            photos += zip.putAll(COVERS, covers)
        }
        return photos
    }

    private fun ZipOutputStream.putAll(folder: String, files: Map<String, File>): Int {
        var count = 0
        files.forEach { (name, file) ->
            if (!file.isFile) return@forEach
            putNextEntry(ZipEntry("$folder/$name"))
            file.inputStream().use { it.copyTo(this) }
            closeEntry()
            count++
        }
        return count
    }

    /** Whether [head] — the first bytes of a file — is an archive rather than a bare document. */
    fun isArchive(head: ByteArray): Boolean =
        head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

    /** Reads the document and lists the photos, without taking the photos out. */
    fun read(source: InputStream): Contents {
        var dump: Dump? = null
        val receipts = mutableSetOf<String>()
        val covers = mutableSetOf<String>()
        ZipInputStream(source).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val (folder, name) = entry.name.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
                when {
                    entry.name == DOCUMENT -> dump = Dump.decode(zip.readBytes().toString(Charsets.UTF_8))
                    folder == RECEIPTS && !name.isNullOrEmpty() -> receipts += name
                    folder == COVERS && !name.isNullOrEmpty() -> covers += name
                }
                zip.closeEntry()
            }
        }
        return Contents(
            dump = dump ?: error("The archive has no $DOCUMENT in it"),
            receipts = receipts,
            covers = covers,
        )
    }

    /**
     * Takes the wanted photos out, each to the file its name maps to. A second pass over
     * the archive: the rows have to be in before it is known which photos they point at.
     */
    fun extract(
        source: InputStream,
        receipts: (name: String) -> File?,
        covers: (name: String) -> File?,
    ): Int {
        var count = 0
        ZipInputStream(source).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val (folder, name) = entry.name.split('/', limit = 2).let { it[0] to it.getOrNull(1) }
                val target = when {
                    name.isNullOrEmpty() -> null
                    folder == RECEIPTS -> receipts(name)
                    folder == COVERS -> covers(name)
                    else -> null
                }
                if (target != null) {
                    target.outputStream().use { zip.copyTo(it) }
                    count++
                }
                zip.closeEntry()
            }
        }
        return count
    }
}
