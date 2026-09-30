package app.jourjour.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import app.jourjour.data.entity.Currency
import java.math.BigDecimal
import java.util.Locale

class MoneyTest {

    private val usd = Currency("USD", "US Dollar", "$", decimals = 2)
    private val jpy = Currency("JPY", "Japanese Yen", "¥", decimals = 0)

    @Test
    fun `an amount is held in thousandths`() {
        assertEquals(1_250_500L, Money.of(BigDecimal("1250.50")).milli)
        assertEquals(9_000_000L, Money.ofUnits(9_000).milli)
    }

    @Test
    fun `adding up does not accumulate error`() {
        val cent = Money.of(BigDecimal("0.01"))
        val sum = (1..100).fold(Money.ZERO) { acc, _ -> acc + cent }
        assertEquals(Money.of(BigDecimal("1.00")), sum)
    }

    @Test
    fun `input takes a dot, a comma and grouping separators`() {
        val expected = Money.of(BigDecimal("1250.50"))
        assertEquals(expected, MoneyFormat.parseAmount("1250.50", usd.decimals))
        assertEquals(expected, MoneyFormat.parseAmount("1250,50", usd.decimals))
        assertEquals(expected, MoneyFormat.parseAmount("1 250,50", usd.decimals))
        assertEquals(expected, MoneyFormat.parseAmount("1 250,50", usd.decimals))
        assertNull(MoneyFormat.parseAmount("", usd.decimals))
        assertNull(MoneyFormat.parseAmount("abc", usd.decimals))
    }

    @Test
    fun `input is rounded to the decimals of the currency`() {
        assertEquals(Money.of(BigDecimal("1250.51")), MoneyFormat.parseAmount("1250.505", 2))
        assertEquals(Money.ofUnits(9_001), MoneyFormat.parseAmount("9000.5", 0))
    }

    @Test
    fun `formatting respects the decimals of the currency`() {
        val amount = Money.of(BigDecimal("1250.5"))
        assertEquals("1,250.50", MoneyFormat.formatAmount(amount, usd.decimals, Locale.US))
        assertEquals("9,000", MoneyFormat.formatAmount(Money.ofUnits(9_000), jpy.decimals, Locale.US))
    }

    /**
     * An amount and its symbol are one figure: a hard space so that no line can break
     * between them, inside a left-to-right isolate so that a symbol in Arabic script
     * neither turns the pair round nor disturbs the text it stands in. The marks are
     * built from their code points rather than pasted in, being invisible either way.
     */
    @Test
    fun `an amount and its symbol are held together`() {
        val isolate = Char(0x2066).toString()
        val popIsolate = Char(0x2069).toString()
        val hardSpace = Char(0x00A0).toString()

        val amount = Money.of(BigDecimal("1250.5"))
        assertEquals(
            isolate + "1,250.50" + hardSpace + "$" + popIsolate,
            MoneyFormat.formatAmountWithCurrency(amount, usd, Locale.US),
        )
        assertEquals(
            isolate + "9,000" + hardSpace + "¥" + popIsolate,
            MoneyFormat.formatAmountWithCurrency(Money.ofUnits(9_000), jpy, Locale.US),
        )
    }

    @Test
    fun `quantity and the unit price derived from it`() {
        val quantity = MoneyFormat.parseQuantity("42,35")!!
        assertEquals(42_350L, quantity.milli)
        assertEquals("42.35", MoneyFormat.formatQuantity(quantity, Locale.US))

        val amount = Money.of(BigDecimal("2117.50"))
        assertEquals(Money.of(BigDecimal("50.00")), unitPrice(amount, quantity))
        assertNull(unitPrice(amount, Quantity.ZERO))
        assertNull(unitPrice(amount, null))
    }

    @Test
    fun `an amount from a field reads back unchanged`() {
        // This is how a wallet holding 50,000 yen turned into 50: the formatter put
        // "50,000" into the field and the parser read the comma as a decimal point.
        val locales = listOf(
            Locale.US,
            Locale.of("ru", "RU"),
            Locale.GERMANY,
            // Arabic-Indic digits and their own decimal sign.
            Locale.of("ar", "EG"),
            // A typographic minus, U+2212, rather than a hyphen.
            Locale.of("sv", "SE"),
        )
        locales.forEach { locale ->
            listOf(
                Money.ofUnits(50_000) to 0,
                Money.of(BigDecimal("1250.50")) to 2,
                Money.of(BigDecimal("1234567.89")) to 2,
                Money.of(BigDecimal("0.75")) to 2,
                Money.of(BigDecimal("-300.00")) to 2,
                Money.of(BigDecimal("12.345")) to 3,
            ).forEach { (amount, decimals) ->
                val text = MoneyFormat.formatAmountForInput(amount, decimals, locale)
                assertEquals(
                    "locale $locale, amount $text",
                    amount,
                    MoneyFormat.parseAmount(text, decimals, locale),
                )
            }
        }
    }

    @Test
    fun `a grouping separator is not mistaken for a decimal point`() {
        val ru = Locale.of("ru", "RU")
        // A comma groups digits in English and marks the decimal in Russian.
        assertEquals(Money.ofUnits(50_000), MoneyFormat.parseAmount("50,000", 0, Locale.US))
        assertEquals(Money.of(BigDecimal("50.5")), MoneyFormat.parseAmount("50,5", 1, ru))
        // Both signs at once: the rightmost one is the decimal.
        assertEquals(Money.of(BigDecimal("1234.56")), MoneyFormat.parseAmount("1,234.56", 2, Locale.US))
        assertEquals(Money.of(BigDecimal("1234.56")), MoneyFormat.parseAmount("1.234,56", 2, ru))
        // A repeated sign groups digits.
        assertEquals(Money.ofUnits(1_234_567), MoneyFormat.parseAmount("1.234.567", 0, ru))
        // A dot typed on a Russian device is still understood as the decimal.
        assertEquals(Money.of(BigDecimal("50.5")), MoneyFormat.parseAmount("50.5", 1, ru))
    }

    @Test
    fun `display and input differ by the grouping separators`() {
        val amount = Money.ofUnits(50_000)
        assertEquals("50,000", MoneyFormat.formatAmount(amount, 0, Locale.US))
        assertEquals("50000", MoneyFormat.formatAmountForInput(amount, 0, Locale.US))
    }

    @Test
    fun `digits of any script and any minus sign are read`() {
        val ar = Locale.of("ar", "EG")
        assertEquals(
            Money.of(BigDecimal("1234.56")),
            MoneyFormat.parseAmount("١٢٣٤٫٥٦", 2, ar),
        )
        // The Arabic formatter puts a letter mark (U+061C) before the minus.
        assertEquals(Money.of(BigDecimal("-300")), MoneyFormat.parseAmount("؜-٣٠٠", 0, ar))
        assertEquals(Money.of(BigDecimal("-300")), MoneyFormat.parseAmount("−300", 0, Locale.US))
    }

    @Test
    fun `an amount too large to hold is refused rather than wrapped`() {
        assertNull(MoneyFormat.parseAmount("99999999999999999999", 2, Locale.US))
        assertNull(MoneyFormat.parseQuantity("1e20", Locale.US))
        assertThrows(ArithmeticException::class.java) { Money.of(BigDecimal("1e20")) }
    }
}
