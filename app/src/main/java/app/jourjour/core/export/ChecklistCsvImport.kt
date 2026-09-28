package app.jourjour.core.export

import app.jourjour.data.entity.ChecklistItem
import java.util.Locale

/**
 * Reads a checklist back from a table in the shape [ChecklistCsv] writes — or part of it.
 *
 * The columns are found by their titles in the first line, in whatever order they come, so
 * a file cut down or rearranged in a spreadsheet still reads. Only the section and the
 * wording have to be there; a file of two columns is a perfectly good checklist, and every
 * item in it arrives outstanding.
 *
 * Nothing is written until the whole file has been understood. A row that cannot be made
 * sense of stops the import and is reported with its line number: half an import is worse
 * than none, because there is no telling by eye which half arrived.
 */
object ChecklistCsvImport {

    /** The separators an export can have used, tried against the header in this order. */
    private val SEPARATORS = listOf(',', ';')

    val REQUIRED: List<ChecklistColumn> = listOf(ChecklistColumn.SECTION, ChecklistColumn.TITLE)

    /**
     * What a flag may be written as, beyond the two words the export writes.
     *
     * Marks rather than words, so that they hold in any language: this is what a person
     * editing the file by hand reaches for, whatever the column is headed. The words
     * themselves are not here — they come from the same string resources the export wrote
     * them with, so that the two halves of the round trip cannot drift apart. Anything
     * outside both lists is reported rather than quietly read as "no": a column nobody can
     * read is a column that has not been understood.
     */
    private val AFFIRMATIVE_MARKS = setOf("true", "1", "+", "x", "v", "✓", "✔")
    private val NEGATIVE_MARKS = setOf("", "false", "0", "-", "—")

    sealed interface Result {

        /** The items the file describes, in the order it describes them. */
        data class Ready(val items: List<ChecklistItem>) : Result

        /** No arrangement of the separators produced a first line naming any column. */
        data object HeaderMismatch : Result

        /** The first line names some columns, but not all of [REQUIRED]; these are missing. */
        data class MissingColumns(val columns: List<ChecklistColumn>) : Result

        /** A column that has to say something and says nothing. [line] counts from 1. */
        data class Empty(val line: Int, val column: ChecklistColumn) : Result

        /** A flag written as something neither list knows. */
        data class Malformed(val line: Int, val column: ChecklistColumn, val value: String) : Result
    }

    /**
     * @param headers the titles this app would write, in [ChecklistColumn] order — the
     *   fixed names the first line of the file is matched against.
     * @param yes, @param no the words the export writes a flag as, from the same string
     *   resources it used, so that what was written is what is read.
     */
    fun read(
        text: String,
        headers: List<String>,
        tripId: Long,
        yes: String,
        no: String,
    ): Result {
        val columnOf = ChecklistColumn.entries.zip(headers)
            .associate { (column, title) -> title to column }

        // Whichever separator makes the first line name the most columns: the file may
        // have been written on a phone whose locale splits columns differently.
        val (separator, positions) = SEPARATORS
            .map { candidate ->
                candidate to positions(CsvReader.read(text, candidate).firstOrNull(), columnOf)
            }
            .maxBy { (_, found) -> found.size }
        if (positions.isEmpty()) return Result.HeaderMismatch
        val missing = REQUIRED.filter { it !in positions }
        if (missing.isNotEmpty()) return Result.MissingColumns(missing)

        val items = mutableListOf<ChecklistItem>()
        CsvReader.read(text, separator).drop(1).forEachIndexed { index, fields ->
            // The header is line 1, so the first record is line 2 — what a spreadsheet shows.
            val line = index + 2

            fun field(column: ChecklistColumn): String =
                positions[column]?.let { fields.getOrNull(it) }.orEmpty().trim()

            val section = field(ChecklistColumn.SECTION)
            if (section.isEmpty()) return Result.Empty(line, ChecklistColumn.SECTION)
            val title = field(ChecklistColumn.TITLE)
            if (title.isEmpty()) return Result.Empty(line, ChecklistColumn.TITLE)

            val rawDone = field(ChecklistColumn.DONE)
            val done = readFlag(rawDone, yes, no)
                ?: return Result.Malformed(line, ChecklistColumn.DONE, rawDone)
            val rawSkipped = field(ChecklistColumn.SKIPPED)
            val skipped = readFlag(rawSkipped, yes, no)
                ?: return Result.Malformed(line, ChecklistColumn.SKIPPED, rawSkipped)

            items += ChecklistItem(
                tripId = tripId,
                section = section,
                title = title,
                isDone = done,
                isSkipped = skipped,
            )
        }
        return Result.Ready(items)
    }

    /** A flag as a person might have written it, or null for something neither list knows. */
    private fun readFlag(value: String, yes: String, no: String): Boolean? {
        val written = value.lowercase(Locale.ROOT)
        // The words the export wrote come first: they are what the file actually holds,
        // and one of them might well be a mark in some language.
        return when (written) {
            yes.lowercase(Locale.ROOT) -> true
            no.lowercase(Locale.ROOT) -> false
            in AFFIRMATIVE_MARKS -> true
            in NEGATIVE_MARKS -> false
            else -> null
        }
    }

    /** Where each column the first line names is found; a title named twice keeps its first. */
    private fun positions(
        header: List<String>?,
        columnOf: Map<String, ChecklistColumn>,
    ): Map<ChecklistColumn, Int> {
        val found = mutableMapOf<ChecklistColumn, Int>()
        header?.forEachIndexed { index, title ->
            val column = columnOf[title.trim()] ?: return@forEachIndexed
            found.putIfAbsent(column, index)
        }
        return found
    }
}
