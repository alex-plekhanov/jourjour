package app.jourjour.ui.trip

import androidx.annotation.StringRes
import app.jourjour.R
import app.jourjour.core.money.Money
import app.jourjour.core.money.MoneyFormat
import java.time.LocalDate

/**
 * The fields of the rate sheet, as they are being typed.
 *
 * Numbers stay strings for as long as they are on screen: "1," and "" are halfway states
 * that are not numbers yet, and the currency the field converts to is fixed, so there is
 * nothing here to reformat behind the person's back.
 */
data class RateForm(
    /** How many units of [fromCurrencyCode] the rate is quoted for. */
    val multiplier: String = "1",
    val fromCurrencyCode: String? = null,
    val rate: String = "",
    /**
     * The far end. Taken from the base currency when the rate is made and from the row
     * itself when an existing one is opened — never rewritten, see [TripRate].
     */
    val toCurrencyCode: String,
    /** What the rate has to say for itself. Written by hand, or stamped by a lookup. */
    val note: String = "",
    /** True while the note is a lookup's stamp, which the next lookup may overwrite. */
    val noteFromLookup: Boolean = false,
    @param:StringRes val multiplierError: Int? = null,
    @param:StringRes val fromError: Int? = null,
    @param:StringRes val rateError: Int? = null,
    /** True while the internet is being asked; the button says so and cannot be pressed twice. */
    val isFetching: Boolean = false,
    /** What the last lookup had to say, good or bad. Cleared as soon as a field is touched. */
    @param:StringRes val lookupError: Int? = null,
    /** The day the fetched rate belongs to, which need not be the day that was asked for. */
    val fetchedDate: LocalDate? = null,
) {
    /** A lookup needs both ends and nothing else; the numbers are what it fills in. */
    val canFetch: Boolean get() = fromCurrencyCode != null && !isFetching

    companion object {
        fun of(rate: RateDraft): RateForm = RateForm(
            multiplier = rate.multiplier.toString(),
            fromCurrencyCode = rate.fromCurrencyCode,
            rate = MoneyFormat.formatRateForInput(rate.rate),
            toCurrencyCode = rate.toCurrencyCode,
            note = rate.note,
            noteFromLookup = rate.noteFromLookup,
        )
    }
}

/** A checked form, or the same form with the reasons it was refused written on it. */
sealed interface RateFormCheck {
    data class Accepted(
        val fromCurrencyCode: String,
        val toCurrencyCode: String,
        val rate: Money,
        val multiplier: Long,
        val note: String,
        val noteFromLookup: Boolean,
    ) : RateFormCheck

    data class Refused(val form: RateForm) : RateFormCheck
}

/**
 * @param pairTaken whether the trip already carries a rate for the pair, the one thing
 * this form cannot see for itself.
 */
fun RateForm.check(pairTaken: (from: String, to: String) -> Boolean): RateFormCheck {
    val multiplierValue = multiplier.trim().toLongOrNull()
    val rateValue = MoneyFormat.parseAmount(rate, Money.SCALE)

    val multiplierError = when {
        multiplierValue == null || multiplierValue < 1 -> R.string.error_rate_multiplier
        else -> null
    }
    val fromError = when {
        fromCurrencyCode == null -> R.string.error_currency_required
        fromCurrencyCode == toCurrencyCode -> R.string.error_rate_same_currency
        pairTaken(fromCurrencyCode, toCurrencyCode) -> R.string.error_rate_duplicate
        else -> null
    }
    val rateError = when {
        rateValue == null || rateValue.milli <= 0 -> R.string.error_rate_amount
        else -> null
    }

    if (multiplierError != null || fromError != null || rateError != null) {
        return RateFormCheck.Refused(
            copy(
                multiplierError = multiplierError,
                fromError = fromError,
                rateError = rateError,
            )
        )
    }
    return RateFormCheck.Accepted(
        fromCurrencyCode = checkNotNull(fromCurrencyCode),
        toCurrencyCode = toCurrencyCode,
        rate = checkNotNull(rateValue),
        multiplier = checkNotNull(multiplierValue),
        note = note.trim(),
        // A note trimmed away to nothing is nobody's, whoever typed it.
        noteFromLookup = noteFromLookup && note.isNotBlank(),
    )
}
