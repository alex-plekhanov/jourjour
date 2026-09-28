package app.jourjour.core.export

/**
 * One record of a CSV, RFC 4180 in shape.
 *
 * Shared by the two tables the app writes, so that a rule about quotation marks is decided
 * once. What the separator is comes from outside, from a string resource, because a
 * spreadsheet splits a line on whatever its own locale calls the list separator: a comma
 * in English, a semicolon where the comma is already busy being a decimal point.
 */
object CsvWriter {

    /**
     * Without these three bytes at the front a spreadsheet reads a UTF-8 file as the
     * system codepage, and every Cyrillic name comes out as rubbish.
     */
    const val BOM = "﻿"

    private const val RECORD_END = "\r\n"

    fun row(values: List<String>, separator: String): String =
        values.joinToString(separator, postfix = RECORD_END) { field(it, separator) }

    /**
     * Quoted against the separator actually in use and nothing else. Quoting a comma while
     * the separator is a semicolon would put every amount in quotation marks, and a
     * spreadsheet reads a quoted number as text.
     */
    private fun field(value: String, separator: String): String =
        if (value.any { it.toString() == separator || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
