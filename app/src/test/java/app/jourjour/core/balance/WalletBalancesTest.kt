package app.jourjour.core.balance

import app.jourjour.core.money.Money
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.TripWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The rules of 1.4 and 1.5: what moves a wallet is what the wallet itself was charged, a
 * charge that is not in yet leaves the balance unknown rather than wrong, and a recount is
 * measured against the entries timed up to it.
 */
class WalletBalancesTest {

    private val cash = TripWallet(id = 1, tripId = 1, paymentMethodId = 1, currencyCode = "USD", openingBalanceMilli = 100_000)
    private val card = TripWallet(id = 2, tripId = 1, paymentMethodId = 2, currencyCode = "EUR")
    private val noon = LocalDateTime.of(2026, 9, 14, 12, 0)

    @Test
    fun `opening balance plus what came in less what went out`() {
        val entries = listOf(
            entry(1, noon, expense = cash.id to 30_000),
            entry(2, noon.plusHours(1), income = cash.id to 5_000, incomeCurrency = "USD"),
        )

        assertEquals(Money(75_000), WalletBalances.at(cash, entries).money)
    }

    @Test
    fun `a half paid in another currency waits for the bank`() {
        val entries = listOf(entry(1, noon, expense = card.id to 30_000, expenseCurrency = "USD"))

        val balance = WalletBalances.at(card, entries)

        assertFalse(balance.isKnown)
        assertEquals(1, balance.pending)
        // Nothing moved: the entered amount is in the wrong currency to count.
        assertEquals(Money.ZERO, balance.money)
    }

    @Test
    fun `a settled half moves the wallet by what the bank charged`() {
        val entries = listOf(
            entry(1, noon, expense = card.id to 30_000, expenseCurrency = "USD", expenseSettlement = 27_500),
        )

        val balance = WalletBalances.at(card, entries)

        assertTrue(balance.isKnown)
        assertEquals(Money(-27_500), balance.money)
    }

    @Test
    fun `a recount counts the entries timed up to it, the moment itself included`() {
        val entries = listOf(
            entry(1, noon.minusHours(1), expense = cash.id to 10_000),
            entry(2, noon, expense = cash.id to 20_000),
            entry(3, noon.plusMinutes(1), expense = cash.id to 40_000),
        )

        assertEquals(Money(70_000), WalletBalances.at(cash, entries, moment = noon).money)
        assertEquals(Money(30_000), WalletBalances.at(cash, entries).money)
    }

    @Test
    fun `an exchange leaves one wallet and enters the other`() {
        val entries = listOf(
            entry(1, noon, expense = cash.id to 100_000, income = card.id to 90_000),
        )

        assertEquals(Money.ZERO, WalletBalances.at(cash, entries).money)
        assertEquals(Money(90_000), WalletBalances.at(card, entries).money)
    }

    @Test
    fun `entries of other wallets do not count`() {
        val entries = listOf(entry(1, noon, expense = card.id to 30_000, expenseCurrency = "EUR"))

        assertEquals(Money(100_000), WalletBalances.at(cash, entries).money)
    }

    private fun entry(
        id: Long,
        at: LocalDateTime,
        expense: Pair<Long, Long>? = null,
        expenseCurrency: String = "USD",
        expenseSettlement: Long? = null,
        income: Pair<Long, Long>? = null,
        incomeCurrency: String = "EUR",
    ) = Entry(
        id = id,
        tripId = 1,
        categoryId = 1,
        dateTime = at,
        expenseWalletId = expense?.first,
        expenseAmountMilli = expense?.second,
        expenseCurrencyCode = expenseCurrency.takeIf { expense != null },
        expenseSettlementAmountMilli = expenseSettlement,
        incomeWalletId = income?.first,
        incomeAmountMilli = income?.second,
        incomeCurrencyCode = incomeCurrency.takeIf { income != null },
    )
}
