package app.jourjour.ui.trip

import app.jourjour.R
import app.jourjour.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RateFormTest {

    private val nothingTaken: (String, String) -> Boolean = { _, _ -> false }

    private fun form(
        multiplier: String = "1",
        from: String? = "USD",
        rate: String = "84.78",
    ) = RateForm(multiplier = multiplier, fromCurrencyCode = from, rate = rate, toCurrencyCode = "RUB")

    @Test
    fun `a filled form comes back as numbers`() {
        val checked = form().check(nothingTaken)

        val accepted = checked as RateFormCheck.Accepted
        assertEquals("USD", accepted.fromCurrencyCode)
        assertEquals("RUB", accepted.toCurrencyCode)
        assertEquals(Money.of(BigDecimal("84.78")), accepted.rate)
        assertEquals(1L, accepted.multiplier)
    }

    @Test
    fun `the rate keeps three decimals, whatever the currencies show`() {
        val accepted = form(multiplier = "10", rate = "5.354").check(nothingTaken)
            as RateFormCheck.Accepted

        assertEquals(Money.of(BigDecimal("5.354")), accepted.rate)
        assertEquals(10L, accepted.multiplier)
    }

    @Test
    fun `a currency is required`() {
        val refused = form(from = null).check(nothingTaken) as RateFormCheck.Refused
        assertEquals(R.string.error_currency_required, refused.form.fromError)
    }

    @Test
    fun `a currency cannot be converted to itself`() {
        val refused = form(from = "RUB").check(nothingTaken) as RateFormCheck.Refused
        assertEquals(R.string.error_rate_same_currency, refused.form.fromError)
    }

    @Test
    fun `a pair the trip already carries is refused`() {
        val refused = form().check { from, to -> from == "USD" && to == "RUB" }
            as RateFormCheck.Refused
        assertEquals(R.string.error_rate_duplicate, refused.form.fromError)
    }

    @Test
    fun `the multiplier counts whole units, at least one`() {
        listOf("0", "-2", "", "1.5").forEach { value ->
            val refused = form(multiplier = value).check(nothingTaken) as RateFormCheck.Refused
            assertEquals(
                "multiplier \"$value\"",
                R.string.error_rate_multiplier,
                refused.form.multiplierError,
            )
        }
    }

    @Test
    fun `a rate has to be a number above zero`() {
        listOf("", "0", "-3", "abc").forEach { value ->
            val refused = form(rate = value).check(nothingTaken) as RateFormCheck.Refused
            assertEquals("rate \"$value\"", R.string.error_rate_amount, refused.form.rateError)
        }
    }

    @Test
    fun `an empty note is saved as the default, marked as the lookup's`() {
        val accepted = form().copy(note = "  ", noteDefault = "Fetched")
            .check(nothingTaken) as RateFormCheck.Accepted

        assertEquals("Fetched", accepted.note)
        assertTrue(accepted.noteFromLookup)
    }

    @Test
    fun `a typed note is saved over the default`() {
        val accepted = form().copy(note = " Cash desk ", noteDefault = "Fetched")
            .check(nothingTaken) as RateFormCheck.Accepted

        assertEquals("Cash desk", accepted.note)
        assertTrue(!accepted.noteFromLookup)
    }

    @Test
    fun `a saved stamp opens as the default, a typed note as the note`() {
        val draft = RateDraft(
            fromCurrencyCode = "USD",
            toCurrencyCode = "RUB",
            rate = Money.of(BigDecimal("84.78")),
            multiplier = 1,
            note = "Fetched",
            noteFromLookup = true,
        )

        val stamped = RateForm.of(draft)
        assertEquals("", stamped.note)
        assertEquals("Fetched", stamped.noteDefault)

        val typed = RateForm.of(draft.copy(noteFromLookup = false))
        assertEquals("Fetched", typed.note)
        assertEquals(null, typed.noteDefault)
    }

    @Test
    fun `a lookup needs the currency and nothing else`() {
        assertTrue(form(rate = "").canFetch)
        assertTrue(!form(from = null).canFetch)
        assertTrue(!form().copy(isFetching = true).canFetch)
    }
}
