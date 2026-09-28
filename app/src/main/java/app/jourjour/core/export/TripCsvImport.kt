package app.jourjour.core.export

import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.TripWalletDetails
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Reads a trip back from a table in the shape [TripCsv] writes — or in part of it.
 *
 * The columns are found by their titles in the first line, in whatever order they come,
 * so a file cut down or rearranged in a spreadsheet still reads. A handful of columns
 * have to be there, the rest may be left out; a column the app has no title for is
 * passed over.
 *
 * Nothing is written until the whole file has been understood. A single row that cannot be
 * made sense of stops the import and is reported with its line number: half an import is
 * worse than none, because there is no telling by eye which half arrived.
 */
object TripCsvImport {

    /** The separators an export can have used, tried against the header in this order. */
    private val SEPARATORS = listOf(',', ';')

    /**
     * What a file has to carry, whatever else it leaves out. The expense half is the one
     * nearly every entry has; the income half is asked for row by row, where the category
     * calls for it. The time is not here: a row without one starts its day, so a file
     * without the column simply starts every day.
     */
    val REQUIRED: List<TripColumn> = listOf(
        TripColumn.DATE,
        TripColumn.CATEGORY,
        TripColumn.EXPENSE_WALLET,
        TripColumn.EXPENSE_AMOUNT,
        TripColumn.EXPENSE_CURRENCY,
    )

    /**
     * The ISO date the export writes, and the day-first forms a spreadsheet saves a date in
     * on a Russian locale — 01.05.2025, or 01.05.25 — which is what a file that has been
     * through one comes back with.
     */
    private val dateFormats: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d.M.uuuu"),
        DateTimeFormatter.ofPattern("d.M.uu"),
    )

    /** What the export writes, and what a person types: seconds are taken if they are there. */
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm[:ss]")

    sealed interface Result {

        /** The entries the file describes, ready to be written as they stand. */
        data class Ready(val entries: List<Entry>) : Result

        /** No arrangement of the separators produced a first line naming any column. */
        data object HeaderMismatch : Result

        /** The first line names some columns, but not all of [REQUIRED]; these are missing. */
        data class MissingColumns(val columns: List<TripColumn>) : Result

        /** A value the dictionaries know nothing about. [line] counts from 1, header included. */
        data class Unknown(val line: Int, val column: TripColumn, val value: String) : Result

        /** A value of the wrong shape: a date that is not a date, a number that is not one. */
        data class Malformed(val line: Int, val column: TripColumn, val value: String) : Result

        /** A value the row's category calls for, and the column is missing or blank. */
        data class Empty(val line: Int, val column: TripColumn) : Result

        /** A value the row's category has no use for: an amount on a half it does not have. */
        data class Unexpected(val line: Int, val column: TripColumn, val value: String) : Result

        /**
         * A currency the wallet cannot be paid in: cash is the currency of the notes in it
         * and nothing else, and [wallet] is such a wallet.
         */
        data class WrongCurrency(
            val line: Int,
            val column: TripColumn,
            val value: String,
            val wallet: TripWalletDetails,
        ) : Result
    }

    /**
     * @param headers the titles this app would write, in [TripColumn.csv] order — the
     *   fixed names the first line of the file is matched against.
     * @param nameOf resolves the stored name and resource key of a dictionary entry, so
     *   that a category is matched by the name the export actually put in the file.
     */
    fun read(
        text: String,
        headers: List<String>,
        tripId: Long,
        categories: List<Category>,
        wallets: List<TripWalletDetails>,
        currencies: List<Currency>,
        nameOf: (name: String, nameKey: String?) -> String,
        walletNameOf: (TripWalletDetails) -> String,
    ): Result {
        val columnOf = TripColumn.csv.zip(headers).associate { (column, title) -> title to column }

        // The separator is whichever one makes the first line name the most columns: the
        // file may have been written on a phone whose locale splits columns differently.
        val (separator, positions) = SEPARATORS
            .map { candidate -> candidate to positions(CsvReader.read(text, candidate).firstOrNull(), columnOf) }
            .maxBy { (_, found) -> found.size }
        if (positions.isEmpty()) return Result.HeaderMismatch
        val missing = REQUIRED.filter { it !in positions }
        if (missing.isNotEmpty()) return Result.MissingColumns(missing)

        val records = CsvReader.read(text, separator).drop(1)
        val reader = RowReader(
            positions = positions,
            decimal = TripCsv.decimalOf(separator.toString()),
            categories = categories.associateBy { nameOf(it.name, it.nameKey) },
            wallets = wallets.associateBy(walletNameOf),
            currencies = currencies.associateBy { it.code },
        )

        val entries = mutableListOf<Entry>()
        records.forEachIndexed { index, fields ->
            // The header is line 1, so the first record is line 2 — what a spreadsheet shows.
            when (val row = reader.read(fields, tripId, line = index + 2)) {
                is Row.Read -> entries += row.entry
                is Row.Problem -> return row.result
            }
        }
        return Result.Ready(entries)
    }

    /** Where each column the first line names is found; a title named twice keeps its first. */
    private fun positions(header: List<String>?, columnOf: Map<String, TripColumn>): Map<TripColumn, Int> {
        val found = mutableMapOf<TripColumn, Int>()
        header?.forEachIndexed { index, title ->
            val column = columnOf[title.trim()] ?: return@forEachIndexed
            found.putIfAbsent(column, index)
        }
        return found
    }

    /** One record, once it has been looked at: an entry, or the first thing wrong with it. */
    private sealed interface Row {
        data class Read(val entry: Entry) : Row
        data class Problem(val result: Result) : Row
    }

    /** Turns one record into an entry, or into the first thing wrong with it. */
    private class RowReader(
        private val positions: Map<TripColumn, Int>,
        private val decimal: Char,
        private val categories: Map<String, Category>,
        private val wallets: Map<String, TripWalletDetails>,
        private val currencies: Map<String, Currency>,
    ) {

        fun read(fields: List<String>, tripId: Long, line: Int): Row {
            // By the position the title had in the first line. A column the file has not
            // got reads as blank everywhere, which is what an optional column left out means.
            fun field(column: TripColumn): String =
                positions[column]?.let { fields.getOrNull(it) }.orEmpty().trim()

            fun wrong(result: Result) = Row.Problem(result)

            val rawDate = field(TripColumn.DATE)
            val date = dateFormats.firstNotNullOfOrNull { format ->
                runCatching { LocalDate.parse(rawDate, format) }.getOrNull()
            }
                ?: return wrong(Result.Malformed(line, TripColumn.DATE, rawDate))

            // A blank time starts the day: a file written by hand need not carry one.
            val rawTime = field(TripColumn.TIME)
            val time = if (rawTime.isEmpty()) {
                LocalTime.MIDNIGHT
            } else {
                runCatching { LocalTime.parse(rawTime, timeFormat) }.getOrNull()
                    ?: return wrong(Result.Malformed(line, TripColumn.TIME, rawTime))
            }

            val rawCategory = field(TripColumn.CATEGORY)
            if (rawCategory.isEmpty()) return wrong(Result.Empty(line, TripColumn.CATEGORY))
            val category = categories[rawCategory]
                ?: return wrong(Result.Unknown(line, TripColumn.CATEGORY, rawCategory))

            val quantity = amount(field(TripColumn.QUANTITY))
            if (quantity is Parsed.Bad) {
                return wrong(Result.Malformed(line, TripColumn.QUANTITY, quantity.value))
            }

            // The category says which halves the row has to carry, exactly as it does on
            // the form: an expense, an income, or — an exchange, money moved — both.
            val expense = half(
                fields = ::field,
                line = line,
                needed = category.isExpense,
                amountColumn = TripColumn.EXPENSE_AMOUNT,
                currencyColumn = TripColumn.EXPENSE_CURRENCY,
                walletColumn = TripColumn.EXPENSE_WALLET,
            )
            if (expense is Half.Wrong) return wrong(expense.problem)

            val income = half(
                fields = ::field,
                line = line,
                needed = category.isIncome,
                amountColumn = TripColumn.INCOME_AMOUNT,
                currencyColumn = TripColumn.INCOME_CURRENCY,
                walletColumn = TripColumn.INCOME_WALLET,
            )
            if (income is Half.Wrong) return wrong(income.problem)

            val expenseGood = expense as Half.Good
            val incomeGood = income as Half.Good

            val expenseSettled = settlement(::field, line, expenseGood, TripColumn.EXPENSE_SETTLEMENT_AMOUNT)
            if (expenseSettled is Settled.Bad) return wrong(expenseSettled.problem)
            val incomeSettled = settlement(::field, line, incomeGood, TripColumn.INCOME_SETTLEMENT_AMOUNT)
            if (incomeSettled is Settled.Bad) return wrong(incomeSettled.problem)

            val rawPoint = field(TripColumn.COORDINATES)
            val point = point(rawPoint)
                ?: return wrong(Result.Malformed(line, TripColumn.COORDINATES, rawPoint))

            return Row.Read(
                Entry(
                    tripId = tripId,
                    categoryId = category.id,
                    dateTime = date.atTime(time),
                    title = field(TripColumn.TITLE),
                    place = field(TripColumn.PLACE),
                    notes = field(TripColumn.NOTES),
                    // Only where the category counts something. A file written by hand can
                    // carry a 1 in every row, and stored under a category that counts
                    // nothing it would be a figure the form never shows and nobody can
                    // clear, which the totals would then add up.
                    quantityMilli = (quantity as Parsed.Good).value
                        ?.takeIf { category.trackQuantity }
                        ?.let { milli(it, SCALE) },
                    expenseWalletId = expenseGood.wallet?.wallet?.id,
                    expenseAmountMilli = expenseGood.milli,
                    expenseCurrencyCode = expenseGood.currency?.code,
                    incomeWalletId = incomeGood.wallet?.wallet?.id,
                    incomeAmountMilli = incomeGood.milli,
                    incomeCurrencyCode = incomeGood.currency?.code,
                    expenseSettlementAmountMilli = (expenseSettled as Settled.Good).milli,
                    incomeSettlementAmountMilli = (incomeSettled as Settled.Good).milli,
                    latitude = point.first,
                    longitude = point.second,
                )
            )
        }

        /**
         * A half the category has must be there whole — amount, currency and wallet, as the
         * form requires them; a half it has not got must be blank, since an amount on it
         * would be money going somewhere the entry cannot show. The wallet's rules hold
         * here as on the form: a wallet of a method that fixes its currency takes that
         * currency only, and the amount is rounded to the decimals its currency has.
         */
        private fun half(
            fields: (TripColumn) -> String,
            line: Int,
            needed: Boolean,
            amountColumn: TripColumn,
            currencyColumn: TripColumn,
            walletColumn: TripColumn,
        ): Half {
            val rawAmount = fields(amountColumn)
            val amount = when (val parsed = amount(rawAmount)) {
                is Parsed.Bad -> return Half.Wrong(Result.Malformed(line, amountColumn, parsed.value))
                is Parsed.Good -> parsed.value
            }
            if (amount == null) {
                return if (needed) Half.Wrong(Result.Empty(line, amountColumn)) else Half.Good(null, null, null)
            }
            if (!needed) return Half.Wrong(Result.Unexpected(line, amountColumn, rawAmount))

            val rawCurrency = fields(currencyColumn)
            if (rawCurrency.isEmpty()) return Half.Wrong(Result.Empty(line, currencyColumn))
            val currency = currencies[rawCurrency]
                ?: return Half.Wrong(Result.Unknown(line, currencyColumn, rawCurrency))

            val rawWallet = fields(walletColumn)
            if (rawWallet.isEmpty()) return Half.Wrong(Result.Empty(line, walletColumn))
            val wallet = wallets[rawWallet]
                ?: return Half.Wrong(Result.Unknown(line, walletColumn, rawWallet))
            if (wallet.paymentMethod.fixedCurrency && wallet.currency.code != currency.code) {
                return Half.Wrong(Result.WrongCurrency(line, currencyColumn, rawCurrency, wallet))
            }

            return Half.Good(milli(amount, currency.decimals), currency, wallet)
        }

        /**
         * What the bank charged for a half paid in some other currency than the wallet's.
         * It is always in the wallet's own currency, so it needs no currency read back and
         * is rounded to the wallet's decimals; on a half paid in the wallet's own currency
         * it has no meaning, and a value there is refused as the form would refuse it.
         */
        private fun settlement(
            fields: (TripColumn) -> String,
            line: Int,
            half: Half.Good,
            column: TripColumn,
        ): Settled {
            val raw = fields(column)
            val amount = when (val parsed = amount(raw)) {
                is Parsed.Bad -> return Settled.Bad(Result.Malformed(line, column, parsed.value))
                is Parsed.Good -> parsed.value ?: return Settled.Good(null)
            }
            val wallet = half.wallet
            if (wallet == null || half.currency?.code == wallet.currency.code) {
                return Settled.Bad(Result.Unexpected(line, column, raw))
            }
            return Settled.Good(milli(amount, wallet.currency.decimals))
        }

        /** A number as written, or nothing for a blank field; the scale comes later. */
        private fun amount(value: String): Parsed {
            if (value.isEmpty()) return Parsed.Good(null)
            val plain = value.replace(decimal, '.')
            val parsed = runCatching { BigDecimal(plain) }.getOrNull() ?: return Parsed.Bad(value)
            return Parsed.Good(parsed)
        }

        /**
         * Thousandths, the scale everything is held in, of a value first rounded to the
         * [decimals] it is allowed — the form does the same, so that no fraction a person
         * never saw can settle into the database.
         */
        private fun milli(value: BigDecimal, decimals: Int): Long =
            value.setScale(decimals, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP)
                .movePointRight(SCALE)
                .longValueExact()

        /** "55.755826, 37.617300", or nothing at all. Null means the field was wrong. */
        private fun point(value: String): Pair<Double?, Double?>? {
            if (value.isEmpty()) return null to null
            val parts = value.split(',').map { it.trim() }
            if (parts.size != 2) return null
            val latitude = parts[0].toDoubleOrNull() ?: return null
            val longitude = parts[1].toDoubleOrNull() ?: return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return latitude to longitude
        }
    }

    /** A number that read cleanly — or nothing, for a blank field — or the text that did not. */
    private sealed interface Parsed {
        data class Good(val value: BigDecimal?) : Parsed
        data class Bad(val value: String) : Parsed
    }

    /** A settled amount once it has been looked at beside its half. */
    private sealed interface Settled {
        data class Good(val milli: Long?) : Settled
        data class Bad(val problem: Result) : Settled
    }

    /** One half of an entry once its three fields have been looked at together. */
    private sealed interface Half {
        data class Good(
            val milli: Long?,
            val currency: Currency?,
            val wallet: TripWalletDetails?,
        ) : Half

        data class Wrong(val problem: Result) : Half
    }

    /** Amounts and quantities are held as whole thousandths. */
    private const val SCALE = 3
}
