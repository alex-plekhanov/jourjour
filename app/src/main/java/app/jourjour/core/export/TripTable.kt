package app.jourjour.core.export

import app.jourjour.core.location.Coordinates
import app.jourjour.core.money.Money
import app.jourjour.core.money.Quantity
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.EntryDetails
import app.jourjour.data.entity.countedQuantityMilli
import app.jourjour.data.entity.TripWalletDetails
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * The columns a trip is written out in, in the order they appear.
 *
 * Every export goes through this list, so a new column reaches CSV and XLSX at once and in
 * the same place. The titles live outside — a screen resource decides what a column is
 * called, and matching one to every entry of this enum is what the compiler checks.
 */
enum class TripColumn {
    DATE,
    TIME,
    CATEGORY,
    TITLE,
    QUANTITY,
    EXPENSE_AMOUNT,
    EXPENSE_CURRENCY,
    EXPENSE_WALLET,
    EXPENSE_PAYMENT_METHOD,
    INCOME_AMOUNT,
    INCOME_CURRENCY,
    INCOME_WALLET,
    INCOME_PAYMENT_METHOD,
    EXPENSE_SETTLEMENT_AMOUNT,
    EXPENSE_SETTLEMENT_CURRENCY,
    INCOME_SETTLEMENT_AMOUNT,
    INCOME_SETTLEMENT_CURRENCY,
    PLACE,
    COORDINATES,
    NOTES;

    companion object {
        /**
         * What a CSV carries. The payment method is left out of it: a wallet already
         * names one, and an import has to find a wallet, not guess at a pair. A
         * spreadsheet is read by people rather than by this app, so it may carry both.
         */
        val csv: List<TripColumn> =
            entries - setOf(EXPENSE_PAYMENT_METHOD, INCOME_PAYMENT_METHOD)
    }
}

/**
 * One value on its way out, with enough type left on it to survive into a spreadsheet.
 *
 * A CSV turns all of these back into text; XLSX keeps them apart, so an amount stays a
 * number that can be summed and a date stays a date that can be sorted.
 */
sealed interface Cell {

    data object Blank : Cell

    data class Text(val value: String) : Cell

    /** @param decimals how many to show, or null to let the reader decide. */
    data class Number(val value: BigDecimal, val decimals: Int?) : Cell

    data class Date(val value: LocalDate) : Cell

    data class Time(val value: LocalTime) : Cell
}

/** Turns entries into rows of [Cell]s, one per [TripColumn]. */
object TripTable {

    /** How many decimals an amount gets when its currency somehow did not come along. */
    private const val DEFAULT_DECIMALS = 2

    /**
     * Oldest first: a trip reads forwards, while the screen shows the latest on top.
     *
     * @param columns which to write and in what order. The caller decides — a spreadsheet
     * is asked what it should carry — but the titles beside the rows have to be the same
     * list, or the two would not line up.
     */
    fun rows(
        entries: List<EntryDetails>,
        columns: List<TripColumn> = TripColumn.entries,
        nameOf: (name: String, nameKey: String?) -> String,
        walletNameOf: (TripWalletDetails) -> String,
    ): List<List<Cell>> = entries
        .sortedBy { it.entry.dateTime }
        .map { details -> columns.map { cell(it, details, nameOf, walletNameOf) } }

    private fun cell(
        column: TripColumn,
        details: EntryDetails,
        nameOf: (String, String?) -> String,
        walletNameOf: (TripWalletDetails) -> String,
    ): Cell {
        val entry = details.entry
        return when (column) {
            TripColumn.DATE -> Cell.Date(entry.dateTime.toLocalDate())
            TripColumn.TIME -> Cell.Time(entry.dateTime.toLocalTime())
            TripColumn.CATEGORY -> text(nameOf(details.category.name, details.category.nameKey))
            TripColumn.TITLE -> text(entry.title)
            TripColumn.QUANTITY -> quantity(details.countedQuantityMilli)

            TripColumn.EXPENSE_AMOUNT ->
                amount(entry.expenseAmountMilli, details.expenseCurrency)
            TripColumn.EXPENSE_CURRENCY -> text(details.expenseCurrency?.code)
            TripColumn.EXPENSE_WALLET -> text(details.expenseWallet?.let(walletNameOf))
            TripColumn.EXPENSE_PAYMENT_METHOD -> text(
                details.expenseWallet?.paymentMethod?.let { nameOf(it.name, it.nameKey) }
            )

            TripColumn.INCOME_AMOUNT ->
                amount(entry.incomeAmountMilli, details.incomeCurrency)
            TripColumn.INCOME_CURRENCY -> text(details.incomeCurrency?.code)
            TripColumn.INCOME_WALLET -> text(details.incomeWallet?.let(walletNameOf))
            TripColumn.INCOME_PAYMENT_METHOD -> text(
                details.incomeWallet?.paymentMethod?.let { nameOf(it.name, it.nameKey) }
            )

            // A settled amount is always in the wallet's own currency, so the column is
            // written from the wallet rather than stored beside the amount.
            TripColumn.EXPENSE_SETTLEMENT_AMOUNT ->
                amount(entry.expenseSettlementAmountMilli, details.expenseWallet?.currency)
            TripColumn.EXPENSE_SETTLEMENT_CURRENCY -> text(
                details.expenseWallet?.currency?.code
                    ?.takeIf { entry.expenseSettlementAmountMilli != null }
            )
            TripColumn.INCOME_SETTLEMENT_AMOUNT ->
                amount(entry.incomeSettlementAmountMilli, details.incomeWallet?.currency)
            TripColumn.INCOME_SETTLEMENT_CURRENCY -> text(
                details.incomeWallet?.currency?.code
                    ?.takeIf { entry.incomeSettlementAmountMilli != null }
            )

            TripColumn.PLACE -> text(entry.place)
            TripColumn.COORDINATES -> coordinates(entry.latitude, entry.longitude)
            TripColumn.NOTES -> text(entry.notes)
        }
    }

    private fun text(value: String?): Cell =
        if (value.isNullOrEmpty()) Cell.Blank else Cell.Text(value)

    private fun amount(milli: Long?, currency: Currency?): Cell =
        if (milli == null) {
            Cell.Blank
        } else {
            Cell.Number(Money(milli).toBigDecimal(), currency?.decimals ?: DEFAULT_DECIMALS)
        }

    /** No fixed number of decimals: two litres should not read "2.000". */
    private fun quantity(milli: Long?): Cell =
        if (milli == null) {
            Cell.Blank
        } else {
            Cell.Number(Quantity(milli).toBigDecimal().stripTrailingZeros(), null)
        }

    /**
     * One text field rather than two numbers. A pair of coordinates is read and pasted as
     * a whole, and it is written with a dot wherever it goes — a comma standing in for the
     * decimal point would make it unusable in a map.
     */
    private fun coordinates(latitude: Double?, longitude: Double?): Cell =
        if (latitude == null || longitude == null) {
            Cell.Blank
        } else {
            Cell.Text(Coordinates(latitude, longitude).format())
        }
}
