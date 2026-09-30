package app.jourjour.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import app.jourjour.data.entity.Currency

/**
 * Formats and parses amounts and quantities the way the device locale writes numbers.
 * How many decimals to use comes from [Currency.decimals]: JPY has none, KWD has three.
 */
object MoneyFormat {

    /** "1 250.50" — for display, with digit grouping. */
    fun formatAmount(money: Money, decimals: Int, locale: Locale = Locale.getDefault()): String =
        numberFormat(locale, decimals, decimals, grouping = true).format(money.toBigDecimal())

    /**
     * "1250.50" — for putting into an editable field, without digit grouping.
     * Group separators get in the way of editing, and on the way back they are
     * indistinguishable from a decimal point, so a field only ever receives this form.
     */
    fun formatAmountForInput(
        money: Money,
        decimals: Int,
        locale: Locale = Locale.getDefault(),
    ): String = numberFormat(locale, decimals, decimals, grouping = false).format(money.toBigDecimal())

    /**
     * "1 250.50 $" — the amount followed by the currency symbol, as one figure.
     *
     * One figure in two senses, both of which the plain space got wrong for a symbol like
     * the Omani rial's `ر.ع.`:
     *
     * - The space between the two is a hard one, so a line can never break there. A column
     *   was putting the number on one line and what it was on the next.
     * - The pair is wrapped in a bidirectional isolate, so a symbol written right to left
     *   reorders only itself. Without it a comma joining two totals was being dragged to
     *   the wrong side of the Arabic, and a row of figures came out shuffled.
     *
     * Both marks are for showing text with. Nothing written to a file goes through here —
     * an exported table carries the figure and the code in columns of their own.
     */
    fun formatAmountWithCurrency(
        money: Money,
        currency: Currency,
        locale: Locale = Locale.getDefault(),
    ): String = ISOLATE +
        formatAmount(money, currency.decimals, locale) +
        NBSP +
        currency.symbol +
        POP_ISOLATE

    /**
     * "84.78" — an exchange rate, to as many decimals as it actually has.
     *
     * Neither currency decides the shape: a rate is not a sum anybody hands over, and
     * writing it to the two decimals of a dollar would turn 1.8 into 2. So it keeps all
     * three decimals it is stored with and drops the zeros it does not need.
     */
    fun formatRate(rate: Money, locale: Locale = Locale.getDefault()): String =
        numberFormat(locale, 0, Money.SCALE, grouping = true).format(rate.toBigDecimal())

    fun formatRateForInput(rate: Money, locale: Locale = Locale.getDefault()): String =
        numberFormat(locale, 0, Money.SCALE, grouping = false).format(rate.toBigDecimal())

    /** "42.35" — a quantity; trailing zeros are dropped. */
    fun formatQuantity(quantity: Quantity, locale: Locale = Locale.getDefault()): String =
        numberFormat(locale, 0, Quantity.SCALE, grouping = true).format(quantity.toBigDecimal())

    fun formatQuantityForInput(quantity: Quantity, locale: Locale = Locale.getDefault()): String =
        numberFormat(locale, 0, Quantity.SCALE, grouping = false).format(quantity.toBigDecimal())

    /**
     * Parses what a person typed. The result is rounded to [decimals] places so that no
     * fractions a person never saw can settle into the database. Null for anything that
     * is not a number, and for a number too large to hold — the field says so, rather
     * than storing a different amount than was typed.
     */
    fun parseAmount(
        input: String,
        decimals: Int,
        locale: Locale = Locale.getDefault(),
    ): Money? = parseDecimal(input, locale)?.let {
        runCatching { Money.of(it.setScale(decimals, RoundingMode.HALF_UP)) }.getOrNull()
    }

    fun parseQuantity(input: String, locale: Locale = Locale.getDefault()): Quantity? =
        parseDecimal(input, locale)?.let { runCatching { Quantity.of(it) }.getOrNull() }

    /**
     * Turns typed text into a plain number.
     *
     * A dot and a comma mean different things in different countries, so the decimal
     * separator is picked like this:
     *  - both signs present — the rightmost one is the decimal ("1,234.56" and "1.234,56");
     *  - one sign repeated — it groups digits, so drop every occurrence ("1.234.567");
     *  - a single lone sign — it is the decimal unless it is the current locale's group
     *    separator. That is why "50,000" is fifty thousand in English and "50,5" is fifty
     *    and a half in Russian.
     *
     * Digits are taken in whatever script the locale writes them: an Arabic device puts
     * Arabic-Indic digits into a field and expects to read them back. So are the locale's
     * own minus sign and decimal separator — Swedish formats a minus as U+2212, Arabic a
     * decimal as U+066B — which is why a field on those devices could not read its own
     * value before.
     */
    private fun parseDecimal(input: String, locale: Locale): BigDecimal? {
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val cleaned = buildString {
            for (ch in input) {
                when {
                    // NBSP (0x00A0) and narrow NBSP (0x202F) group digits in the Russian
                    // and French locales, and Char.isWhitespace does not catch them.
                    ch.isWhitespace() || ch.code == 0x00A0 || ch.code == 0x202F -> Unit
                    ch == symbols.groupingSeparator -> Unit
                    // Direction marks: the Arabic formatter puts one (U+061C) before a
                    // minus. They say how to lay the text out and are not part of it.
                    ch.category == CharCategory.FORMAT -> Unit
                    ch.isDigit() -> append(Character.digit(ch, 10))
                    ch == symbols.minusSign || ch == '−' -> append('-')
                    // A dot and a comma are told apart below; any other decimal sign the
                    // locale uses can only be the decimal.
                    ch == symbols.decimalSeparator && ch != '.' && ch != ',' -> append('.')
                    else -> append(ch)
                }
            }
        }
        if (cleaned.isEmpty()) return null

        val dotCount = cleaned.count { it == '.' }
        val commaCount = cleaned.count { it == ',' }
        val decimalSeparator: Char? = when {
            dotCount > 0 && commaCount > 0 ->
                if (cleaned.lastIndexOf('.') > cleaned.lastIndexOf(',')) '.' else ','
            dotCount > 1 -> null
            commaCount > 1 -> null
            dotCount == 1 -> '.'
            commaCount == 1 -> ','
            else -> null
        }

        val normalized = buildString {
            for (ch in cleaned) {
                when (ch) {
                    '.', ',' -> if (ch == decimalSeparator) append('.')
                    else -> append(ch)
                }
            }
        }
        return normalized.toBigDecimalOrNull()
    }

    /**
     * The marks that hold an amount and its symbol together, written as the numbers they
     * are: all three are invisible, and a space that is not a space is not something to
     * leave a reader to spot.
     *
     * The isolate is left-to-right rather than "whichever the first letter is": the figure
     * is written the way this app writes one — the number, then what it is in — and a
     * symbol in Arabic script would otherwise turn the pair round and stand in front of
     * the number. What the isolate settles is the text around the figure, not the order
     * inside it.
     */
    private val NBSP = Char(0x00A0)
    private val ISOLATE = Char(0x2066)
    private val POP_ISOLATE = Char(0x2069)

    private fun numberFormat(
        locale: Locale,
        min: Int,
        max: Int,
        grouping: Boolean,
    ): NumberFormat = DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale)).apply {
        minimumFractionDigits = min
        maximumFractionDigits = max
        isGroupingUsed = grouping
        roundingMode = RoundingMode.HALF_UP
    }
}
