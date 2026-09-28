package app.jourjour.core.export

import app.jourjour.data.entity.EntryDetails
import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.time.format.DateTimeFormatter

/**
 * The receipt photos of a trip, gathered into one archive.
 *
 * What is stored on disk is a UUID, which tells a person nothing once the file is out of
 * the app. Inside the archive each photo is renamed after the entry it belongs to and
 * numbered, so the pictures sort in the order they were taken whatever opens them.
 */
object ReceiptsZip {

    const val MIME_TYPE = "application/zip"

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Long enough to recognise an entry, short enough not to trip a file system. */
    private const val MAX_TITLE = 40

    private const val DEFAULT_EXTENSION = "jpg"

    /**
     * Writes every photo the entries point at and answers with how many went in.
     *
     * An entry whose file has gone missing is skipped rather than failing the export: one
     * lost picture should not cost a person the rest of them.
     *
     * @param nameOf resolves the stored name and resource key of a dictionary entry.
     * @param fileOf where a stored photo name lives on disk.
     */
    fun write(
        entries: List<EntryDetails>,
        nameOf: (name: String, nameKey: String?) -> String,
        fileOf: (photoFile: String) -> File,
        target: OutputStream,
    ): Int {
        val withPhotos = entries
            .filter { it.entry.photoFile != null }
            .sortedBy { it.entry.dateTime }

        var written = 0
        ZipOutputStream(target).use { zip ->
            // A JPEG is compressed already: deflating it again costs time and saves
            // nothing worth measuring.
            zip.setLevel(Deflater.NO_COMPRESSION)

            withPhotos.forEach { details ->
                val file = fileOf(requireNotNull(details.entry.photoFile))
                if (!file.isFile) return@forEach

                zip.putNextEntry(ZipEntry(fileName(written + 1, details, nameOf)))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                written++
            }
        }
        return written
    }

    /**
     * `007 2026-04-10 Lunch at the pier.jpg` — the number keeps the order, the date and
     * the title say which entry it came from.
     */
    fun fileName(
        position: Int,
        details: EntryDetails,
        nameOf: (name: String, nameKey: String?) -> String,
    ): String {
        val entry = details.entry
        val label = entry.title
            .ifBlank { nameOf(details.category.name, details.category.nameKey) }
            .take(MAX_TITLE)
            .sanitized()
        val date = entry.dateTime.toLocalDate().format(dateFormat)
        val extension = entry.photoFile
            ?.substringAfterLast('.', DEFAULT_EXTENSION)
            ?.ifBlank { DEFAULT_EXTENSION }
            ?: DEFAULT_EXTENSION

        return "%03d %s %s.%s".format(position, date, label, extension)
    }

    /** Anything a file system might object to becomes a space; runs of them collapse. */
    private fun String.sanitized(): String =
        map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else ' ' }
            .joinToString("")
            .trim()
            .replace(Regex(" +"), " ")
            .ifBlank { "receipt" }
}
