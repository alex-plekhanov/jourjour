package app.jourjour.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

/**
 * The file is written by hand, so what these check is that it is a package a spreadsheet
 * will accept — every part present and referred to — and that the types survive: an amount
 * arrives as a number and a date as a date rather than as text that merely looks like one.
 */
class TripXlsxTest {

    private val allColumns = TripColumn.entries.toList()

    @Test
    fun the_archive_holds_every_part_of_the_package() {
        assertEquals(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
            ),
            write(entry()).names(),
        )
    }

    @Test
    fun the_titles_come_first_and_are_set_apart() {
        val sheet = write(entry()).sheet()

        assertTrue(sheet, sheet.contains("""<c r="A1" s="1" t="inlineStr"><is><t xml:space="preserve">DATE"""))
        assertTrue(sheet, sheet.contains(">NOTES<"))
    }

    @Test
    fun a_date_is_written_as_a_day_count_not_as_text() {
        // 1 January 2021 is serial 44197 in every spreadsheet, phantom leap day included.
        val sheet = write(entry(dateTime = LocalDateTime.of(2021, 1, 1, 9, 0))).sheet()

        assertTrue(sheet, sheet.contains("""<c r="A2" s="2"><v>44197</v></c>"""))
    }

    @Test
    fun columns_are_as_wide_as_what_they_hold() {
        val sheet = write(entry()).sheet()

        // The date is column A, the time B, the expense amount F and its currency G. The
        // stored width carries the padding a spreadsheet takes off again when it shows it.
        assertTrue(sheet, sheet.contains("""<col min="1" max="1" width="10.71" customWidth="1"/>"""))
        assertTrue(sheet, sheet.contains("""<col min="2" max="2" width="5.71" customWidth="1"/>"""))
        assertTrue(sheet, sheet.contains("""<col min="6" max="6" width="12.71" customWidth="1"/>"""))
        assertTrue(sheet, sheet.contains("""<col min="7" max="7" width="5.71" customWidth="1"/>"""))
    }

    @Test
    fun a_time_is_written_as_a_fraction_of_the_day() {
        // Nine in the morning is three eighths of the way through the day.
        val sheet = write(entry(dateTime = LocalDateTime.of(2021, 1, 1, 9, 0))).sheet()

        assertTrue(sheet, sheet.contains("""<c r="B2" s="6"><v>0.375</v></c>"""))
    }

    @Test
    fun an_amount_is_a_number_formatted_by_its_currency() {
        val sheet = write(
            entry(expenseAmountMilli = 1_250_500),
            entry(id = 2, expenseAmountMilli = 500_000, expenseCurrency = jpy),
            entry(id = 3, expenseAmountMilli = 12_345, expenseCurrency = kwd),
        ).sheet()

        // Two decimals, none and three: the style is what a reader pads the number to.
        assertTrue(sheet, sheet.contains("""<c r="F2" s="4"><v>1250.500</v></c>"""))
        assertTrue(sheet, sheet.contains("""<c r="F3" s="3"><v>500.000</v></c>"""))
        assertTrue(sheet, sheet.contains("""<c r="F4" s="5"><v>12.345</v></c>"""))
    }

    @Test
    fun an_empty_cell_is_not_written_at_all() {
        val sheet = write(entry()).sheet()

        // Only the date, the time and the category are known, so the row carries three cells.
        val row = sheet.substringAfter("""<row r="2">""").substringBefore("</row>")
        assertEquals(3, row.split("<c ").size - 1)
    }

    @Test
    fun markup_in_text_is_escaped() {
        val sheet = write(entry(title = "Tea & <b>pie</b>")).sheet()

        assertTrue(sheet, sheet.contains("Tea &amp; &lt;b&gt;pie&lt;/b&gt;"))
        assertFalse(sheet, sheet.contains("<b>pie"))
    }

    @Test
    fun the_sheet_name_is_cleaned_and_cut_to_length() {
        val workbook = write(entry(), sheetName = "Japan/2026: [spring]").workbook()
        assertTrue(workbook, workbook.contains("""name="Japan2026 spring""""))

        val long = write(entry(), sheetName = "x".repeat(40)).workbook()
        assertTrue(long, long.contains("""name="${"x".repeat(31)}""""))

        val blank = write(entry(), sheetName = "   ").workbook()
        assertTrue(blank, blank.contains("""name="Trip""""))
    }

    @Test
    fun there_are_as_many_columns_as_in_the_csv() {
        val sheet = write(entry()).sheet()
        val header = sheet.substringAfter("""<row r="1">""").substringBefore("</row>")

        assertEquals(TripColumn.entries.size, header.split("<c ").size - 1)
    }

    @Test
    fun the_sheet_carries_only_the_chosen_columns() {
        val sheet = write(
            entry(title = "Lunch", expenseAmountMilli = 1_250_500),
            columns = listOf(TripColumn.DATE, TripColumn.TITLE, TripColumn.EXPENSE_AMOUNT),
        ).sheet()

        val header = sheet.substringAfter("""<row r="1">""").substringBefore("</row>")
        assertEquals(3, header.split("<c ").size - 1)
        assertTrue(header, header.contains(">TITLE<"))
        assertFalse(header, header.contains(">PLACE<"))

        // The amount has moved from column F to C, and its title moved with it.
        val row = sheet.substringAfter("""<row r="2">""").substringBefore("</row>")
        assertTrue(row, row.contains("""<c r="C2" s="4"><v>1250.500</v></c>"""))
    }

    @Test
    fun the_column_widths_follow_the_chosen_columns() {
        val sheet = write(entry(), columns = listOf(TripColumn.DATE, TripColumn.NOTES)).sheet()

        // Two columns chosen, so two widths: the date's, then the notes' — and no third.
        assertTrue(sheet, sheet.contains("""<col min="1" max="1" width="10.71" customWidth="1"/>"""))
        assertTrue(sheet, sheet.contains("""<col min="2" max="2" width="18.71" customWidth="1"/></cols>"""))
    }

    private fun write(
        vararg entries: TestEntry,
        sheetName: String = "Japan",
        columns: List<TripColumn> = allColumns,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val count = TripXlsx.write(
            sheetName = sheetName,
            columns = columns,
            titleOf = { it.name },
            entries = entries.toList(),
            nameOf = { name, _ -> name },
            walletNameOf = { it.wallet.name.orEmpty() },
            target = out,
        )
        assertEquals(entries.size, count)
        return out.toByteArray()
    }

    private fun ByteArray.names(): List<String> = parts().map { it.first }

    private fun ByteArray.sheet(): String = part("xl/worksheets/sheet1.xml")

    private fun ByteArray.workbook(): String = part("xl/workbook.xml")

    private fun ByteArray.part(name: String): String =
        parts().first { it.first == name }.second

    private fun ByteArray.parts(): List<Pair<String, String>> =
        ZipInputStream(inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .map { it.name to zip.readBytes().decodeToString() }
                .toList()
        }
}
