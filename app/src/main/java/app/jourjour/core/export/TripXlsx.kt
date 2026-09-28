package app.jourjour.core.export

import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.TripWalletDetails
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A trip's entries as a spreadsheet, written by hand.
 *
 * An `.xlsx` is a ZIP of a few XML parts, and Android carries both halves —
 * `ZipOutputStream` and string building. The libraries that would do this instead (Apache
 * POI and the lighter writers) all want StAX, which Android has not got, and POI besides
 * would add megabytes to the app to write a table of seventeen columns.
 *
 * What this is for, over the CSV beside it: a spreadsheet reads it without guessing. There
 * is no separator to get wrong, an amount arrives as a number that sums and a date as a
 * date that sorts. The columns and their order come from [TripTable], shared with the CSV,
 * so neither export can quietly drift from the other.
 */
object TripXlsx {

    const val MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Day zero of the serial numbers a spreadsheet counts dates in. */
    private val EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    /** Styles declared in [STYLES], referred to by index from a cell. */
    private const val STYLE_HEADER = 1
    private const val STYLE_DATE = 2
    private const val STYLE_INTEGER = 3
    private const val STYLE_TWO_DECIMALS = 4
    private const val STYLE_THREE_DECIMALS = 5
    private const val STYLE_TIME = 6

    private const val SECONDS_IN_DAY = 24 * 60 * 60

    /** Enough for a minute, which is 1/1440 of a day, to come back as the minute it was. */
    private const val TIME_SCALE = 10

    /**
     * Column widths, in characters of the default font. What a spreadsheet stores is the
     * characters plus its own padding of five pixels, measured in digits of that font —
     * seven pixels wide in Calibri 11 — and what it shows is the stored width less that
     * padding, so a width written as plain characters comes up short by 0.64 or so.
     */
    private const val WIDTH_PADDING = 5.0 / 7.0
    private const val WIDTH_DATE = 10
    private const val WIDTH_SHORT = 5
    private const val WIDTH_AMOUNT = 12
    private const val WIDTH_TEXT = 18

    /** A sheet name may not carry these, nor run past this length. */
    private const val MAX_SHEET_NAME = 31
    private val FORBIDDEN_IN_SHEET_NAME = charArrayOf('[', ']', ':', '*', '?', '/', '\\')

    /**
     * @param columns which columns the sheet carries, in order. A spreadsheet is read by a
     * person, who is asked what they want in it; the CSV always carries everything.
     * @param titleOf what a column is called. Taken as a function rather than a list, so
     * that a title cannot end up over the wrong column.
     * @param nameOf resolves the stored name and resource key of a dictionary entry.
     * @return how many entries went into the sheet.
     */
    fun write(
        sheetName: String,
        columns: List<TripColumn>,
        titleOf: (TripColumn) -> String,
        entries: List<EntryDetails>,
        nameOf: (name: String, nameKey: String?) -> String,
        walletNameOf: (TripWalletDetails) -> String,
        target: OutputStream,
    ): Int {
        val headers = columns.map(titleOf)
        val rows = TripTable.rows(entries, columns, nameOf, walletNameOf)

        ZipOutputStream(target).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", ROOT_RELS)
            zip.put("xl/workbook.xml", workbook(sheetName))
            zip.put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.put("xl/styles.xml", STYLES)
            zip.put("xl/worksheets/sheet1.xml", sheet(columns, headers, rows))
        }
        return rows.size
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // region The parts of the package

    private val CONTENT_TYPES = flattened(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""",
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""",
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""",
        """<Default Extension="xml" ContentType="application/xml"/>""",
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""",
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""",
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""",
        """</Types>""",
    )

    private val ROOT_RELS = flattened(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""",
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""",
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""",
        """</Relationships>""",
    )

    private val WORKBOOK_RELS = flattened(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""",
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""",
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""",
        """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""",
        """</Relationships>""",
    )

    /**
     * The least a reader will accept: one plain font and one bold, the two fills and the
     * border every workbook has to declare, then the handful of cell formats this sheet
     * uses. Number formats 1 and 2 are built in ("0" and "0.00"); three decimals has to be
     * spelled out, hence the custom 164.
     */
    private val STYLES = flattened(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""",
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""",
        """<numFmts count="1"><numFmt numFmtId="164" formatCode="0.000"/></numFmts>""",
        """<fonts count="2">""",
        """<font><sz val="11"/><name val="Calibri"/></font>""",
        """<font><b/><sz val="11"/><name val="Calibri"/></font>""",
        """</fonts>""",
        """<fills count="2">""",
        """<fill><patternFill patternType="none"/></fill>""",
        """<fill><patternFill patternType="gray125"/></fill>""",
        """</fills>""",
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""",
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""",
        """<cellXfs count="7">""",
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""",
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""",
        """<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""",
        """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""",
        """<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""",
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""",
        // 20 is the built-in "h:mm".
        """<xf numFmtId="20" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""",
        """</cellXfs>""",
        """</styleSheet>""",
    )

    private fun workbook(sheetName: String) = flattened(
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""",
        """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""",
        """<sheets><sheet name="${escaped(asSheetName(sheetName))}" sheetId="1" r:id="rId1"/></sheets>""",
        """</workbook>""",
    )

    private fun sheet(
        columns: List<TripColumn>,
        headers: List<String>,
        rows: List<List<Cell>>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        // The titles stay put while the entries scroll under them.
        append("""<sheetViews><sheetView workbookViewId="0">""")
        append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
        append("""</sheetView></sheetViews>""")
        // Each column as wide as what it holds: a date, a time and a code are short, an
        // amount is a number, and a name or a title needs room not to be cut off the
        // moment the file is opened.
        append("<cols>")
        columns.forEachIndexed { index, column ->
            val number = index + 1
            val width = "%.2f".format(Locale.ROOT, widthOf(column) + WIDTH_PADDING)
            append("""<col min="$number" max="$number" width="$width" customWidth="1"/>""")
        }
        append("</cols>")
        append("<sheetData>")

        append("""<row r="1">""")
        headers.forEachIndexed { index, title ->
            append(inlineString(reference(index, 1), title, STYLE_HEADER))
        }
        append("</row>")

        rows.forEachIndexed { index, cells ->
            val number = index + 2
            append("""<row r="$number">""")
            cells.forEachIndexed { column, cell ->
                append(render(reference(column, number), cell))
            }
            append("</row>")
        }

        append("</sheetData></worksheet>")
    }

    private fun render(reference: String, cell: Cell): String = when (cell) {
        // A cell that is not written at all is the empty one: there is nothing to say.
        Cell.Blank -> ""
        is Cell.Text -> inlineString(reference, cell.value, style = null)
        is Cell.Date -> number(
            reference,
            (cell.value.toEpochDay() - EPOCH.toEpochDay()).toString(),
            STYLE_DATE,
        )
        // A time of day is the fraction of the day gone by, on the same scale as a date:
        // that is what a spreadsheet sorts and formats as a time.
        is Cell.Time -> number(
            reference,
            BigDecimal(cell.value.toSecondOfDay())
                .divide(BigDecimal(SECONDS_IN_DAY), TIME_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString(),
            STYLE_TIME,
        )
        is Cell.Number -> number(reference, cell.value.toPlainString(), styleOf(cell.decimals))
    }

    /** In characters of the default font, the unit a spreadsheet sizes a column in. */
    private fun widthOf(column: TripColumn): Int = when (column) {
        TripColumn.DATE -> WIDTH_DATE
        TripColumn.TIME,
        TripColumn.QUANTITY,
        TripColumn.EXPENSE_CURRENCY,
        TripColumn.INCOME_CURRENCY,
        TripColumn.EXPENSE_SETTLEMENT_CURRENCY,
        TripColumn.INCOME_SETTLEMENT_CURRENCY -> WIDTH_SHORT
        TripColumn.EXPENSE_AMOUNT,
        TripColumn.INCOME_AMOUNT,
        TripColumn.EXPENSE_SETTLEMENT_AMOUNT,
        TripColumn.INCOME_SETTLEMENT_AMOUNT -> WIDTH_AMOUNT
        else -> WIDTH_TEXT
    }

    private fun styleOf(decimals: Int?): Int? = when (decimals) {
        0 -> STYLE_INTEGER
        2 -> STYLE_TWO_DECIMALS
        3 -> STYLE_THREE_DECIMALS
        // A quantity, or a currency with some other number of decimals: let the reader
        // show it as it stands rather than pad it to a length that would be wrong.
        else -> null
    }

    private fun inlineString(reference: String, value: String, style: Int?): String =
        """<c r="$reference"${attribute(style)} t="inlineStr">""" +
            """<is><t xml:space="preserve">${escaped(value)}</t></is></c>"""

    private fun number(reference: String, value: String, style: Int?): String =
        """<c r="$reference"${attribute(style)}><v>$value</v></c>"""

    private fun attribute(style: Int?): String = if (style == null) "" else """ s="$style""""

    // endregion

    /** "A1", "B1" … "AA1": the column letters a spreadsheet counts in. */
    private fun reference(column: Int, row: Int): String {
        val letters = StringBuilder()
        var rest = column
        do {
            letters.insert(0, 'A' + rest % 26)
            rest = rest / 26 - 1
        } while (rest >= 0)
        return "$letters$row"
    }

    private fun asSheetName(name: String): String =
        name.filterNot { it in FORBIDDEN_IN_SHEET_NAME }
            .trim()
            .take(MAX_SHEET_NAME)
            .ifBlank { "Trip" }

    /**
     * XML forbids most control characters outright, so those are dropped rather than
     * escaped: nothing a person types into a title is worth failing the whole file for.
     */
    private fun escaped(value: String): String = buildString {
        value.forEach { character ->
            when {
                character == '&' -> append("&amp;")
                character == '<' -> append("&lt;")
                character == '>' -> append("&gt;")
                character == '"' -> append("&quot;")
                character < ' ' && character !in "\t\n\r" -> Unit
                else -> append(character)
            }
        }
    }

    /** The parts are written as readable lines and shipped as one. */
    private fun flattened(vararg lines: String): String = lines.joinToString("")
}
