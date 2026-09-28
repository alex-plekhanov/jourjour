package app.jourjour.core.export

/**
 * Reads back what [TripCsv] writes.
 *
 * RFC 4180 to the letter, which a split on the separator would not be: a field in quotes
 * may hold the separator, a line break, or a quotation mark of its own written twice. The
 * records come back exactly as they were, so a title with a comma in it survives the round
 * trip.
 */
object CsvReader {

    /**
     * @return one list of fields per record. A file that ends with a line break does not
     * produce an empty record after it.
     */
    fun read(text: String, separator: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(record)
            record = mutableListOf()
        }

        val body = text.removePrefix(TripCsv.BOM)
        while (index < body.length) {
            val character = body[index]
            when {
                quoted && character == '"' ->
                    // Two in a row are one quotation mark; a single one ends the field.
                    if (body.getOrNull(index + 1) == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                    }

                quoted -> field.append(character)

                character == '"' && field.isEmpty() -> quoted = true

                character == separator -> endField()

                character == '\r' -> {
                    endRecord()
                    // CRLF is one break, and so is a lone CR from an older writer.
                    if (body.getOrNull(index + 1) == '\n') index++
                }

                character == '\n' -> endRecord()

                else -> field.append(character)
            }
            index++
        }

        // Whatever is left over is the last record, unless the file ended on a break.
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
        return records
    }
}
