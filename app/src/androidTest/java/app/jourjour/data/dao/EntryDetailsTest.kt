package app.jourjour.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.BalanceSnapshot
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripWallet
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * An entry has two relations to the same table twice over — the expense and income
 * wallets, the expense and income currencies. Such relations are easy to wire up the wrong
 * way round, so each one is checked to bring back its own row. The wallets are nested
 * relations of their own, which is one more thing that can be crossed.
 */
@RunWith(AndroidJUnit4::class)
class EntryDetailsTest {

    private lateinit var db: AppDatabase
    private var tripId: Long = 0
    private var exchangeId: Long = 0
    private var cashJpy: Long = 0
    private var cardUsd: Long = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()

        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        db.currencyDao().insert(Currency("JPY", "Japanese Yen", "¥", decimals = 0))
        val cashId = db.paymentMethodDao().insert(PaymentMethod(name = "Cash", icon = "💵", fixedCurrency = true))
        val cardId = db.paymentMethodDao().insert(
            PaymentMethod(name = "Card", icon = "💳")
        )
        exchangeId = db.categoryDao().insert(
            Category(name = "Currency exchange", icon = "💱", isExpense = true, isIncome = true)
        )
        tripId = db.tripDao().insert(
            Trip(
                name = "Japan",
                startDate = LocalDate.of(2026, 4, 3),
                endDate = LocalDate.of(2026, 4, 17),
            )
        )
        cashJpy = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = cashId, currencyCode = "JPY")
        )
        cardUsd = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = cardId, currencyCode = "USD")
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun a_currency_exchange_pulls_in_both_halves() = runBlocking {
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 4, 10, 0),
                expenseWalletId = cardUsd,
                expenseAmountMilli = 100_000,
                expenseCurrencyCode = "USD",
                incomeWalletId = cashJpy,
                incomeAmountMilli = 15_000_000,
                incomeCurrencyCode = "JPY",
            )
        )

        val details = db.entryDao().observeByTrip(tripId).first().single()
        assertEquals("Currency exchange", details.category.name)
        assertEquals("Card", details.expenseWallet?.paymentMethod?.name)
        assertEquals("Cash", details.incomeWallet?.paymentMethod?.name)
        assertEquals("USD", details.expenseWallet?.currency?.code)
        assertEquals("JPY", details.incomeWallet?.currency?.code)
        assertEquals("USD", details.expenseCurrency?.code)
        assertEquals("JPY", details.incomeCurrency?.code)
        // Decimals come from each half's own currency, not from the neighbouring one.
        assertEquals(2, details.expenseCurrency?.decimals)
        assertEquals(0, details.incomeCurrency?.decimals)
    }

    @Test
    fun an_expense_only_entry_has_no_income_relations() = runBlocking {
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 4, 12, 0),
                expenseWalletId = cashJpy,
                expenseAmountMilli = 1_200_000,
                expenseCurrencyCode = "JPY",
            )
        )

        val details = db.entryDao().observeByTrip(tripId).first().single()
        assertEquals("Cash", details.expenseWallet?.paymentMethod?.name)
        assertNull(details.incomeWallet)
        assertNull(details.incomeCurrency)
    }

    @Test
    fun entries_come_newest_first() = runBlocking {
        listOf(
            LocalDateTime.of(2026, 4, 4, 9, 0),
            LocalDateTime.of(2026, 4, 6, 9, 0),
            LocalDateTime.of(2026, 4, 5, 9, 0),
        ).forEach { moment ->
            db.entryDao().insert(
                Entry(
                    tripId = tripId,
                    categoryId = exchangeId,
                    dateTime = moment,
                    expenseWalletId = cashJpy,
                    expenseAmountMilli = 1_000_000,
                    expenseCurrencyCode = "JPY",
                )
            )
        }

        val dates = db.entryDao().observeByTrip(tripId).first().map { it.entry.dateTime.toLocalDate() }
        assertEquals(
            listOf(
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 5),
                LocalDate.of(2026, 4, 4),
            ),
            dates,
        )
    }

    @Test
    fun the_settled_amount_is_set_and_cleared_apart_from_the_entry() = runBlocking {
        val entryId = db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 4, 12, 0),
                expenseWalletId = cardUsd,
                expenseAmountMilli = 3_000_000,
                expenseCurrencyCode = "JPY",
            )
        )

        db.entryDao().setExpenseSettlement(entryId, 20_150)
        db.entryDao().getById(entryId)!!.let { entry ->
            assertEquals(20_150L, entry.expenseSettlementAmountMilli)
            // The statement leaves the rest of the entry alone.
            assertEquals(3_000_000L, entry.expenseAmountMilli)
            assertEquals("JPY", entry.expenseCurrencyCode)
        }

        // An emptied field clears the reconciliation: it may have been a mistake.
        db.entryDao().setExpenseSettlement(entryId, null)
        assertNull(db.entryDao().getById(entryId)!!.expenseSettlementAmountMilli)
    }

    @Test
    fun a_wallet_remembers_the_currency_it_was_last_paid_in() = runBlocking {
        assertNull(db.entryDao().lastCurrencyForWallet(cardUsd))

        // The card is charged in dollars, then in yen. The later one is the answer, and
        // the other wallet's entries are none of its business.
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 4, 10, 0),
                expenseWalletId = cardUsd,
                expenseAmountMilli = 100_000,
                expenseCurrencyCode = "USD",
            )
        )
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 6, 10, 0),
                expenseWalletId = cardUsd,
                expenseAmountMilli = 1_000_000,
                expenseCurrencyCode = "JPY",
            )
        )
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 7, 10, 0),
                expenseWalletId = cashJpy,
                expenseAmountMilli = 500_000,
                expenseCurrencyCode = "JPY",
            )
        )

        assertEquals("JPY", db.entryDao().lastCurrencyForWallet(cardUsd))
    }

    @Test
    fun the_income_half_counts_towards_what_a_wallet_was_last_paid_in() = runBlocking {
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = exchangeId,
                dateTime = LocalDateTime.of(2026, 4, 4, 10, 0),
                incomeWalletId = cardUsd,
                incomeAmountMilli = 100_000,
                incomeCurrencyCode = "JPY",
            )
        )

        assertEquals("JPY", db.entryDao().lastCurrencyForWallet(cardUsd))
    }

    @Test
    fun a_balance_snapshot_names_its_wallet_and_goes_with_it() = runBlocking {
        db.balanceSnapshotDao().insert(
            BalanceSnapshot(
                walletId = cashJpy,
                dateTime = LocalDateTime.of(2026, 4, 5, 20, 0),
                amountMilli = 49_200_000,
                notes = "recounted",
            )
        )

        val details = db.balanceSnapshotDao().observeByTrip(tripId).first().single()
        assertEquals("Cash", details.wallet.paymentMethod.name)
        assertEquals(0, details.wallet.currency.decimals)
        assertEquals(49_200_000L, details.snapshot.amountMilli)

        // A recount cannot be deleted on its own and says nothing without the wallet it
        // counted, so it is no reason to keep that wallet: it goes along with it.
        db.tripWalletDao().delete(db.tripWalletDao().getById(cashJpy)!!.wallet)
        assertTrue(db.balanceSnapshotDao().observeByTrip(tripId).first().isEmpty())
    }
}
