package app.jourjour.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A split on the separator would get most of these wrong, which is the whole reason this
 * exists: the writer quotes a field precisely when its contents would otherwise be read
 * as structure.
 */
class CsvReaderTest {

    @Test
    fun plain_fields_split_on_the_separator() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            CsvReader.read("a,b,c\r\n1,2,3\r\n", ','),
        )
    }

    @Test
    fun quotes_hide_a_separator_and_a_line_break() {
        val text = "a,\"b,still b\",\"two\nlines\"\r\n"

        assertEquals(listOf(listOf("a", "b,still b", "two\nlines")), CsvReader.read(text, ','))
    }

    @Test
    fun a_doubled_quote_is_one_quote() {
        assertEquals(
            listOf(listOf("say \"hi\"", "x")),
            CsvReader.read("\"say \"\"hi\"\"\",x\r\n", ','),
        )
    }

    @Test
    fun empty_fields_are_kept() {
        assertEquals(listOf(listOf("", "", "")), CsvReader.read(",,\r\n", ','))
    }

    @Test
    fun the_encoding_mark_is_stripped() {
        assertEquals(listOf(listOf("a")), CsvReader.read(TripCsv.BOM + "a\r\n", ','))
    }

    @Test
    fun a_last_record_without_a_break_is_not_lost() {
        assertEquals(listOf(listOf("a"), listOf("b")), CsvReader.read("a\r\nb", ','))
    }

    @Test
    fun a_lone_line_feed_separates_records_too() {
        // Not what this app writes, but what a spreadsheet on another system might.
        assertEquals(listOf(listOf("a"), listOf("b")), CsvReader.read("a\nb\n", ','))
    }

    @Test
    fun a_semicolon_as_the_separator() {
        assertEquals(
            listOf(listOf("a", "b,c")),
            CsvReader.read("a;b,c\r\n", ';'),
        )
    }
}
