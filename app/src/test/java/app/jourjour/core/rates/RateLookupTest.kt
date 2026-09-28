package app.jourjour.core.rates

import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class RateLookupTest {

    @Test
    fun `a reply is read into a rate and the day it belongs to`() {
        val fetched = parseRateReply(
            """{"date":"2026-09-20","base":"USD","quote":"RUB","rate":84.38}"""
        ).getOrThrow()

        assertEquals(Money.of(BigDecimal("84.38")), fetched.rate)
        assertEquals(1L, fetched.multiplier)
        assertEquals(LocalDate.of(2026, 9, 20), fetched.date)
    }

    @Test
    fun `a rate below one is quoted per ten, hundred or thousand units`() {
        // The published figure to three decimals would be 0.009, which is not a rate
        // anybody could read, let alone reckon with.
        val yen = parseRateReply(
            """{"date":"1999-01-04","base":"JPY","quote":"USD","rate":0.00878}"""
        ).getOrThrow()
        assertEquals(1000L, yen.multiplier)
        assertEquals(Money.of(BigDecimal("8.78")), yen.rate)

        val kip = parseRateReply(
            """{"date":"2026-09-26","base":"LAK","quote":"RUB","rate":0.0038}"""
        ).getOrThrow()
        assertEquals(1000L, kip.multiplier)
        assertEquals(Money.of(BigDecimal("3.8")), kip.rate)

        val riel = parseRateReply(
            """{"date":"2026-09-26","base":"KHR","quote":"RUB","rate":0.02084}"""
        ).getOrThrow()
        assertEquals(100L, riel.multiplier)
        assertEquals(Money.of(BigDecimal("2.084")), riel.rate)
    }

    @Test
    fun `scaling stops as soon as the rate has a whole part`() {
        val (scaled, multiplier) = quoteInWholeUnits(BigDecimal("0.5354"))
        assertEquals(10L, multiplier)
        assertEquals(0, BigDecimal("5.354").compareTo(scaled))
    }

    @Test
    fun `a rate of one or more is left as it is`() {
        val (scaled, multiplier) = quoteInWholeUnits(BigDecimal("1.8"))
        assertEquals(1L, multiplier)
        assertEquals(0, BigDecimal("1.8").compareTo(scaled))
    }

    @Test
    fun `an unreadable reply is a failure rather than a wrong rate`() {
        assertTrue(parseRateReply("not json").isFailure)
        assertTrue(parseRateReply("""{"date":"2026-09-20"}""").isFailure)
        assertTrue(parseRateReply("""{"rate":"oops","date":"2026-09-20"}""").isFailure)
    }

    @Test
    fun `a rate keeps the decimals it has and no more`() {
        val locale = Locale.forLanguageTag("en")
        assertEquals("84.78", MoneyFormat.formatRate(Money.of(BigDecimal("84.78")), locale))
        assertEquals("147", MoneyFormat.formatRate(Money.of(BigDecimal("147")), locale))
        // The two decimals of a dollar would have made this 2, an eleventh out.
        assertEquals("1.8", MoneyFormat.formatRate(Money.of(BigDecimal("1.8")), locale))
        assertEquals("5.354", MoneyFormat.formatRate(Money.of(BigDecimal("5.354")), locale))
    }
}
