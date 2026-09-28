package app.jourjour.ui.budget

import app.jourjour.data.entity.Currency

/**
 * The currency converter, as it stands on the screen.
 *
 * It converts through the trip's own rates and nothing else, so what it offers is what the
 * trip has rates for, plus the base currency those rates point at. Two amounts and two
 * currencies, all four of them typed into or chosen: whichever is touched, the other
 * amount follows.
 *
 * [currencies] empty means the trip has no rate at all; [base] null means nothing says
 * what the rates would point at. Either way there is nothing to convert with, and the
 * sheet says which of the two it is.
 */
data class ConverterSheet(
    val currencies: List<Currency> = emptyList(),
    val base: Currency? = null,
    val fromCode: String? = null,
    val toCode: String? = null,
    /** Typed text, as the money fields hold it everywhere else. */
    val from: String = "",
    val to: String = "",
) {
    val fromCurrency: Currency? get() = currencies.firstOrNull { it.code == fromCode }

    val toCurrency: Currency? get() = currencies.firstOrNull { it.code == toCode }

    /** Whether there is anything to convert with at all. */
    val canConvert: Boolean get() = base != null && currencies.size > 1
}
