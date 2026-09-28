package app.jourjour.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import app.jourjour.data.db.AppDatabase
import app.jourjour.data.entity.Category
import app.jourjour.data.entity.Currency
import app.jourjour.data.entity.Entry
import app.jourjour.data.entity.PaymentMethod
import app.jourjour.data.entity.Trip
import app.jourjour.data.entity.TripWallet
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TripPersistenceTest {

    private lateinit var db: AppDatabase
    private var cashId: Long = 0
    private var cardId: Long = 0
    private var tripId: Long = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()

        db.currencyDao().insert(Currency("USD", "US Dollar", "$"))
        db.currencyDao().insert(Currency("JPY", "Japanese Yen", "¥", decimals = 0))
        cashId = db.paymentMethodDao().insert(PaymentMethod(name = "Cash", icon = "💵", fixedCurrency = true))
        cardId = db.paymentMethodDao().insert(
            PaymentMethod(name = "Card", icon = "💳")
        )
        tripId = db.tripDao().insert(
            Trip(
                name = "Japan",
                startDate = LocalDate.of(2026, 4, 3),
                endDate = LocalDate.of(2026, 4, 17),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun trip_dates_survive_a_write_and_a_read() = runBlocking {
        val trip = db.tripDao().getById(tripId)
        assertEquals(LocalDate.of(2026, 4, 3), trip?.startDate)
        assertEquals(LocalDate.of(2026, 4, 17), trip?.endDate)
    }

    @Test
    fun a_wallet_is_written_changed_and_taken_away_on_its_own() = runBlocking {
        // Entries point at wallets by id, so the form edits rows rather than recreating them.
        val jpyId = db.tripWalletDao().insert(
            TripWallet(
                tripId = tripId,
                paymentMethodId = cashId,
                currencyCode = "JPY",
                openingBalanceMilli = 50_000_000,
            )
        )
        val usdId = db.tripWalletDao().insert(
            TripWallet(
                tripId = tripId,
                paymentMethodId = cashId,
                currencyCode = "USD",
                name = "Emergency dollars",
                openingBalanceMilli = 300_000,
            )
        )
        // The same method in the same currency twice over: two folds of cash are two wallets.
        db.tripWalletDao().insert(
            TripWallet(
                tripId = tripId,
                paymentMethodId = cashId,
                currencyCode = "USD",
                name = "Pocket dollars",
            )
        )
        assertEquals(3, db.tripWalletDao().getByTrip(tripId).size)

        val kept = db.tripWalletDao().getById(usdId)!!.wallet
        db.tripWalletDao().update(kept.copy(openingBalanceMilli = 1_000_000))
        db.tripWalletDao().delete(db.tripWalletDao().getById(jpyId)!!.wallet)

        val wallets = db.tripWalletDao().getByTrip(tripId)
        assertEquals(2, wallets.size)
        val changed = wallets.single { it.wallet.id == usdId }
        assertEquals(1_000_000, changed.wallet.openingBalanceMilli)
        // The related dictionary rows come along with the wallet.
        assertEquals("Cash", changed.paymentMethod.name)
        assertEquals(2, changed.currency.decimals)
    }

    @Test
    fun the_wallets_used_most_recently_come_first() = runBlocking {
        val first = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = cashId, currencyCode = "JPY")
        )
        val second = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = cardId, currencyCode = "USD")
        )
        // Never used yet, so they keep the order they were made in.
        assertEquals(listOf(first, second), db.tripWalletDao().getByTrip(tripId).map { it.wallet.id })

        db.tripWalletDao().touch(second, LocalDateTime.of(2026, 4, 4, 9, 0))
        assertEquals(listOf(second, first), db.tripWalletDao().getByTrip(tripId).map { it.wallet.id })
    }

    @Test
    fun deleting_a_trip_takes_its_wallets_and_entries() = runBlocking {
        val categoryId = db.categoryDao().insert(Category(name = "Dining", icon = "🍽"))
        val walletId = db.tripWalletDao().insert(
            TripWallet(tripId = tripId, paymentMethodId = cashId, currencyCode = "JPY")
        )
        db.entryDao().insert(
            Entry(
                tripId = tripId,
                categoryId = categoryId,
                dateTime = LocalDateTime.of(2026, 4, 4, 19, 30),
                expenseWalletId = walletId,
                expenseAmountMilli = 3_500_000,
                expenseCurrencyCode = "JPY",
            )
        )
        assertEquals(1, db.entryDao().countForTrip(tripId))

        db.tripDao().delete(db.tripDao().getById(tripId)!!)

        assertNull(db.tripDao().getById(tripId))
        assertTrue(db.tripWalletDao().getByTrip(tripId).isEmpty())
        assertEquals(0, db.entryDao().countForTrip(tripId))
        // The dictionaries themselves stay put.
        assertEquals(2, db.paymentMethodDao().getAll().size)
    }
}
