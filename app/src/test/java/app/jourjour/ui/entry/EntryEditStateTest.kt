package app.jourjour.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.jourjour.core.money.Money
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripWallet
import app.jourjour.data.entity.TripWalletDetails
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The rule that decides which currency an entry may use — the reason a wallet exists at
 * all. Checked away from any screen.
 */
class EntryEditStateTest {

    private val usd = Currency("USD", "US Dollar", "$")
    private val jpy = Currency("JPY", "Japanese Yen", "¥", decimals = 0)
    private val eur = Currency("EUR", "Euro", "€")
    private val currencies = listOf(usd, jpy, eur)

    private val cash = PaymentMethod(id = 1, name = "Cash", icon = "💵", fixedCurrency = true)
    private val card = PaymentMethod(id = 2, name = "Card", icon = "💳")

    private val trip = Trip(
        id = 1,
        name = "Japan",
        startDate = LocalDate.of(2026, 4, 3),
        endDate = LocalDate.of(2026, 4, 17),
    )

    private fun wallet(id: Long, paymentMethod: PaymentMethod, currency: Currency) =
        TripWalletDetails(
            wallet = TripWallet(
                id = id,
                tripId = trip.id,
                paymentMethodId = paymentMethod.id,
                currencyCode = currency.code,
            ),
            paymentMethod = paymentMethod,
            currency = currency,
        )

    private val cashJpy = wallet(1, cash, jpy)
    private val cashUsd = wallet(2, cash, usd)
    private val cardUsd = wallet(3, card, usd)

    private fun state(
        wallets: List<TripWalletDetails> = listOf(cashJpy, cashUsd, cardUsd),
        categories: List<Category> = emptyList(),
    ) = EntryEditState(
        currencies = currencies,
        wallets = wallets,
        trip = trip,
        categories = categories,
    )

    @Test
    fun `a cash wallet may only hold the currency it is`() {
        val state = state()
        assertEquals(listOf(jpy), state.currencyOptions(cashJpy.wallet.id))
        assertTrue(state.currencyIsLocked(cashJpy.wallet.id))
    }

    @Test
    fun `a card may be paid with in any currency`() {
        // The bank converts, so the whole dictionary is open.
        val state = state()
        assertEquals(currencies, state.currencyOptions(cardUsd.wallet.id))
        assertFalse(state.currencyIsLocked(cardUsd.wallet.id))
    }

    @Test
    fun `with no wallet there is nothing to choose from`() {
        // A trip whose first entry is being written before any wallet exists.
        val state = state(wallets = emptyList())
        assertEquals(emptyList<Currency>(), state.currencyOptions(null))
        assertTrue(state.currencyIsLocked(null))
    }

    @Test
    fun `the currencies a card offers come recently used first`() {
        val state = state().copy(
            currencies = listOf(
                usd.copy(usedAt = LocalDateTime.of(2026, 4, 3, 9, 0)),
                jpy,
                eur.copy(usedAt = LocalDateTime.of(2026, 4, 5, 9, 0)),
            )
        )
        // The picker itself puts the recent ones on top; the options are the dictionary.
        assertEquals(listOf("USD", "JPY", "EUR"), state.currencyOptions(cardUsd.wallet.id).map { it.code })
    }

    @Test
    fun `the last currency anything was paid in is the one a card opens on`() {
        val state = state().copy(
            currencies = listOf(
                usd.copy(usedAt = LocalDateTime.of(2026, 4, 3, 9, 0)),
                jpy.copy(usedAt = LocalDateTime.of(2026, 4, 5, 9, 0)),
                eur,
            )
        )
        // Not the currency of the card's own account: a card is used where one is standing.
        assertEquals("JPY", state.lastUsedCurrency?.code)
    }

    @Test
    fun `a card falls back on its own currency while nothing has been paid in yet`() {
        assertNull(state().lastUsedCurrency)
    }

    @Test
    fun `nothing is settled while the currency paid in is the wallet's own`() {
        val state = state()
        val block = AmountBlock(walletId = cashJpy.wallet.id, currencyCode = "JPY")
        assertNull(state.settlementCurrency(block))
    }

    @Test
    fun `paying a card in another currency leaves the account to be settled`() {
        val state = state()
        val block = AmountBlock(walletId = cardUsd.wallet.id, currencyCode = "JPY")
        assertEquals(usd, state.settlementCurrency(block))
    }

    @Test
    fun `the unit price comes from whichever half is filled in`() {
        val fuel = Category(id = 10, name = "Fuel", icon = "⛽", trackQuantity = true)
        val state = state(categories = listOf(fuel)).copy(
            categoryId = fuel.id,
            quantity = "30",
            expense = AmountBlock(
                walletId = cashJpy.wallet.id, amount = "5400", currencyCode = "JPY",
            ),
        )
        val (price, currency) = requireNotNull(state.unitPrice)
        assertEquals(Money.of(BigDecimal("180")), price)
        assertEquals(jpy, currency)
    }

    @Test
    fun `no unit price is shown without a quantity or an amount`() {
        val fuel = Category(id = 10, name = "Fuel", icon = "⛽", trackQuantity = true)
        val base = state(categories = listOf(fuel)).copy(categoryId = fuel.id)
        assertNull(base.copy(quantity = "30").unitPrice)
        assertNull(
            base.copy(
                expense = AmountBlock(amount = "5400", currencyCode = "JPY"),
            ).unitPrice
        )
        // A category that does not track quantity shows no price at all.
        val dining = Category(id = 11, name = "Dining", icon = "🍽")
        assertNull(
            state(categories = listOf(dining)).copy(
                categoryId = dining.id,
                quantity = "30",
                expense = AmountBlock(amount = "5400", currencyCode = "JPY"),
            ).unitPrice
        )
    }
}
