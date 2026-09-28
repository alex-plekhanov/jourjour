package app.jourjour.core.balance

import app.jourjour.core.money.Money
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.TripWallet
import java.time.LocalDateTime

/**
 * What a wallet holds, and how much of that could not be worked out.
 *
 * [money] is the opening balance plus what came in and less what went out. [pending]
 * counts the halves paid in some other currency whose settled amount is not in yet: each
 * moved the wallet by whatever the bank charged, and until that has been written down the
 * figure is a guess dressed up as a total — which is why it is shown only while [isKnown].
 */
data class Balance(val money: Money, val pending: Int) {
    val isKnown: Boolean get() = pending == 0
}

/**
 * A balance is never stored. It is worked out from the entries every time it is shown, so
 * that correcting an entry corrects the balance with it — and so that a recount, which
 * keeps only what was counted, is measured against the records as they stand now.
 */
object WalletBalances {

    /**
     * What [wallet] held at [moment], counting the entries timed up to and including it;
     * null counts them all. The cut-off is by the entry's own time, so an expense
     * remembered in the evening and put back to the morning falls on the far side of a
     * lunchtime recount, where it happened.
     */
    fun at(wallet: TripWallet, entries: List<Entry>, moment: LocalDateTime? = null): Balance {
        var money = Money(wallet.openingBalanceMilli)
        var pending = 0

        entries.forEach { entry ->
            if (moment != null && entry.dateTime > moment) return@forEach
            if (entry.expenseWalletId == wallet.id) {
                val charged = charged(
                    wallet.currencyCode, entry.expenseCurrencyCode,
                    entry.expenseAmountMilli, entry.expenseSettlementAmountMilli,
                )
                if (charged == null) pending++ else money -= charged
            }
            if (entry.incomeWalletId == wallet.id) {
                val charged = charged(
                    wallet.currencyCode, entry.incomeCurrencyCode,
                    entry.incomeAmountMilli, entry.incomeSettlementAmountMilli,
                )
                if (charged == null) pending++ else money += charged
            }
        }
        return Balance(money, pending)
    }

    /**
     * What the wallet itself gave up or took in. Paid in its own currency that is the
     * amount entered; paid in another, only the bank knows, and until that has been
     * written down on the reconciliation screen there is nothing to move the balance by.
     */
    fun charged(
        walletCode: String,
        code: String?,
        amountMilli: Long?,
        settlementMilli: Long?,
    ): Money? {
        val amount = amountMilli ?: return Money.ZERO
        if (code == walletCode) return Money(amount)
        return settlementMilli?.let { Money(it) }
    }
}
