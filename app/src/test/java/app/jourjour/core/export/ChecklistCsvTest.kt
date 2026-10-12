package app.jourjour.core.export

import app.jourjour.data.entity.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table a checklist goes out as and comes back from. What matters is the round trip —
 * a list written out and read back has to be the same list — and what a file that has been
 * through a spreadsheet can still say: columns moved, columns missing, a flag written as a
 * cross instead of a word.
 */
class ChecklistCsvTest {

    private val headers =
        listOf("Раздел", "Наименование", "Количество", "Выполнено", "Не актуально")

    private val items = listOf(
        ChecklistItem(id = 1, tripId = 1, section = "Papers", title = "Passport", isDone = true),
        ChecklistItem(id = 2, tripId = 1, section = "Papers", title = "Visa", isSkipped = true),
        // A comma and a quotation mark, which is what the quoting rules are there for.
        ChecklistItem(
            id = 3,
            tripId = 1,
            section = "Bag",
            title = "Charger, 65 W \"fast\"",
            quantityMilli = 2_000,
        ),
    )

    private fun write(separator: String = ";") =
        ChecklistCsv.build(headers, items, separator, yes = "Да", no = "Нет")

    private fun read(text: String) =
        ChecklistCsvImport.read(text, headers, tripId = 2, yes = "Да", no = "Нет")

    @Test
    fun `a list written out and read back is the same list`() {
        val result = read(write()) as ChecklistCsvImport.Result.Ready

        assertEquals(items.map { it.section }, result.items.map { it.section })
        assertEquals(items.map { it.title }, result.items.map { it.title })
        assertEquals(items.map { it.isDone }, result.items.map { it.isDone })
        assertEquals(items.map { it.isSkipped }, result.items.map { it.isSkipped })
        // How many comes back as a count and not as a blank, and the items that never
        // said come back saying nothing rather than one.
        assertEquals(items.map { it.quantityMilli }, result.items.map { it.quantityMilli })
        // The rows belong to whoever is reading them in, not to the trip that wrote them.
        assertTrue(result.items.all { it.tripId == 2L })
    }

    @Test
    fun `the lines come down the page in the order the screen reads them`() {
        // There is no column for the order, so the lines have to carry it. A section
        // written in two sittings has its rows sitting apart, and the file gathers them
        // the way the screen does rather than writing the sections interleaved.
        val scattered = listOf(
            ChecklistItem(id = 1, tripId = 1, section = "Papers", title = "Passport"),
            ChecklistItem(id = 2, tripId = 1, section = "Bag", title = "Charger"),
            ChecklistItem(id = 3, tripId = 1, section = "Papers", title = "Visa"),
        )

        val written = ChecklistCsv.build(headers, scattered, ";", yes = "Да", no = "Нет")
        val result = read(written) as ChecklistCsvImport.Result.Ready

        assertEquals(listOf("Passport", "Visa", "Charger"), result.items.map { it.title })
    }

    @Test
    fun `how many is written without grouping, so a thousand of a thing survives`() {
        // A grouped three thousand goes out as "3 000" or "3,000" and comes back as
        // three on a phone whose language has changed since — the space means nothing
        // there, and the comma means a half. Digits and a decimal point, nothing else.
        val thousands = listOf(
            ChecklistItem(id = 1, tripId = 1, section = "Bag", title = "Pin", quantityMilli = 3_000_000),
        )

        val written = ChecklistCsv.build(headers, thousands, ";", yes = "Да", no = "Нет")
        val quantity = written.lines()[1].split(";")[2]

        assertTrue(quantity, quantity.all { it.isDigit() })
        val result = read(written) as ChecklistCsvImport.Result.Ready
        assertEquals(3_000_000L, result.items.single().quantityMilli)
    }

    @Test
    fun `it reads back whichever separator it was written with`() {
        val commas = read(write(separator = ",")) as ChecklistCsvImport.Result.Ready

        assertEquals(3, commas.items.size)
        assertEquals("Charger, 65 W \"fast\"", commas.items.last().title)
    }

    @Test
    fun `the columns may be moved about, and the flags left out`() {
        val text = "Наименование;Раздел\r\nPassport;Papers\r\n"

        val result = read(text) as ChecklistCsvImport.Result.Ready

        assertEquals("Papers", result.items.single().section)
        assertEquals("Passport", result.items.single().title)
        // A file of two columns is a perfectly good checklist, all of it outstanding.
        assertTrue(result.items.single().isOutstanding)
    }

    @Test
    fun `a flag may be written as a mark instead of the word`() {
        val text = "Раздел;Наименование;Выполнено;Не актуально\r\nBag;Charger;x;-\r\n"

        val result = read(text) as ChecklistCsvImport.Result.Ready

        assertTrue(result.items.single().isDone)
        assertTrue(!result.items.single().isSkipped)
    }

    @Test
    fun `the words are the ones the export was given, not a list of its own`() {
        val text = "Раздел;Наименование;Выполнено\r\nBag;Charger;Yes\r\n"

        // Read the way an app running in English would read the file it wrote.
        val english = ChecklistCsvImport.read(text, headers, tripId = 2, yes = "Yes", no = "No")
        assertTrue((english as ChecklistCsvImport.Result.Ready).items.single().isDone)

        // The same file read with the Russian words: "Yes" is not one of them.
        assertTrue(read(text) is ChecklistCsvImport.Result.Malformed)
    }

    @Test
    fun `a flag nobody can read stops the import rather than counting as no`() {
        val text = "Раздел;Наименование;Выполнено\r\nBag;Charger;perhaps\r\n"

        val result = read(text) as ChecklistCsvImport.Result.Malformed

        assertEquals(2, result.line)
        assertEquals(ChecklistColumn.DONE, result.column)
        assertEquals("perhaps", result.value)
    }

    @Test
    fun `a row without a section or a wording stops the import`() {
        val noSection = read("Раздел;Наименование\r\n;Passport\r\n")
        assertEquals(
            ChecklistCsvImport.Result.Empty(2, ChecklistColumn.SECTION),
            noSection,
        )

        val noTitle = read("Раздел;Наименование\r\nPapers;\r\n")
        assertEquals(ChecklistCsvImport.Result.Empty(2, ChecklistColumn.TITLE), noTitle)
    }

    @Test
    fun `a file naming no column the app knows is refused`() {
        assertEquals(
            ChecklistCsvImport.Result.HeaderMismatch,
            read("one;two\r\nPapers;Passport\r\n"),
        )
    }

    @Test
    fun `a file missing a required column says which`() {
        val result = read("Наименование;Выполнено\r\nPassport;Да\r\n")

        assertEquals(
            ChecklistCsvImport.Result.MissingColumns(listOf(ChecklistColumn.SECTION)),
            result,
        )
    }

    @Test
    fun `the file starts with the mark that makes a spreadsheet read it as UTF-8`() {
        assertTrue(write().startsWith(CsvWriter.BOM))
    }
}
